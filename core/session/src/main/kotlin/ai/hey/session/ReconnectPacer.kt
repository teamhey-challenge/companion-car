package ai.hey.session

import ai.hey.model.HeyConfig
import ai.hey.model.HeyLog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "TalkLink"

/** What cut a reconnect pause short (or, for [Backoff], nothing did). The name goes into the log line. */
enum class ReconnectTrigger(val label: String) { Network("network"), Button("button"), Backoff("backoff") }

/** The device's connectivity, as the link needs it: a call whenever the default network is usable (PLEOS-94). */
fun interface Connectivity {
    /** Calls [onReady] on every "network available / validated" event, [onLost] when the default network goes; until the handle is closed. */
    fun watch(onReady: () -> Unit, onLost: () -> Unit): AutoCloseable
}

/**
 * The pause between reconnect attempts: 1/2/4/8/15 s, cut short at once by a network that came back or by the C8
 * "다시 연결" button. Times come from [clock] (ms, monotonic) so tests run on a fake one.
 * Logs `link: reconnect trigger=… after … ms` and `link: online after … ms`.
 *
 * A lost network marks the link down, because a socket on the dead network can look open for a long time. The first
 * "network is back" event after a loss stamps [networkBackAt]; an attempt that began before that stamp ran on the
 * dead network and is stale ([TalkLink] drops it). Later events of the same return (available, then validated) only
 * cut a pause short.
 */
class ReconnectPacer(
    private val clock: () -> Long,
    private val connectivity: Connectivity,
    private val backoffSec: List<Long> = HeyConfig.RECONNECT_BACKOFF_SEC,
) {
    private val wake = Channel<ReconnectTrigger>(Channel.CONFLATED)

    /** Triggers that arrived while the link is down; [TalkLink] also listens while an attempt is in flight. */
    val events: ReceiveChannel<ReconnectTrigger> get() = wake
    private var watching: AutoCloseable? = null

    @Volatile private var downSince = clock()

    @Volatile private var down = true

    @Volatile private var lost = false

    /** When the network last came back after a loss; an attempt that began earlier is stale. */
    @Volatile var networkBackAt: Long = Long.MIN_VALUE
        private set

    fun start() {
        wake.tryReceive()
        downSince = clock()
        down = true
        lost = false
        networkBackAt = Long.MIN_VALUE
        watching?.close()
        watching = connectivity.watch({ trigger(ReconnectTrigger.Network) }, ::onNetworkLost)
    }

    fun stop() {
        watching?.close()
        watching = null
    }

    private fun onNetworkLost() {
        lost = true
        markDown()
    }

    /** Asks for an attempt now. Ignored while the link is online and the network never went: there is nothing to cut short. */
    fun trigger(trigger: ReconnectTrigger) {
        if (trigger == ReconnectTrigger.Network && lost) {
            lost = false
            networkBackAt = clock()
            down = true
        }
        if (down) wake.trySend(trigger)
    }

    /** The link was lost (or an attempt failed); keeps the first moment of a run of failures. */
    fun markDown() {
        if (!down) {
            down = true
            downSince = clock()
        }
    }

    fun markOnline() {
        HeyLog.i(TAG, "link: online after ${clock() - downSince} ms")
        down = false
        wake.tryReceive()
    }

    /** Waits out the backoff for [failures] failed attempts in a row, or until a trigger; returns what ended it. */
    suspend fun pause(failures: Int): ReconnectTrigger {
        val trigger = withTimeoutOrNull(backoffMillis(failures, backoffSec)) { wake.receive() } ?: ReconnectTrigger.Backoff
        HeyLog.i(TAG, "link: reconnect trigger=${trigger.label} after ${clock() - downSince} ms")
        return trigger
    }
}
