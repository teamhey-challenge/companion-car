package ai.hey.feature.home

/** The routes of PRD 3.2 that this app has. One NavHost, these names. */
object Routes {
    const val Onboarding = "onboarding"
    const val Pair = "pair"
    const val Home = "home"
    const val Drive = "drive"
    const val Memory = "memory"
    const val Settings = "settings"

    /** Parked-only routes: every one of them is left for [Drive] the moment the car is not parked (PRD 3.5). */
    val parkedOnly = setOf(Onboarding, Pair, Home, Memory, Settings)
}

/**
 * Where the app must be, given where it is. Fail-closed: [parked] is true only for a positively known Park + speed 0
 * (`DrivingStatus.Parked`); unknown counts as moving. Returns null when [current] is already right.
 *
 *  - not parked: /drive from anywhere (AC-3.5-01, AC-3.5-04, AC-4.5-08)
 *  - parked and on /drive: /onboarding when consent is missing, otherwise /home (AC-3.5-02)
 *  - parked, consent missing, on any other parked screen but /onboarding: /onboarding (PRD 4.3.7)
 */
fun gateRoute(current: String?, parked: Boolean, needsOnboarding: Boolean): String? = when {
    !parked -> Routes.Drive.takeIf { current != it }
    current == Routes.Drive || current == null -> if (needsOnboarding) Routes.Onboarding else Routes.Home
    needsOnboarding && current != Routes.Onboarding -> Routes.Onboarding
    else -> null
}

/**
 * The driving voice announcement for a fault (PRD 4.8.4 `txt-error-voice`): spoken once per cause, never again for the
 * same cause, and re-armed when the fault clears. Screens never change on a fault while driving (C5 stays as it is).
 */
class FaultAnnouncer(private val speak: (HomeFault) -> Unit) {
    private var last: HomeFault? = null

    fun onFault(fault: HomeFault?, parked: Boolean) {
        if (fault == null) { last = null; return }
        if (parked) return // parked: the banner says it, no voice
        if (fault == last) return
        last = fault
        // Connecting is a transient state of a healthy reconnect, not worth interrupting a drive for.
        if (fault != HomeFault.Connecting) speak(fault)
    }
}
