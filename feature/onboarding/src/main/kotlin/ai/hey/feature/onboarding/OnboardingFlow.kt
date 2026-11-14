package ai.hey.feature.onboarding

/** How C1 ended once the consent was recorded (PRD 4.1.2, planner F1). */
enum class OnboardingOutcome {
    /** The microphone was refused: go to C8 (microphone). Consent stays recorded. */
    MicDenied,

    /** The microphone is allowed: go on to C2. CAR_SPEED may still be denied (driving then fails closed, PRD 4.1.5 Partial). */
    Done,
}

/**
 * The runtime permissions after the consent is on the server (never before): microphone first, vehicle speed second.
 * Free of Android so the order and the outcome are testable.
 */
class OnboardingFlow(
    /** Asks one runtime permission and returns whether it was granted. */
    private val requestPermission: suspend (permission: String) -> Boolean,
    private val microphone: String,
    private val vehicleSpeed: String,
) {
    suspend fun afterConsent(): OnboardingOutcome {
        val mic = requestPermission(microphone)
        requestPermission(vehicleSpeed)
        return if (mic) OnboardingOutcome.Done else OnboardingOutcome.MicDenied
    }
}

/** "동의하고 시작" without the required consent ticked is refused on the screen: nothing is asked or sent. */
fun canStart(voiceTicked: Boolean): Boolean = voiceTicked
