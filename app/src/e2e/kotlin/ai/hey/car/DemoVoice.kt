package ai.hey.car

import ai.hey.vehicle.InjectableVoiceInput
import ai.hey.vehicle.PleosVoiceInput
import ai.hey.vehicle.VoiceInput
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Debug and releaseE2e builds only (sourceSet `e2e`; the real release has the no-op twin in src/release). By default these
 * builds listen with the real Pleos SpeechToText (the emulator's host microphone), exactly like release. A sentence is
 * injected only on request, by adb broadcast:
 *
 *  - next listen only: `adb shell am broadcast -a ai.hey.car.DEBUG_VOICE -p ai.hey.car --es text "<한국어 문장>"`
 *    (`--es text ""` = the next listen hears nothing, which shows the "잘 듣지 못했어요" state); afterwards the real mic is back;
 *  - every listen (E2E suites that repeat a turn): add `--ez sticky true`;
 *  - back to the real microphone: `adb shell am broadcast -a ai.hey.car.DEBUG_VOICE_REAL -p ai.hey.car`;
 *  - start a turn without touching the screen: `adb shell am broadcast -a ai.hey.car.DEBUG_TALK -p ai.hey.car`;
 *  - read a text with the Pleos voice (mouth from `onUpdatedRms`): `... -a ai.hey.car.DEBUG_SPEAK --es text "<문장>"`.
 */
internal object DemoVoice {
    const val ACTION = "ai.hey.car.DEBUG_VOICE"
    const val ACTION_REAL = "ai.hey.car.DEBUG_VOICE_REAL"
    const val ACTION_TALK = "ai.hey.car.DEBUG_TALK"
    const val ACTION_SPEAK = "ai.hey.car.DEBUG_SPEAK"
    const val SENDER_PERMISSION = android.Manifest.permission.DUMP

    fun create(app: Application): VoiceInput {
        val input = InjectableVoiceInput(PleosVoiceInput(app))
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val graph = (app as HeyApp).graph
                when (intent.action) {
                    ACTION_TALK -> graph.repository.startTalk()
                    ACTION_SPEAK -> graph.vehicle.speak(intent.getStringExtra("text").orEmpty(), ai.hey.model.SpeechPriority.High, null)
                    ACTION_REAL -> input.useReal()
                    else -> input.inject(intent.getStringExtra("text").orEmpty(), sticky = intent.getBooleanExtra("sticky", false))
                }
            }
        }
        val filter = IntentFilter(ACTION).apply { addAction(ACTION_REAL); addAction(ACTION_TALK); addAction(ACTION_SPEAK) }
        // Debug/e2e builds only. The sender must hold android.permission.DUMP (signature|privileged|development): the adb shell has it,
        // an installed third-party app cannot get it, so only `adb shell am broadcast` reaches the receiver. Same adb commands as before.
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, SENDER_PERMISSION, null, Context.RECEIVER_EXPORTED) else @Suppress("UnspecifiedRegisterReceiverFlag") app.registerReceiver(receiver, filter, SENDER_PERMISSION, null)
        return input
    }
}

/** The one hook [AppGraph] calls: the real recognizer with the adb-injectable override in these builds. */
internal fun demoVoiceOrNull(app: Application): VoiceInput? = DemoVoice.create(app)
