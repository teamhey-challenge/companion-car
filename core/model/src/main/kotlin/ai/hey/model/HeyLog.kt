package ai.hey.model

/**
 * Platform-neutral log sink so core modules stay testable on the JVM. The app installs an android.util.Log
 * sink; tokens and credentials must never be passed in.
 */
object HeyLog {
    @Volatile
    var sink: (level: Char, tag: String, message: String) -> Unit = { level, tag, message -> println("$level/$tag: $message") }

    /** What the driver said and what the car answered may be logged only when this is true; the app sets it from BuildConfig.DEBUG (PLEOS-137). */
    @Volatile
    var contentEnabled: Boolean = false

    fun d(tag: String, message: String) = sink('D', tag, message)
    fun i(tag: String, message: String) = sink('I', tag, message)
    fun w(tag: String, message: String) = sink('W', tag, message)

    /** Logs [message] plus ` text=<content>` only when [contentEnabled]; release keeps the message (lengths, ids) alone. */
    fun content(tag: String, message: String, content: String) = i(tag, if (contentEnabled) "$message text=$content" else message)
}
