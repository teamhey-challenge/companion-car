package ai.hey.vehicle

import ai.pleos.playground.stt.constant.ErrorType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Outcome of starting an SDK engine. A success is a value, never `null`, so it cannot be mistaken for a timeout. */
internal sealed interface EngineInit<out E> {
    data class Ready<E>(val engine: E) : EngineInit<E>
    data class Failed(val error: ErrorType) : EngineInit<Nothing>
}

/**
 * Creates an engine, starts its asynchronous initialisation through [start] (which must call `report` exactly once, from
 * any thread) and waits at most [timeoutMs]. A failed or timed-out engine is released before returning, so the caller
 * only ever owns an engine inside [EngineInit.Ready].
 */
internal suspend fun <E> initEngine(
    timeoutMs: Long,
    create: () -> E,
    start: (engine: E, report: (EngineInit<E>) -> Unit) -> Unit,
    release: (E) -> Unit,
): EngineInit<E> {
    val engine = try {
        create()
    } catch (_: Throwable) {
        return EngineInit.Failed(ErrorType.UNKNOWN)
    }
    val outcome = CompletableDeferred<EngineInit<E>>()
    val result = try {
        start(engine) { outcome.complete(it) }
        withTimeoutOrNull(timeoutMs) { outcome.await() } ?: EngineInit.Failed(ErrorType.TIMEOUT)
    } catch (t: kotlinx.coroutines.CancellationException) {
        runCatching { release(engine) }
        throw t
    } catch (_: Throwable) {
        EngineInit.Failed(ErrorType.UNKNOWN)
    }
    if (result is EngineInit.Failed) runCatching { release(engine) }
    return result
}
