package ai.hey.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Hand-mirrored from packages/protocol/src/{catalog,types}.ts. The fixtures in packages/protocol/fixtures
// are parsed and round-tripped by ModelFixturesTest, which is what keeps the two sides the same shape.

@Serializable
enum class Lang(val wire: String, val label: String) {
    @SerialName("en") En("en", "English"),
    @SerialName("ja") Ja("ja", "日本語"),
    @SerialName("zh-Hans") ZhHans("zh-Hans", "简体中文"),
    @SerialName("ko") Ko("ko", "한국어"),
}

@Serializable
enum class ConditionId {
    @SerialName("luggage") Luggage,
    @SerialName("lessWalking") LessWalking,
    @SerialName("meetEntrance") MeetEntrance,
    @SerialName("temperature") Temperature,
}

@Serializable
enum class Temperature {
    @SerialName("cooler") Cooler,
    @SerialName("warmer") Warmer,
}

@Serializable
enum class PresetId(val driverCanDisable: Boolean, val question: Boolean) {
    @SerialName("slower") Slower(true, false),
    @SerialName("cooler") Cooler(true, false),
    @SerialName("warmer") Warmer(true, false),
    @SerialName("window") Window(true, true),
    @SerialName("card") Card(false, true),
    @SerialName("howLong") HowLong(true, true),
    @SerialName("stopHere") StopHere(false, false),
    @SerialName("trunk") Trunk(true, false),
}

@Serializable
enum class QuickReplyId {
    @SerialName("yes") Yes,
    @SerialName("moment") Moment,
    @SerialName("fiveMin") FiveMin,
    @SerialName("stopHere") StopHere,
    @SerialName("cardOk") CardOk,
    @SerialName("cannot") Cannot,
}

@Serializable
enum class DropoffReasonId {
    @SerialName("frontNoStop") FrontNoStop,
    @SerialName("sideCloser") SideCloser,
    @SerialName("luggageEasier") LuggageEasier,
    @SerialName("parkingEntrance") ParkingEntrance,
    @SerialName("acrossSafer") AcrossSafer,
    @SerialName("construction") Construction,
}

@Serializable
enum class ErrorCode {
    CAR_NOT_FOUND, CAR_BUSY, CAR_OFFLINE, JOIN_DECLINED, JOIN_EXPIRED, TRIP_FULL, TRIP_ENDED, NOT_LEAD,
    RATE_LIMITED, TOO_LONG, TRANSLATE_FAILED, PLACE_NOT_FOUND, ROUTE_FAILED, STALE_VERSION, BAD_REQUEST,
}

@Serializable
enum class NoticeKind {
    @SerialName("joinApproved") JoinApproved,
    @SerialName("recheck") Recheck,
    @SerialName("routeStarted") RouteStarted,
    @SerialName("routeFailed") RouteFailed,
    @SerialName("dropoffProposed") DropoffProposed,
    @SerialName("dropoffPinned") DropoffPinned,
    @SerialName("dropoffAgreed") DropoffAgreed,
    @SerialName("dropoffDeclined") DropoffDeclined,
    @SerialName("reply") Reply,
    @SerialName("arrived") Arrived,
    @SerialName("guestJoined") GuestJoined,
}

/** `IDLE` is the car-only "no trip" state. */
@Serializable
enum class TripState { IDLE, REQUESTED, CONNECTED, CARD_SENT, IN_TRIP, ARRIVED, ENDED }

@Serializable
enum class DrivingState { PARKED, DRIVING }

@Serializable
enum class RequestState {
    @SerialName("received") Received,
    @SerialName("spoken") Spoken,
    @SerialName("answered") Answered,
    @SerialName("failed") Failed,
}

@Serializable
enum class RequestKind {
    @SerialName("preset") Preset,
    @SerialName("free") Free,
}

@Serializable
enum class DropoffBy {
    @SerialName("driver") Driver,
    @SerialName("passenger") Passenger,
}

/** Driver proposals use pending..superseded, passenger pins use pending/accepted/declined/superseded. */
@Serializable
enum class ProposalStatus {
    @SerialName("pending") Pending,
    @SerialName("ok") Ok,
    @SerialName("elsewhere") Elsewhere,
    @SerialName("unsure") Unsure,
    @SerialName("superseded") Superseded,
    @SerialName("accepted") Accepted,
    @SerialName("declined") Declined,
}

@Serializable
enum class DriverDecision {
    @SerialName("accept") Accept,
    @SerialName("decline") Decline,
}

@Serializable
data class LatLng(val lat: Double, val lng: Double)

@Serializable
data class Place(
    val placeId: String,
    val nameLocal: String,
    val nameKo: String,
    val addressKo: String,
    val area: String,
    val lat: Double,
    val lng: Double,
)

@Serializable
enum class DropoffKind {
    @SerialName("default") Default,
    @SerialName("entrance") Entrance,
    @SerialName("side") Side,
    @SerialName("curated") Curated,
    @SerialName("pin") Pin,
}

@Serializable
enum class EntranceType {
    @SerialName("main") Main,
    @SerialName("side") Side,
    @SerialName("service") Service,
    @SerialName("parking") Parking,
}

@Serializable
data class StreetName(val ko: String, val latin: String? = null)

/** A drop-off point described by structure; `label` is the Korean text the car shows, `labelLocal` is the reader's language. */
@Serializable
data class DropoffCandidate(
    val candidateId: String,
    val kind: DropoffKind,
    val entranceType: EntranceType? = null,
    val ref: String? = null,
    val street: StreetName? = null,
    val label: String,
    val labelLocal: String,
    val point: LatLng,
    val distanceM: Double,
)

@Serializable
data class DropoffProposal(
    val version: Int,
    val by: DropoffBy,
    val point: LatLng,
    val kind: DropoffKind,
    val entranceType: EntranceType? = null,
    val ref: String? = null,
    val street: StreetName? = null,
    val label: String,
    val labelLocal: String,
    val reasonId: DropoffReasonId? = null,
    val reasonText: String? = null,
    val status: ProposalStatus,
)

@Serializable
data class AgreedDropoff(
    val version: Int,
    val by: DropoffBy,
    val point: LatLng,
    val kind: DropoffKind,
    val entranceType: EntranceType? = null,
    val ref: String? = null,
    val street: StreetName? = null,
    val label: String,
    val labelLocal: String,
)

@Serializable
data class CardInput(
    val destination: Place,
    val conditions: List<ConditionId>,
    val entranceNote: String? = null,
    val temperature: Temperature? = null,
)

@Serializable
data class CardUpdate(
    val destination: Place? = null,
    val conditions: List<ConditionId>? = null,
    val entranceNote: String? = null,
    val temperature: Temperature? = null,
)

@Serializable
data class RequestItem(
    val requestId: String,
    val kind: RequestKind,
    val presetId: PresetId? = null,
    val text: String,
    val replyExpected: Boolean,
    val state: RequestState,
    val replyId: QuickReplyId? = null,
    val replyText: String? = null,
    val ts: Long,
)

@Serializable
data class ReplyItem(val replyId: QuickReplyId, val requestId: String? = null, val text: String, val ts: Long)

@Serializable
data class RouteInfo(val durationSec: Int, val distanceM: Int, val tollFare: Int)

@Serializable
data class ProgressInfo(val etaSec: Int, val distanceM: Int, val stale: Boolean)

@Serializable
data class ArrivalDropoff(
    val point: LatLng,
    val kind: DropoffKind,
    val entranceType: EntranceType? = null,
    val ref: String? = null,
    val street: StreetName? = null,
    val label: String,
    val labelLocal: String,
)

@Serializable
data class ArrivalSummary(
    val destination: Place,
    val dropoff: ArrivalDropoff? = null,
    /** Seconds from route.started to arrival, measured by the relay. */
    val elapsedSec: Int? = null,
    /** Planned route distance reported at route.started, not the driven distance. */
    val routeDistanceM: Int? = null,
    val tollFare: Int? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
)

@Serializable
data class DeviceYou(val role: String, val lang: Lang)

@Serializable
data class PassengerCar(val plateSuffix: String, val online: Boolean)

/** Passenger read model. The car app never receives it; it is parsed only by the fixture test. */
@Serializable
data class PassengerTripView(
    val role: String,
    val tripId: String,
    val state: String,
    val you: DeviceYou,
    val partySize: Int,
    val deviceCount: Int,
    val inviteCode: String? = null,
    val car: PassengerCar,
    val driving: DrivingState,
    val joinExpiresAt: Long? = null,
    val card: CardInput? = null,
    val route: RouteInfo? = null,
    val routeFailed: Boolean,
    val progress: ProgressInfo? = null,
    val dropoff: DropoffProposal? = null,
    val agreedDropoff: AgreedDropoff? = null,
    val requests: List<RequestItem>,
    val replies: List<ReplyItem>,
    val summary: ArrivalSummary? = null,
    val endReason: String? = null,
)

@Serializable
data class CarCardView(
    val destination: Place,
    val conditions: List<ConditionId>,
    val conditionLabelsKo: List<String>,
    val entranceNote: String? = null,
    val temperature: Temperature? = null,
    val temperatureLabelKo: String? = null,
)

@Serializable
data class PendingJoin(val joinRequestId: String, val lang: Lang, val partySize: Int, val expiresAt: Long)

@Serializable
data class CarView(
    val role: String = "car",
    val state: TripState,
    val carCode: String,
    val plateSuffix: String,
    val driving: DrivingState,
    val pendingJoin: PendingJoin? = null,
    val tripId: String? = null,
    val passengerLang: Lang? = null,
    val partySize: Int? = null,
    val deviceCount: Int? = null,
    val card: CarCardView? = null,
    val route: RouteInfo? = null,
    val routeStale: Boolean = false,
    val progress: ProgressInfo? = null,
    val dropoff: DropoffProposal? = null,
    val pendingPins: List<DropoffProposal> = emptyList(),
    val agreedDropoff: AgreedDropoff? = null,
    val requests: List<RequestItem> = emptyList(),
    val replies: List<ReplyItem> = emptyList(),
    val summary: ArrivalSummary? = null,
)

@Serializable
enum class SpeechPriority {
    @SerialName("high") High,
    @SerialName("normal") Normal,
}

@Serializable
data class SayData(val speechId: String, val textKo: String, val priority: SpeechPriority)

@Serializable
data class NoticeData(val noticeId: String, val kind: NoticeKind)

@Serializable
data class ErrorData(val code: ErrorCode, val ref: String? = null)

@Serializable
data class CreateCarRequest(val plateSuffix: String)

@Serializable
data class CreateCarResponse(val carId: String, val carCode: String, val driverToken: String, val qrUrl: String)
