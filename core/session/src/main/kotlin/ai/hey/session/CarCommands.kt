package ai.hey.session

import ai.hey.model.CarSettings

/** Commands that are safe in any phase, a moving car included: talking, local view state, settings values. */
interface CarCommands {
    /** Push-to-talk: opens the microphone for one sentence and runs one turn with it. Ignored while a turn is running. */
    fun startTalk()

    /** Stops listening and drops a turn that has not been answered. Safe in any phase. */
    fun cancelTalk()

    /** The driver ended the sentence (button released, or pressed again on the driving screen): send what was heard. No-op unless listening. */
    fun endListening()

    /** The result of the runtime RECORD_AUDIO request the activity made when [TurnState.NeedsPermission] showed. */
    fun voicePermissionResult(granted: Boolean)

    /** Re-reads RECORD_AUDIO (activity onResume): granted now → the Microphone fault goes away without a press. */
    fun refreshVoicePermission()
    fun closeSettings()
    fun updateSettings(transform: (CarSettings) -> CarSettings)
}

/**
 * Commands that need the driver's hands. Only [TalkRepository] creates one (the constructor is module-internal), and
 * the only way to reach it is [CarUiState.parked], which the repository fills with one of these only while the car is
 * parked. Every method also asks the live driving state, so a reference kept across a gear change does nothing.
 */
abstract class ParkedCommands internal constructor() {
    abstract fun openSettings()
}
