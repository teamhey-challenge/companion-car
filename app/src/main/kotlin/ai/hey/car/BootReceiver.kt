package ai.hey.car

import ai.hey.model.HeyLog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the session after boot. Only works once the app was opened at least once (platform rule). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            SessionService.start(context)
        } catch (t: Throwable) {
            HeyLog.w("BootReceiver", "could not start session: ${t.javaClass.simpleName}")
        }
    }
}
