package ai.hey.vehicle

import ai.hey.model.SpeechPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One utterance at a time. [speak] reports whether the text was read to the end, and why not when it was not. */
interface SpeechEngine {
    suspend fun speak(text: String): SpeechResult

    /** Cuts the utterance that is being read right now (the pending [speak] then ends as not delivered). */
    fun stop() = Unit
}

/**
 * Speech policy (hey-relay/docs/architecture.md 2.4, policy speech.*):
 * - high priority first, then normal in arrival order; high skips the gap, normal waits [SpeechSettings.gapSec]
 *   after the previous utterance finished;
 * - only normal-priority *request* speech (it has an id) is de-duplicated: the same text inside [dedupWindowMs] of a
 *   completed reading is not read again, and a copy of a text that is still waiting or being read joins that item.
 *   In both cases the copy's [VehicleEvent.SpeechDone] is emitted only once the text was really heard, never at enqueue.
 *   High priority (stopHere, card, pin, drop-off answers) is never de-duplicated;
 * - more than [queueMax] waiting: the oldest normal one (else the oldest) is dropped and every id on it gets
 *   [VehicleEvent.SpeechDropped] with [SpeechDropReason.QueueOverflow].
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
    private class Item(val text: String, val priority: SpeechPriority, val ids: MutableList<String>, val dedupe: Boolean)

    private val lock = Any()
    private val pending = ArrayList<Item>()
    private var current: Item? = null

    /** text -> when it finished being read aloud. Only completed readings are remembered. */
    private val heard = HashMap<String, Long>()
    private var lastDoneAt: Long? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch { loop() }
    }

    fun enqueue(text: String, priority: SpeechPriority, id: String?, force: Boolean = false) {
        if (text.isBlank()) {
            if (id != null) emit(VehicleEvent.SpeechDropped(id, SpeechDropReason.Blank))
            return
        }
        val dedupe = id != null && priority == SpeechPriority.Normal && !force
        val dropped = ArrayList<String>()
        var heardRecently = false
        synchronized(lock) {
            val t = now()
            heard.entries.removeAll { t - it.value > dedupWindowMs }
            val existing = if (dedupe) (pending + listOfNotNull(current)).firstOrNull { it.dedupe && it.text == text } else null
            when {
                dedupe && heard.containsKey(text) -> heardRecently = true
                existing != null -> existing.ids += id!!
                else -> {
                    pending += Item(text, priority, if (id != null) mutableListOf(id) else mutableListOf(), dedupe)
                    while (pending.size > queueMax) {
                        val victim = pending.firstOrNull { it.priority == SpeechPriority.Normal } ?: pending.first()
                        pending.remove(victim)
                        dropped += victim.ids
                    }
                }
            }
        }
        if (heardRecently) emit(VehicleEvent.SpeechDone(id!!))
        dropped.forEach { emit(VehicleEvent.SpeechDropped(it, SpeechDropReason.QueueOverflow)) }
        wake.trySend(Unit)
    }
    /** Drops everything waiting and cuts what is being read: the driver pressed the button, or the focus went (D-14, D-24). Nothing is read again. */
    fun stopAll() {
        synchronized(lock) { pending.clear() }
        engine.stop()
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
            val taken = synchronized(lock) {
                val removed = pending.remove(item)
                if (removed) current = item
                removed
            }
            if (!taken) continue
            val result = engine.speak(item.text)
            val delivered = result is SpeechResult.Delivered
            val reason = (result as? SpeechResult.Dropped)?.reason ?: SpeechDropReason.EngineError
            val ids = synchronized(lock) {
                current = null
                if (delivered && item.dedupe) heard[item.text] = now()
                item.ids.toList()
            }
            lastDoneAt = now()
            ids.forEach { id ->
                emit(if (delivered) VehicleEvent.SpeechDone(id) else VehicleEvent.SpeechDropped(id, reason))
            }
        }
    }
}
