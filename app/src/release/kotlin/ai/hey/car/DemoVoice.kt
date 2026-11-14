package ai.hey.car

import ai.hey.vehicle.VoiceInput
import android.app.Application

/** The shipped release has no injectable override: the Pleos SpeechToText is always used (twin of src/e2e). */
internal fun demoVoiceOrNull(app: Application): VoiceInput? = null
