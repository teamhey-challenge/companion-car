package ai.hey.feature.home

import ai.hey.session.CarFault

/** The routes of PRD 3.2 that this app has. One NavHost, these names. */
object Routes {
    const val Onboarding = "onboarding"

    /** D-48: the steps after C1 (whale, questions, summon, greeting, cards, look, phone link). */
    const val Flow = "onboarding-flow"
    const val Pair = "pair"
    const val Home = "home"
    const val Drive = "drive"
    const val Memory = "memory"
    const val Settings = "settings"

    /** Parked-only routes: every one of them is left for [Drive] the moment the car is not parked (PRD 3.5). */
    val parkedOnly = setOf(Onboarding, Flow, Pair, Home, Memory, Settings)
}

/**
 * Where the app must be, given where it is. Fail-closed: [parked] is true only for a positively known Park + speed 0
 * (`DrivingStatus.Parked`); unknown counts as moving. Returns null when [current] is already right.
 *
 *  - not parked: /drive from anywhere (AC-3.5-01, AC-3.5-04, AC-4.5-08). The spoken onboarding steps run there too (D-48 3);
 *    the parked-only steps simply wait, the stored step is kept.
 *  - parked and on /drive: /onboarding when consent is missing, then /onboarding-flow while the flow is [flowActive], otherwise /home
 *  - parked, consent missing, on any other parked screen but /onboarding: /onboarding (PRD 4.3.7)
 *  - parked, flow active, on any other parked screen than the flow: back to the flow (the phone-link step is drawn inside it)
 */
fun gateRoute(current: String?, parked: Boolean, needsOnboarding: Boolean, flowActive: Boolean = false): String? = when {
    !parked -> Routes.Drive.takeIf { current != it }
    current == Routes.Drive || current == null -> if (needsOnboarding) Routes.Onboarding else if (flowActive) Routes.Flow else Routes.Home
    needsOnboarding && current != Routes.Onboarding -> Routes.Onboarding
    !needsOnboarding && flowActive && current == Routes.Onboarding -> Routes.Flow
    !needsOnboarding && flowActive && current != Routes.Flow -> Routes.Flow
    !needsOnboarding && !flowActive && current == Routes.Flow -> Routes.Home
    else -> null
}

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
