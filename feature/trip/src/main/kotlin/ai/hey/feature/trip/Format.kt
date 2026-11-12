package ai.hey.feature.trip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Wall clock that ticks every [intervalMs]; screens read it for countdowns and "n분 전" style text. */
@Composable
fun rememberNowMillis(intervalMs: Long = 1_000L): State<Long> = produceState(System.currentTimeMillis(), intervalMs) {
    while (true) {
        delay(intervalMs)
        value = System.currentTimeMillis()
    }
}

private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREA)

internal fun clockText(epochMs: Long, zone: ZoneId): String = clockFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))

@Composable
internal fun durationText(seconds: Int): String {
    val minutes = ((seconds + 59) / 60).coerceAtLeast(1)
    return if (minutes < 60) {
        stringResource(R.string.trip_format_minutes, minutes)
    } else {
        stringResource(R.string.trip_format_hours_minutes, minutes / 60, minutes % 60)
    }
}

@Composable
internal fun distanceText(meters: Int): String =
    if (meters < 1_000) {
        stringResource(R.string.trip_format_meters, meters)
    } else {
        stringResource(R.string.trip_format_km, "%.1f".format(Locale.KOREA, meters / 1000.0))
    }

/**
 * The zone every clock text uses: the system's, read at each use so a zone change on the head unit shows at once.
 * The one place a zone is chosen; previews and snapshots provide a fixed zone so their images do not depend on the machine.
 */
val LocalClockZone = staticCompositionLocalOf<ZoneId?> { null }

@Composable
internal fun clockZone(): ZoneId = LocalClockZone.current ?: ZoneId.systemDefault()

/** Clock text for the status strip outside composition (MainActivity), in the system zone. */
fun clockLabel(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String = clockText(epochMs, zone)
