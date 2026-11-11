package ai.hey.model

enum class SpeechRate { Slow, Normal, Fast }

/** Device-local driver settings (D8). Never sent to the relay. */
data class CarSettings(
    /** Presets the driver wants to receive. `stopHere` and `card` cannot be switched off. */
    val enabledPresets: Set<PresetId> = PresetId.entries.toSet(),
    val speechGapSec: Int = HeyConfig.SPEECH_GAP_SEC,
    val speechRate: SpeechRate = SpeechRate.Normal,
    val dark: Boolean = true,
    /** Last 4 characters of the plate; used when the car registers itself. */
    val plateSuffix: String = DEFAULT_PLATE_SUFFIX,
    /** Relay base URL, e.g. http://10.0.2.2:8787 */
    val relayUrl: String = HeyConfig.DEFAULT_RELAY_URL,
) {
    fun withPreset(id: PresetId, on: Boolean): CarSettings =
        if (!id.driverCanDisable) this else copy(enabledPresets = if (on) enabledPresets + id else enabledPresets - id)

    companion object {
        const val DEFAULT_PLATE_SUFFIX = "0000"
    }
}
