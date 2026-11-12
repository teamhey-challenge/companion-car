package ai.hey.model

/** Facts derived from a car snapshot. Pure, shared by the session (what to speak/send) and the screens (what to show). */
object HeyIds {
    /** The relay uses the request id as `speechId` for speech caused by `request.send` (relay README). */
    fun isRequestSpeech(speechId: String): Boolean = speechId.startsWith("req_")
}

/** A question the driver can answer with a quick reply: expected, not answered yet. */
val RequestItem.isOpenQuestion: Boolean
    get() = replyExpected && state != RequestState.Answered && state != RequestState.Failed

/** Requests that still wait for the driver: unread ones and open questions. */
val RequestItem.isPending: Boolean
    get() = state == RequestState.Received || isOpenQuestion

/** Open questions waiting for an answer: what D6 opens for (passenger pins no longer exist). */
val CarView.actionableCount: Int
    get() = requests.count { it.isOpenQuestion }

val CarView.pendingRequestCount: Int
    get() = requests.count { it.isPending }

/** The oldest open question: the quick reply buttons answer this one. */
val CarView.replyTarget: RequestItem?
    get() = requests.firstOrNull { it.isOpenQuestion }

/** Destination as handed to navigation: the agreed drop-off point wins over the passenger's destination. */
fun CarView.routeDestination(): Place? {
    val dest = card?.destination ?: return null
    val agreed = agreedDropoff ?: return dest
    return dest.copy(lat = agreed.point.lat, lng = agreed.point.lng, nameKo = "${dest.nameKo} ${agreed.label}")
}

/**
 * Milliseconds the device clock runs ahead of the relay's, from the `ts` of a received envelope. Small jitter (1 s,
 * the latency of one message) keeps the current offset so the countdown does not twitch. Same rule as the passenger web.
 */
fun nextClockOffset(current: Long, deviceNow: Long, serverTs: Long): Long =
    if (kotlin.math.abs(deviceNow - serverTs - current) > 1_000) deviceNow - serverTs else current

/** The relay's clock, as far as the device can tell. */
fun serverNow(deviceNow: Long, clockOffsetMs: Long): Long = deviceNow - clockOffsetMs

/** The quick replies of one group, in catalog order. */
fun quickRepliesOf(set: QuickReplySet): List<QuickReplyId> = QuickReplyId.entries.filter { it.set == set }

/** Great-circle distance in metres (haversine), rounded. */
fun LatLng.distanceTo(other: LatLng): Int {
    val r = 6_371_000.0
    val p1 = Math.toRadians(lat)
    val p2 = Math.toRadians(other.lat)
    val dp = Math.toRadians(other.lat - lat)
    val dl = Math.toRadians(other.lng - lng)
    val a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
    return Math.round(2 * r * Math.asin(Math.sqrt(a))).toInt()
}
