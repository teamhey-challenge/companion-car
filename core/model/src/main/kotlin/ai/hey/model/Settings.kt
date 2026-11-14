package ai.hey.model

/** Device-local driver settings (D8). Never sent to the server. */
data class CarSettings(
    val speechGapSec: Int = HeyConfig.SPEECH_GAP_SEC,
    /** null = follow the system theme (D-26); true/false = the driver's explicit choice. */
    val dark: Boolean? = null,
    /** Companion server base URL, e.g. https://hey.lastmile.ltd/api */
    val serverUrl: String = HeyConfig.DEFAULT_SERVER_URL,
)
