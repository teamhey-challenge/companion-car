package ai.hey.model

/** Policy values. Source of truth: packages/protocol/src/config.ts (docs/architecture.md §3.4). */
object HeyConfig {
    const val JOIN_REQUEST_TTL_SEC = 300
    const val TRIP_MAX_SEC = 14_400
    const val TRIP_MAX_DEVICES = 4
    const val TRIP_PROGRESS_POLL_SEC = 60
    const val FREE_MAX_CHARS = 100
    const val FREE_PER_MINUTE = 3

    const val SPEECH_GAP_SEC = 8
    const val SPEECH_GAP_MIN_SEC = 4
    const val SPEECH_GAP_MAX_SEC = 30
    const val SPEECH_DEDUP_WINDOW_SEC = 60
    const val SPEECH_QUEUE_MAX = 5

    const val WS_ACK_TIMEOUT_MS = 5_000L

    /** Relay address the emulator reaches the host machine at. Overridable in D8. */
    const val DEFAULT_RELAY_URL = "http://10.0.2.2:8787"

    /** Reconnect delays in seconds; the last value repeats. */
    val RECONNECT_BACKOFF_SEC: List<Long> = listOf(1, 2, 4, 8, 15)

    /** A trip.progress older than this is shown as "출발할 때" on D5. */
    const val PROGRESS_STALE_AFTER_SEC = 180
}
