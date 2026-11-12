package ai.hey.feature.trip

import ai.hey.designsystem.theme.HeyTheme
import ai.hey.session.CarUiState
import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

private fun device(width: Int, height: Int) = DeviceConfig.PIXEL_C.copy(
    screenWidth = width,
    screenHeight = height,
    xdpi = 160,
    ydpi = 160,
    density = Density.MEDIUM,
    orientation = ScreenOrientation.LANDSCAPE,
)

/** Every named state the previews show, dark and light. */
private val shots: List<Pair<String, CarUiState>> = listOf(
    "d1-waiting" to TripSamples.waiting,
    "d1-waiting-offline" to TripSamples.waitingOffline,
    "d1-register-failed" to TripSamples.registerFailed,
    "d1-protocol-mismatch" to TripSamples.protocolMismatch,
    "d1-speech-unavailable" to TripSamples.waitingSpeechIssue,
    "d1-navi-unavailable" to TripSamples.waitingNaviIssue,
    "d2-join-parked" to TripSamples.join,
    "d2-join-driving-d5" to TripSamples.joinDriving,
    "d3-card-waiting" to TripSamples.cardWaiting,
    "d3-card" to TripSamples.cardOk,
    "d3-card-four" to TripSamples.cardFourConditions,
    "d3-end-dialog" to TripSamples.cardEndDialog,
    "d3-in-trip" to TripSamples.cardInTrip,
    "d3-card-long" to TripSamples.cardLong,
    "d3-route-started" to TripSamples.cardRouteStarted,
    "d3-route-requested" to TripSamples.cardRouteRequested,
    "d3-route-confirm" to TripSamples.cardRouteConfirm,
    "d3-route-not-ready" to TripSamples.cardRouteNotReady,
    "d3-route-confirm-timeout" to TripSamples.cardRouteConfirmTimeout,
    "d3-route-failed" to TripSamples.cardRouteFailed,
    "d3-route-stale" to TripSamples.cardRouteStale,
    "d4-dropoff" to TripSamples.dropoffDefault,
    "d4-dropoff-selected" to TripSamples.dropoffSelected,
    "d4-dropoff-none" to TripSamples.dropoffNone,
    "d4-dropoff-ok" to TripSamples.dropoffOk,
    "d4-dropoff-elsewhere" to TripSamples.dropoffElsewhere,
    "d4-dropoff-unsure" to TripSamples.dropoffUnsure,
    "d5-driving-none" to TripSamples.drivingNone,
    "d5-driving-request" to TripSamples.drivingRequest,
    "d5-driving-stale" to TripSamples.drivingStale,
    "d5-driving-long" to TripSamples.drivingLong,
    "d6-requests" to TripSamples.requestsPlain,
    "d6-requests-many" to TripSamples.requestsMany,
    "d6-requests-no-question" to TripSamples.requestsNoQuestion,
    "d6-requests-driver-first" to TripSamples.requestsDriverFirst,
    "d6-voice-listening" to TripSamples.requestsListening,
    "d6-voice-confirm" to TripSamples.requestsVoiceConfirm,
    "d6-voice-sending" to TripSamples.requestsVoiceSending,
    "d6-voice-no-speech" to TripSamples.requestsVoiceNoSpeech,
    "d6-voice-permission" to TripSamples.requestsVoicePermission,
    "d6-voice-sent" to TripSamples.requestsVoiceSent,
    "d6-requests-replying" to TripSamples.requestsReplying,
    "d6-requests-notice-only" to TripSamples.requestsNoticeOnly,
    "d6-requests-connected" to TripSamples.requestsConnected,
    "d6-requests-card-sent" to TripSamples.requestsCardSent,
    "d6-requests-arrived" to TripSamples.requestsArrived,
    "d7-arrived" to TripSamples.arrivedAgreed,
    "d7-arrived-none" to TripSamples.arrivedNoAgreement,
    "d7-arrived-dialog" to TripSamples.arrivedDialog,
    "d7-arrived-offline" to TripSamples.arrivedOffline,
)

private fun Paparazzi.recordAll() {
    shots.forEach { (name, state) ->
        listOf(true, false).forEach { dark ->
            snapshot("$name-${if (dark) "dark" else "light"}") {
                CompositionLocalProvider(LocalClockZone provides TripSamples.ZONE) {
                    HeyTheme(dark) { TripScreen(state, NoTripCommands, TripSamples.NOW) }
                }
            }
        }
    }
}

/** 1600x900dp at 160dpi: the reference car display. */
class Trip1600SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1600, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}

/** 1280x720dp must not break either. */
class Trip1280SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1280, 720), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}
