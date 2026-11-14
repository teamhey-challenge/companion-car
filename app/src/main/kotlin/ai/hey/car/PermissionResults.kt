package ai.hey.car

import android.Manifest

/**
 * The microphone answer inside a RequestMultiplePermissions result, or null when the result does not ask about the
 * microphone (e.g. the notification request): only a real answer goes back to the voice session.
 */
internal fun voiceAnswer(result: Map<String, Boolean>): Boolean? = result[Manifest.permission.RECORD_AUDIO]

/**
 * The one rule for every runtime-permission dialog (PLEOS-90): only while the car is parked. A system permission
 * dialog cannot be closed from the app, and if gear D arrives while it is open the Pleos system covers the app with
 * "주행 중에는 사용할 수 없습니다" (AC-3.5-05). [parked] is `CarUiState.isParked`, which is false for unknown motion.
 */
internal fun mayAskPermission(parked: Boolean): Boolean = parked

/**
 * The notification permission is asked once per activity, as soon as the car is parked (never at launch while moving
 * or unknown), on API 33+ and only while it is not granted.
 */
internal fun shouldAskNotification(sdk: Int, granted: Boolean, parked: Boolean, alreadyAsked: Boolean): Boolean =
    sdk >= 33 && !granted && !alreadyAsked && mayAskPermission(parked)
