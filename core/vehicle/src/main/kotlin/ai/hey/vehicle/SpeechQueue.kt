package ai.hey.vehicle

import ai.hey.model.SpeechPriority
import ai.hey.model.SpeechRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One utterance at a time. [speak] returns true when the text was read to the end. */
interface SpeechEngine {
    suspend fun speak(text: String, rate: SpeechRate): Boolean
}

/**
 * Speech policy (docs/architecture.md 2.4, policy speech.*):
 * - high priority first, then normal in arrival order; high skips the gap, normal waits [SpeechSettings.gapSec]
 *   after the previous utterance finished;
 * - the same text inside [dedupWindowMs] is not read again (the caller still gets SpeechDone so a request is not
 *   stuck in "received");
 * - more than [queueMax] waiting: the oldest normal one (else the oldest) is dropped.
 * [now] is injected so tests drive time with the coroutine scheduler.
 */
class SpeechQueue(
    scope: CoroutineScope,
    private val engine: SpeechEngine,
    private val settings: () -> SpeechSettings,
    private val now: () -> Long,
    private val emit: (VehicleEvent) -> Unit,
    private val dedupWindowMs: Long,
    private val queueMax: Int,
) {
    private class Item(val seq: Long, val id: String?, val text: String, val priority: SpeechPriority)

    private val lock = Any()
    private val pending = ArrayList<Item>()
    private val recent = HashMap<String, Long>()
    private var seq = 0L
    private var lastDoneAt: Long? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch { loop() }
    }

    fun enqueue(text: String, priority: SpeechPriority, id: String?, force: Boolean = false) {
        if (text.isBlank()) {
            if (id != null) emit(VehicleEvent.SpeechDone(id))
            return
        }
        val dropped = ArrayList<String>()
        var duplicate = false
        synchronized(lock) {
            val t = now()
            recent.entries.removeAll { t - it.value > dedupWindowMs }
            if (!force && recent.containsKey(text)) {
                duplicate = true
            } else {
                recent[text] = t
                pending += Item(seq++, id, text, priority)
                while (pending.size > queueMax) {
                    val victim = pending.firstOrNull { it.priority == SpeechPriority.Normal } ?: pending.first()
                    pending.remove(victim)
                    victim.id?.let { dropped += it }
                }
            }
        }
        if (duplicate && id != null) emit(VehicleEvent.SpeechDone(id))
        dropped.forEach { emit(VehicleEvent.SpeechDropped(it)) }
        wake.trySend(Unit)
    }

    private fun best(): Item? = synchronized(lock) {
        pending.firstOrNull { it.priority == SpeechPriority.High } ?: pending.firstOrNull()
    }

    private suspend fun loop() {
        while (true) {
            val item = best()
            if (item == null) {
                wake.receive()
                continue
            }
            if (item.priority == SpeechPriority.Normal) {
                val last = lastDoneAt
                val wait = if (last == null) 0L else last + settings().gapSec * 1000L - now()
                if (wait > 0) {
                    // A new arrival (possibly a high one) re-evaluates the choice.
                    withTimeoutOrNull(wait) { wake.receive() }
                    continue
                }
            }
            val removed = synchronized(lock) { pending.remove(item) }
            if (!removed) continue
            val ok = engine.speak(item.text, settings().rate)
            lastDoneAt = now()
            item.id?.let { emit(if (ok) VehicleEvent.SpeechDone(it) else VehicleEvent.SpeechDropped(it)) }
        }
    }
}
