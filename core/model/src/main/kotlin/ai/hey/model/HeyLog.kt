package ai.hey.model

/**
 * Platform-neutral log sink so core modules stay testable on the JVM. The app installs an android.util.Log
 * sink; tokens and credentials must never be passed in.
 */
object HeyLog {
    @Volatile
    var sink: (level: Char, tag: String, message: String) -> Unit = { level, tag, message -> println("$level/$tag: $message") }

    fun d(tag: String, message: String) = sink('D', tag, message)
    fun i(tag: String, message: String) = sink('I', tag, message)
    fun w(tag: String, message: String) = sink('W', tag, message)
}
