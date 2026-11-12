package ai.hey.vehicle

import ai.pleos.playground.stt.constant.ErrorType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EngineInitTest {
    private class Fake {
        var released = 0
    }

    @Test
    fun successIsReadyAndTheEngineIsKept() = runTest {
        val fake = Fake()
        val result = initEngine(
            timeoutMs = 10_000,
            create = { fake },
            start = { e, report -> report(EngineInit.Ready(e)) },
            release = { it.released++ },
        )
        assertEquals(EngineInit.Ready(fake), result)
        assertEquals(0, fake.released)
    }

    @Test
    fun failureIsFailedAndTheEngineIsReleased() = runTest {
        val fake = Fake()
        val result = initEngine(
            timeoutMs = 10_000,
            create = { fake },
            start = { _, report -> report(EngineInit.Failed(ErrorType.PERMISSION_DENIED)) },
            release = { it.released++ },
        )
        assertEquals(EngineInit.Failed(ErrorType.PERMISSION_DENIED), result)
        assertEquals(1, fake.released)
    }

    @Test
    fun silenceTimesOutAndTheEngineIsReleased() = runTest {
        val fake = Fake()
        val result = initEngine(
            timeoutMs = 10_000,
            create = { fake },
            start = { _, _ -> },
            release = { it.released++ },
        )
        assertEquals(EngineInit.Failed(ErrorType.TIMEOUT), result)
        assertEquals(1, fake.released)
    }

    @Test
    fun throwingCreateOrStartIsFailedUnknown() = runTest {
        val fake = Fake()
        val created = initEngine<Fake>(10_000, create = { error("no service") }, start = { _, _ -> }, release = { it.released++ })
        assertEquals(EngineInit.Failed(ErrorType.UNKNOWN), created)
        val started = initEngine(10_000, create = { fake }, start = { _, _ -> error("boom") }, release = { it.released++ })
        assertEquals(EngineInit.Failed(ErrorType.UNKNOWN), started)
        assertEquals(1, fake.released)
        assertTrue(created is EngineInit.Failed)
    }
}
