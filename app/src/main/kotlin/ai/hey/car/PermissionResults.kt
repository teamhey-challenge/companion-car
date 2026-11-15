package ai.hey.car

import android.Manifest

/**
 * The microphone answer inside a RequestMultiplePermissions result, or null when the result does not ask about the
 * microphone (e.g. the notification request): only a real answer goes back to the voice session.
 */
internal fun voiceAnswer(result: Map<String, Boolean>): Boolean? = result[Manifest.permission.RECORD_AUDIO]

/**
 * The one rule for every runtime-permission dialog: only while the car is parked. A system permission
 * dialog cannot be closed from the app, and if gear D arrives while it is open the Pleos system covers the app with
 * "주행 중에는 사용할 수 없습니다" (AC-3.5-05). [parked] is `CarUiState.isParked`, which is false for unknown motion.
 */
internal fun mayAskPermission(parked: Boolean): Boolean = parked

/**
 * The notification permission (Android 13+) is what lets the two notifications (friend message, trip-end diary)
 * appear. Asked once, parked only, after consent and the onboarding flow; a denial is kept and never asked again, the
 * notifications are then skipped silently ([AndroidNoticeSink]).
 */
internal fun shouldAskNotifications(parked: Boolean, consented: Boolean, onboardingDone: Boolean, alreadyAsked: Boolean, granted: Boolean, sdk: Int): Boolean =
    sdk >= 33 && !granted && !alreadyAsked && consented && onboardingDone && mayAskPermission(parked)

internal val requestedRuntimePermissions: Set<String> = setOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
