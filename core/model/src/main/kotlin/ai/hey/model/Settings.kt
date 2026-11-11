package ai.hey.model

/** Device-local driver settings (D8). Never sent to the relay. */
data class CarSettings(
    val speechGapSec: Int = HeyConfig.SPEECH_GAP_SEC,
    val dark: Boolean = true,
    /** Plate suffix; used when the car registers itself. */
    val plateSuffix: String = DEFAULT_PLATE_SUFFIX,
    /** Relay base URL, e.g. http://10.0.2.2:8787 */
    val relayUrl: String = HeyConfig.DEFAULT_RELAY_URL,
) {
    companion object {
        const val DEFAULT_PLATE_SUFFIX = "0000"
    }
}
