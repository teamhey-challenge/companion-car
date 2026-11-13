package ai.hey.model

/** Device-local driver settings (D8). Never sent to the server. */
data class CarSettings(
    val speechGapSec: Int = HeyConfig.SPEECH_GAP_SEC,
    val dark: Boolean = true,
    /** Companion server base URL, e.g. https://hey.lastmile.ltd/api */
    val serverUrl: String = HeyConfig.DEFAULT_SERVER_URL,
)
