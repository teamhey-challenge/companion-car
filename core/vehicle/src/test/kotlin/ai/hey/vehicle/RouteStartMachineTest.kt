package ai.hey.vehicle

import ai.hey.model.Place
import ai.hey.model.RouteInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteStartMachineTest {
    private val place = Place("p1", "Hotel", "호텔", "서울 중구", "서울", 37.5, 127.0)
    private val route = RouteInfo(durationSec = 3_480, distanceM = 22_000, tollFare = 700)

    private class Rig(scope: CoroutineScope, var ready: Boolean = true, var throwOnSend: Boolean = false) {
        val sent = ArrayList<Place>()
        var probes = 0
        var aborts = 0
        val machine = RouteStartMachine(
            scope = scope,
            isReady = { ready },
            probe = { probes++ },
            send = { if (throwOnSend) throw IllegalStateException("boom") else sent += it },
            abort = { aborts++ },
        )
        val state get() = machine.state.value
    }

    private fun TestScope.rig(ready: Boolean = true, throwOnSend: Boolean = false) = Rig(backgroundScope, ready, throwOnSend)

    @Test fun readyNavigationAcceptsTheRequestAndWaitsForTheConfirm() = runTest {
        val r = rig()
        r.machine.start(place)
        assertEquals(RouteStartState.Requested, r.state)

        advanceTimeBy(1)

        assertEquals(RouteStartState.AwaitingNaviConfirm, r.state)
        assertEquals(listOf(place), r.sent)
    }

    @Test fun onRouteStartedCompletesAnAttemptThatWaitsForIt() = runTest {
        val r = rig()
        r.machine.start(place)
        advanceTimeBy(1)

        r.machine.onRouteStarted(route)

        assertEquals(RouteStartState.Started(1, route), r.state)
    }

    /** No 10 s failure: a person pressing 안내 시작 in the preview later than that still succeeds. */
    @Test fun noTimeoutFailureWhileAwaitingTheConfirm() = runTest {
        val r = rig()
        r.machine.start(place)
        advanceTimeBy(1)

        advanceTimeBy(RouteStartMachine.CONFIRM_TIMEOUT_MS - 2_000)
        assertEquals(RouteStartState.AwaitingNaviConfirm, r.state)

        r.machine.onRouteStarted(route)
        assertEquals(RouteStartState.Started(1, route), r.state)
    }

    @Test fun confirmCapIsThreeMinutesWithItsOwnCode() = runTest {
        val r = rig()
        r.machine.start(place)
        advanceTimeBy(1)

        advanceTimeBy(RouteStartMachine.CONFIRM_TIMEOUT_MS + 1)

        assertEquals(RouteStartState.Failed(1, RouteFailure.NAVI_CONFIRM_TIMEOUT), r.state)
        assertEquals("the preview is closed on the timeout", 1, r.aborts)
    }

    @Test fun cancelWhileTheNavigationShowsThePreviewClosesIt() = runTest {
        val r = rig()
        r.machine.start(place)
        advanceTimeBy(1)

        r.machine.cancel()

        assertEquals(1, r.aborts)
    }

    @Test fun cancelBeforeTheRequestLeftOrAfterStartedClosesNothing() = runTest {
        val early = rig(ready = false)
        early.machine.start(place)
        advanceTimeBy(1_000)
        early.machine.cancel()
        assertEquals(0, early.aborts)

        val done = rig()
        done.machine.start(place)
        advanceTimeBy(1)
        done.machine.onRouteStarted(route)
        done.machine.cancel()
        assertEquals("a started route belongs to the driver now", 0, done.aborts)
    }

    @Test fun aNavigationErrorClosesThePreviewAndAnUnrelatedErrorWhileRequestedIsNotConsumed() = runTest {
        val waiting = rig(ready = false)
        waiting.machine.start(place)
        advanceTimeBy(1_000)
        assertFalse("nothing was sent yet, the error is not ours", waiting.machine.onError("CAUSE_LOW_BATTERY"))
        assertEquals(RouteStartState.Requested, waiting.state)
        assertEquals(0, waiting.aborts)

        val sent = rig()
        sent.machine.start(place)
        advanceTimeBy(1)
        assertTrue(sent.machine.onError("CAUSE_SEARCH_ROUTE"))
        assertEquals(1, sent.aborts)
    }

    @Test fun aLateRouteStartedAfterCancelIsIgnored() = runTest {
        val r = rig()
        r.machine.start(place)
        advanceTimeBy(1)
        r.machine.cancel()
        assertEquals(RouteStartState.Idle, r.state)

        r.machine.onRouteStarted(route)

        assertEquals(RouteStartState.Idle, r.state)
    }

    @Test fun aRouteStartedWithoutAnAttemptIsIgnored() = runTest {
        val r = rig()

        r.machine.onRouteStarted(route)

        assertEquals(RouteStartState.Idle, r.state)
    }

    @Test fun aRouteStartedAfterAFailureIsIgnored() = runTest {
        val r = rig()
        r.machine.start(place)
        advanceTimeBy(1)
        assertTrue(r.machine.onError("CAUSE_SEARCH_ROUTE"))

        r.machine.onRouteStarted(route)

        assertEquals(RouteStartState.Failed(1, "CAUSE_SEARCH_ROUTE"), r.state)
    }

    @Test fun aCancelledAttemptDoesNotTimeOutIntoAFailureLater() = runTest {
        val r = rig()
        r.machine.start(place)
        advanceTimeBy(1)
        r.machine.cancel()

        advanceTimeBy(RouteStartMachine.CONFIRM_TIMEOUT_MS * 2)

        assertEquals(RouteStartState.Idle, r.state)
    }

    @Test fun anErrorWithoutAnAttemptIsNotConsumed() = runTest {
        val r = rig()

        assertFalse(r.machine.onError("CAUSE_LOW_BATTERY"))
        assertEquals(RouteStartState.Idle, r.state)
    }

    @Test fun navigationThatNeverAnswersFailsAfterFifteenSecondsAsNotReady() = runTest {
        val r = rig(ready = false)
        r.machine.start(place)

        advanceTimeBy(14_000)
        assertEquals(RouteStartState.Requested, r.state)
        assertTrue("it keeps probing while it waits", r.probes >= 2)

        advanceTimeBy(1_600)
        assertEquals(RouteStartState.Failed(1, RouteFailure.NAVI_NOT_READY), r.state)
        assertTrue("nothing was sent to a navigation that is not ready", r.sent.isEmpty())
    }

    @Test fun navigationThatBecomesReadyWithinTheWindowGetsTheRequest() = runTest {
        val r = rig(ready = false)
        r.machine.start(place)
        advanceTimeBy(6_000)
        assertEquals(RouteStartState.Requested, r.state)

        r.ready = true
        advanceTimeBy(1_000)

        assertEquals(RouteStartState.AwaitingNaviConfirm, r.state)
        assertEquals(listOf(place), r.sent)
    }

    @Test fun aRetryAfterNotReadyStartsANewAttempt() = runTest {
        val r = rig(ready = false)
        r.machine.start(place)
        advanceTimeBy(16_000)
        assertEquals(RouteStartState.Failed(1, RouteFailure.NAVI_NOT_READY), r.state)

        r.ready = true
        r.machine.start(place)
        advanceTimeBy(1)
        r.machine.onRouteStarted(route)

        assertEquals(RouteStartState.Started(2, route), r.state)
    }

    @Test fun aSendThatThrowsFailsWithNaviError() = runTest {
        val r = rig(throwOnSend = true)
        r.machine.start(place)
        advanceTimeBy(1)

        assertEquals(RouteStartState.Failed(1, RouteFailure.NAVI_ERROR), r.state)
    }

    @Test fun aNavigationErrorFailsTheWaitingAttemptWithItsCode() = runTest {
        val r = rig()
        r.machine.start(place)
        advanceTimeBy(1)

        r.machine.onError("CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE")

        assertEquals(RouteStartState.Failed(1, "CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE"), r.state)
    }

    @Test fun startWhileAnAttemptIsActiveIsIgnored() = runTest {
        val r = rig()
        r.machine.start(place)
        advanceTimeBy(1)

        r.machine.start(place)
        advanceTimeBy(1)

        assertEquals(1, r.sent.size)
        assertEquals(RouteStartState.AwaitingNaviConfirm, r.state)
    }

    @Test fun cancelDuringTheReadyWaitSendsNothing() = runTest {
        val r = rig(ready = false)
        r.machine.start(place)
        advanceTimeBy(2_000)
        r.machine.cancel()
        r.ready = true

        advanceTimeBy(20_000)

        assertEquals(RouteStartState.Idle, r.state)
        assertTrue(r.sent.isEmpty())
    }
}
