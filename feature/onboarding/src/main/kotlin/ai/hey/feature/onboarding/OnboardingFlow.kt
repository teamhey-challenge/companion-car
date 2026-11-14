package ai.hey.feature.onboarding

/** Where a press of "동의하고 시작" ended (PRD 4.1.2, planner F1). */
enum class OnboardingOutcome {
    /** The required consent is not ticked: stay on C1, say so, ask for no permission and send nothing. */
    VoiceRequired,

    /** The consent could not be recorded: stay on C1, ticks kept. */
    NetworkError,

    /** The microphone was refused: go to C8 (microphone). Consent stays recorded. */
    MicDenied,

    /** Consent recorded and the microphone allowed: go on to C2. CAR_SPEED may still be denied (driving then fails closed). */
    Done,
}

/**
 * The C1 sequence, free of Android so the order is testable: validate -> record consent on the server -> only then ask
 * the runtime permissions, microphone first, vehicle speed second.
 */
class OnboardingFlow(
    /** Records the consents (record = true, memory as ticked). False = the server or network said no. */
    private val submitConsent: suspend (memory: Boolean) -> Boolean,
    /** Asks one runtime permission and returns whether it was granted. */
    private val requestPermission: suspend (permission: String) -> Boolean,
    private val microphone: String,
    private val vehicleSpeed: String,
) {
    suspend fun start(voice: Boolean, memory: Boolean): OnboardingOutcome {
        if (!voice) return OnboardingOutcome.VoiceRequired
        if (!submitConsent(memory)) return OnboardingOutcome.NetworkError
        val mic = requestPermission(microphone)
        requestPermission(vehicleSpeed)
        return if (mic) OnboardingOutcome.Done else OnboardingOutcome.MicDenied
    }
}
