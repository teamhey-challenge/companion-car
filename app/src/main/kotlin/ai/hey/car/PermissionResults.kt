package ai.hey.car

import android.Manifest

/**
 * The microphone answer inside a RequestMultiplePermissions result, or null when the result does not ask about the
 * microphone (e.g. the notification request): only a real answer goes back to the voice session.
 */
internal fun voiceAnswer(result: Map<String, Boolean>): Boolean? = result[Manifest.permission.RECORD_AUDIO]
