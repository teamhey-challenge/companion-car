package ai.hey.car

import ai.hey.model.ServerFrame
import ai.hey.session.TalkTransport
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** What the notification centre shows (D-52 2: exactly two kinds). The strings come from resources when it is posted. */
sealed interface CenterNotice {
    val frameId: String

    /** A friend's message; [subjectIsI] picks "이" (name ends in a final consonant) or "가" for "{name}가 말을 전했어". */
    data class Friend(override val frameId: String, val name: String, val text: String, val subjectIsI: Boolean) : CenterNotice

    data class TripDiary(override val frameId: String, val text: String) : CenterNotice
}

/** Where a notice goes. [AndroidNoticeSink] posts it; a test records it. */
fun interface NoticeSink {
    fun post(notice: CenterNotice)
}

/**
 * Turns the two frames that belong in the notification centre (`friend_message`, `trip_done`) into [CenterNotice]s.
 * A frame id is posted once: the server replays undelivered friend messages when the car reconnects.
 */
class FrameNotifier(private val sink: NoticeSink, private val remembered: Int = 64) {
    private val seen = LinkedHashSet<String>()

    fun onFrame(frame: ServerFrame) {
        val notice = when (frame) {
            is ServerFrame.FriendMessage -> CenterNotice.Friend(frame.id, frame.fromName.trim(), frame.text, endsWithFinalConsonant(frame.fromName))
            is ServerFrame.TripDone -> CenterNotice.TripDiary(frame.id, frame.text)
            else -> return
        }
        synchronized(seen) {
            if (!seen.add(notice.frameId)) return
            if (seen.size > remembered) seen.remove(seen.first())
        }
        sink.post(notice)
    }

    companion object {
        /** True when the last character is a Hangul syllable with a final consonant (받침). */
        fun endsWithFinalConsonant(name: String): Boolean {
            val c = name.trimEnd().lastOrNull() ?: return false
            return c in '\uAC00'..'\uD7A3' && (c - '\uAC00') % 28 != 0
        }
    }
}

/** Passes every frame on to [next] (the repository) and also to [notifier]. Wired in the transport factory, so the repository is untouched. */
fun TalkTransport.Listener.alsoTo(notifier: FrameNotifier): TalkTransport.Listener {
    val next = this
    return object : TalkTransport.Listener by next {
        override fun onFrame(frame: ServerFrame) {
            notifier.onFrame(frame)
            next.onFrame(frame)
        }
    }
}

/** Posts to the system notification centre: a normal-importance channel (no heads-up banner), tap opens the app. */
class AndroidNoticeSink(private val context: Context) : NoticeSink {
    override fun post(notice: CenterNotice) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // Covers the POST_NOTIFICATIONS grant on Android 13+ and a channel the user turned off in settings.
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notice_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val (title, text) = when (notice) {
            is CenterNotice.Friend -> {
                val name = notice.name.ifEmpty { context.getString(R.string.notice_friend_unknown) }
                val res = if (notice.name.isNotEmpty() && notice.subjectIsI) R.string.notice_friend_title_i else R.string.notice_friend_title_ga
                context.getString(res, name) to notice.text
            }
            is CenterNotice.TripDiary -> context.getString(R.string.notice_trip_title) to notice.text
        }
        manager.notify(
            notice.frameId,
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL = "hey_notice"
        const val NOTIFICATION_ID = 4002
    }
}
