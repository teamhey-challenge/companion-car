package ai.hey.model

/** Policy values of the car app. The wire contract itself is [Wire] (companion-shared packages/protocol). */
object HeyConfig {
    const val SPEECH_GAP_SEC = 8
    const val SPEECH_GAP_MIN_SEC = 4
    const val SPEECH_GAP_MAX_SEC = 30
    const val SPEECH_DEDUP_WINDOW_SEC = 60
    const val SPEECH_QUEUE_MAX = 5

    /** The public companion server (reverse proxy: /api/* -> server). Overridable in D8 and with -PserverUrl at build time. */
    const val DEFAULT_SERVER_URL = "https://hey.lastmile.ltd/api"

    /** Reconnect delays in seconds; the last value repeats. */
    val RECONNECT_BACKOFF_SEC: List<Long> = listOf(1, 2, 4, 8, 15)
}
