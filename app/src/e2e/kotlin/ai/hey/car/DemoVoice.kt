package ai.hey.car

import ai.hey.vehicle.ScriptedVoiceInput
import ai.hey.vehicle.VoiceInput
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Debug and releaseE2e builds only (sourceSet `e2e`; the real release has the no-op twin in src/release). The emulator's
 * microphone is silent, so the driver's sentence comes from this injectable source instead of Pleos SpeechToText:
 *
 *  - default sentence: the bundled fixture `assets/voice-fixture.txt` (Korean), returned after a short "listening" pause;
 *  - override at run time: `adb shell am broadcast -a ai.hey.car.DEBUG_VOICE -p ai.hey.car --es text "<한국어 문장>"`
 *    (`--es text ""` makes the next listen hear nothing, which shows the "잘 듣지 못했어요" state);
 *  - start a turn without touching the screen: `adb shell am broadcast -a ai.hey.car.DEBUG_TALK -p ai.hey.car`;
 *  - read a text with the Pleos voice (mouth from `onUpdatedRms`): `... -a ai.hey.car.DEBUG_SPEAK --es text "<문장>"`.
 */
internal object DemoVoice {
    const val ACTION = "ai.hey.car.DEBUG_VOICE"
    const val ACTION_TALK = "ai.hey.car.DEBUG_TALK"
    const val ACTION_SPEAK = "ai.hey.car.DEBUG_SPEAK"
    private const val FIXTURE = "voice-fixture.txt"
    private const val LISTEN_MS = 800L

    fun create(app: Application): VoiceInput {
        val fixture = try {
            app.assets.open(FIXTURE).bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            null
        }
        val input = ScriptedVoiceInput(listenMillis = LISTEN_MS, text = fixture, silentAfterReply = true)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val graph = (app as HeyApp).graph
                when (intent.action) {
                    ACTION_TALK -> graph.repository.startTalk()
                    ACTION_SPEAK -> graph.vehicle.speak(intent.getStringExtra("text").orEmpty(), ai.hey.model.SpeechPriority.High, null)
                    else -> input.text = intent.getStringExtra("text")
                }
            }
        }
        val filter = IntentFilter(ACTION).apply { addAction(ACTION_TALK); addAction(ACTION_SPEAK) }
        // Debug/e2e builds only; the broadcast comes from adb, so the receiver is exported (minSdk 28 has no flag overload before 33).
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) else @Suppress("UnspecifiedRegisterReceiverFlag") app.registerReceiver(receiver, filter)
        return input
    }
}

/** The one hook [AppGraph] calls: the demo recognizer in these builds. */
internal fun demoVoiceOrNull(app: Application): VoiceInput? = DemoVoice.create(app)
