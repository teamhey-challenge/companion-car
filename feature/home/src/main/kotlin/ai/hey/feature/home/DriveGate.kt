package ai.hey.feature.home

import ai.hey.model.OnboardingStep
import ai.hey.session.CarFault
import ai.hey.session.CarUiState

/** The routes of PRD 3.2 that this app has. One NavHost, these names. */
object Routes {
    /** the first-run flow (title, whale, consent sheet, questions, long press, greeting, cards, dress-up, phone link). */
    const val Flow = "onboarding-flow"
    const val Home = "home"
    const val Drive = "drive"
    const val Memory = "memory"
    const val Settings = "settings"
    const val Friends = "friends"

    /** `car/settings/phone`: QR + code, the linked Google account, unlink. */
    const val PhoneLink = "settings/phone"

    /** Parked-only routes: every one of them is left for [Drive] the moment the car is not parked (PRD 3.5). */
    val parkedOnly = setOf(Flow, Home, Memory, Settings, PhoneLink, Friends)
}

/**
 * Where the app must be, given where it is. Fail-closed: [parked] is true only for a positively known Park + speed 0
 * (`DrivingStatus.Parked`); unknown counts as moving. Returns null when [current] is already right.
 *
 *  - not parked: /drive from anywhere (AC-3.5-01, AC-3.5-04, AC-4.5-08). The spoken first-run steps run there too;
 *    the steps that need a press simply wait, the stored step is kept.
 *  - parked while the first-run flow is [flowActive]: /onboarding-flow from every other screen. there is no separate consent
 *    or phone-link screen; the consent sheet (O2) and the phone link (O8) are steps of the flow, and a car whose record consent
 *    is missing (first run, or withdrawn in settings) is [flowActive] too, so it lands on the O2 sheet
 *  - parked and on /drive or the finished flow: /home
 */
fun gateRoute(current: String?, parked: Boolean, flowActive: Boolean = false): String? = when {
    !parked -> Routes.Drive.takeIf { current != it }
    flowActive -> Routes.Flow.takeIf { current != it }
    current == Routes.Drive || current == null || current == Routes.Flow -> Routes.Home
    else -> null
}

/** The first-run flow is wanted: a step is not done, or the record consent is missing (first run, withdrawn). */
val CarUiState.flowActive: Boolean get() = onboardingStep != OnboardingStep.Done || needsOnboarding

/** [gateRoute] straight from the UI state, so the gate's inputs (motion, consent, step) are decided in one tested place. */
fun gateRoute(current: String?, state: CarUiState): String? = gateRoute(current, state.isParked, state.flowActive)

/**
 * The driving voice announcement for a fault (PRD 4.8.4 `txt-error-voice`): spoken once per cause, never again for the
 * same cause, and re-armed when the fault clears. Screens never change on a fault while driving (C5 stays as it is).
 */
class FaultAnnouncer(private val speak: (CarFault) -> Unit) {
    private var last: CarFault? = null

    fun onFault(fault: CarFault?, parked: Boolean) {
        if (fault == null) { last = null; return }
        // Parked: the banner says it. Exception: a turn that heard nothing is the character's own line (AC-4.4-06), spoken in any state.
        if (parked && fault != CarFault.Recognition) return
        if (fault == last) return
        last = fault
        // Connecting is a transient state of a healthy reconnect, not worth interrupting a drive for.
        if (fault != CarFault.Connecting) speak(fault)
    }
}
