package ai.hey.feature.trip

import ai.hey.model.AgreedDropoff
import ai.hey.model.DropoffKind
import ai.hey.model.EntranceType
import ai.hey.model.StreetName
import ai.hey.model.ArrivalSummary
import ai.hey.model.CarCardView
import ai.hey.model.CarView
import ai.hey.model.ConditionId
import ai.hey.model.DropoffBy
import ai.hey.model.DropoffCandidate
import ai.hey.model.DropoffProposal
import ai.hey.model.DrivingState
import ai.hey.model.Lang
import ai.hey.model.LatLng
import ai.hey.model.PendingJoin
import ai.hey.model.Place
import ai.hey.model.PresetId
import ai.hey.model.ProgressInfo
import ai.hey.model.ProposalStatus
import ai.hey.model.QuickReplyId
import ai.hey.model.RequestItem
import ai.hey.model.RequestKind
import ai.hey.model.RequestState
import ai.hey.model.RouteInfo
import ai.hey.model.Temperature
import ai.hey.model.TripState
import ai.hey.session.Candidates
import ai.hey.session.CarUiState
import ai.hey.session.CommandKey
import ai.hey.session.CommandState
import ai.hey.session.ConnectionStatus
import ai.hey.session.DropoffDraft
import ai.hey.session.Panel
import ai.hey.session.VehicleIssue
import ai.hey.vehicle.DrivingStatus
import ai.hey.vehicle.SpeechDropReason

/**
 * States every screen is previewed and snapshotted in. Real-shaped data (a hotel in Jung-gu, long Korean names),
 * not placeholders. [NOW] is the clock the previews show.
 */
object TripSamples {
    /** 2026-11-12 23:42 KST */
    const val NOW = 1_794_580_920_000L

    val hotel = Place(
        placeId = "fake:lotte-hotel-seoul",
        nameLocal = "ロッテホテル ソウル",
        nameKo = "롯데호텔 서울",
        addressKo = "서울 중구 을지로 30",
        area = "서울 중구",
        lat = 37.5655,
        lng = 126.9811,
    )

    val longPlace = Place(
        placeId = "osm:long",
        nameLocal = "请把我们送到首尔国际金融中心地下三层停车场B区东侧电梯厅前的上下车位置谢谢您",
        nameKo = "서울국제금융센터 지하 3층 주차장 B구역 동쪽 엘리베이터 홀 앞 승강장",
        addressKo = "서울특별시 영등포구 국제금융로 10 서울국제금융센터 지하 3층 주차장 B구역 동쪽 엘리베이터 홀 앞",
        area = "서울 영등포구",
        lat = 37.5251,
        lng = 126.9253,
    )

    val card = CarCardView(
        destination = hotel,
        conditions = listOf(ConditionId.Luggage, ConditionId.MeetEntrance, ConditionId.Temperature),
        conditionLabelsKo = listOf("짐 있음", "만나기로 한 입구", "실내 온도"),
        entranceNote = "을지로 쪽 옆문",
        temperature = Temperature.Cooler,
        temperatureLabelKo = "시원하게",
    )

    val fourConditionsCard = card.copy(
        conditions = ConditionId.entries,
        conditionLabelsKo = listOf("짐 있음", "덜 걷기", "만나기로 한 입구", "실내 온도"),
    )

    val longCard = fourConditionsCard.copy(destination = longPlace)

    val candidates = Candidates(
        forPlaceId = hotel.placeId,
        items = listOf(
            DropoffCandidate("default", DropoffKind.Default, null, null, null, "목적지 기본 위치", "목적지 기본 위치", LatLng(37.5655, 126.9811), 0.0),
            DropoffCandidate("fake-1", DropoffKind.Entrance, EntranceType.Main, null, StreetName("을지로", "Eulji-ro"), "을지로 쪽 정문", "을지로 쪽 정문", LatLng(37.5657, 126.9814), 30.0),
            DropoffCandidate("fake-2", DropoffKind.Entrance, EntranceType.Side, null, StreetName("을지로", "Eulji-ro"), "을지로 쪽 옆 출입구", "을지로 쪽 옆 출입구", LatLng(37.5649, 126.9803), 78.0),
            DropoffCandidate("fake-3", DropoffKind.Entrance, EntranceType.Parking, null, null, "주차장 입구", "주차장 입구", LatLng(37.5659, 126.9806), 62.0),
        ),
    )

    private fun request(
        id: String,
        text: String,
        question: Boolean,
        state: RequestState,
        ts: Long,
        kind: RequestKind = RequestKind.Preset,
        preset: PresetId? = null,
    ) = RequestItem(id, kind, preset, text, question, state, ts = ts)

    val requests = listOf(
        request("req_1", "조금 천천히 가 주세요.", false, RequestState.Spoken, NOW - 240_000, preset = PresetId.Slower),
        request("req_2", "창문을 열어도 될까요?", true, RequestState.Spoken, NOW - 120_000, preset = PresetId.Window),
        request("req_3", "공항에서 나오면 바로 짐을 실을 수 있을까요? 트렁크가 크면 좋겠어요.", true, RequestState.Received, NOW - 60_000, RequestKind.Free),
    )

    val manyRequests = requests + listOf(
        request("req_4", "카드로 결제할 수 있나요?", true, RequestState.Spoken, NOW - 50_000, preset = PresetId.Card),
        request("req_5", "도착까지 얼마나 걸리나요?", true, RequestState.Answered, NOW - 40_000, preset = PresetId.HowLong),
        request("req_6", "에어컨을 조금 세게 틀어 주실 수 있을까요?", false, RequestState.Spoken, NOW - 30_000, preset = PresetId.Cooler),
        request("req_7", "트렁크를 열어 주세요.", false, RequestState.Spoken, NOW - 20_000, preset = PresetId.Trunk),
    )

    val pin = DropoffProposal(2, DropoffBy.Passenger, LatLng(37.5651, 126.9807), DropoffKind.Pin, null, null, null, "내가 고른 곳", "내가 고른 곳", status = ProposalStatus.Pending)

    private val base = CarView(
        state = TripState.IDLE,
        carCode = "K7M2QX4R",
        plateSuffix = "1234",
        driving = DrivingState.PARKED,
    )

    private val route = RouteInfo(durationSec = 2_520, distanceM = 18_400, tollFare = 0)

    fun state(
        view: CarView? = base,
        driving: DrivingStatus = DrivingStatus.Parked,
        connection: ConnectionStatus = ConnectionStatus.Online,
        panel: Panel = Panel.None,
        qr: String? = "https://hey.example/c/K7M2QX4R",
        build: CarUiState.() -> CarUiState = { this },
    ): CarUiState = CarUiState(
        connection = connection,
        view = view?.copy(driving = if (driving == DrivingStatus.Parked) DrivingState.PARKED else DrivingState.DRIVING),
        driving = driving,
        qrUrl = qr,
        panel = panel,
    ).build()

    // ---- D1
    val waiting = state()
    val waitingOffline = state(connection = ConnectionStatus.Reconnecting)
    val registerFailed = state(view = null, connection = ConnectionStatus.RegistrationFailed, qr = null)
    val waitingSpeechIssue = state { copy(issues = setOf(VehicleIssue.Speech(SpeechDropReason.EngineUnavailable))) }
    val waitingNaviIssue = state { copy(issues = setOf(VehicleIssue.Navi("NAVI_UNAVAILABLE"))) }

    // ---- D2
    private val requested = base.copy(
        state = TripState.REQUESTED,
        pendingJoin = PendingJoin("jr_1", Lang.En, 2, NOW + 252_000),
        partySize = 2,
        passengerLang = Lang.En,
    )
    val join = state(requested)
    val joinDriving = state(requested, DrivingStatus.Driving)

    // ---- D3
    private val connected = base.copy(state = TripState.CONNECTED, tripId = "trp_1", partySize = 2, passengerLang = Lang.Ja, deviceCount = 1)
    private val cardSent = connected.copy(state = TripState.CARD_SENT, card = card)
    val cardWaiting = state(connected)
    val cardOk = state(cardSent)
    val cardFourConditions = state(cardSent.copy(card = fourConditionsCard)) { copy(changedConditions = setOf(ConditionId.Temperature)) }
    val cardLong = state(cardSent.copy(card = longCard))
    val cardRouteStarted = state(cardSent.copy(route = route))
    val cardRouteFailed = state(cardSent) { copy(commands = mapOf(CommandKey.StartRoute to CommandState.Failed("NAVI_UNAVAILABLE"))) }
    val cardRouteStale = state(
        cardSent.copy(route = route, routeStale = true, agreedDropoff = AgreedDropoff(1, DropoffBy.Driver, LatLng(37.5649, 126.9803), DropoffKind.Entrance, EntranceType.Side, null, StreetName("을지로", "Eulji-ro"), "을지로 쪽 옆 출입구", "을지로 쪽 옆 출입구")),
        driving = DrivingStatus.Parked,
    )

    // ---- D4
    private val withPin = cardSent.copy(dropoff = pin, pendingPins = listOf(pin))
    val dropoffDefault = state(cardSent, panel = Panel.Dropoff) { copy(candidates = TripSamples.candidates) }
    val dropoffSelected = state(cardSent, panel = Panel.Dropoff) {
        copy(candidates = TripSamples.candidates, draft = DropoffDraft(0, "fake-2", 1))
    }
    val dropoffNone = state(cardSent, panel = Panel.Dropoff) { copy(candidates = Candidates(hotel.placeId, emptyList())) }
    val dropoffPin = state(withPin, panel = Panel.Dropoff) { copy(candidates = TripSamples.candidates) }
    private fun answered(status: ProposalStatus) = cardSent.copy(
        dropoff = DropoffProposal(1, DropoffBy.Driver, LatLng(37.5649, 126.9803), DropoffKind.Entrance, EntranceType.Side, null, StreetName("을지로", "Eulji-ro"), "을지로 쪽 옆 출입구", "을지로 쪽 옆 출입구", status = status),
    )
    val dropoffOk = state(answered(ProposalStatus.Ok), panel = Panel.Dropoff) { copy(candidates = TripSamples.candidates) }
    val dropoffElsewhere = state(answered(ProposalStatus.Elsewhere), panel = Panel.Dropoff) { copy(candidates = TripSamples.candidates) }
    val dropoffUnsure = state(answered(ProposalStatus.Unsure), panel = Panel.Dropoff) { copy(candidates = TripSamples.candidates) }

    // ---- D5
    private val inTrip = cardSent.copy(
        state = TripState.IN_TRIP,
        route = route,
        progress = ProgressInfo(1_380, 14_200, stale = false),
    )
    val drivingNone = state(inTrip, DrivingStatus.Driving)
    val drivingRequest = state(inTrip.copy(requests = requests), DrivingStatus.Driving) { copy(lastSpokenKo = "창문을 열어도 될까요?", progressAt = NOW) }
    val drivingStale = state(inTrip.copy(requests = requests), DrivingStatus.Driving) {
        copy(lastSpokenKo = "창문을 열어도 될까요?", progressAt = NOW - 600_000)
    }
    val drivingLong = state(inTrip.copy(card = longCard, requests = manyRequests), DrivingStatus.Driving) {
        copy(lastSpokenKo = manyRequests.last().text)
    }

    // ---- D6
    val requestsPlain = state(inTrip.copy(requests = requests))
    val requestsMany = state(inTrip.copy(requests = manyRequests))
    val requestsPin = state(inTrip.copy(requests = requests, dropoff = pin, pendingPins = listOf(pin)))
    val requestsReplying = state(inTrip.copy(requests = requests)) {
        copy(commands = mapOf(CommandKey.Reply("req_2", QuickReplyId.Yes, 0) to CommandState.InFlight("env")))
    }
    val requestsNoticeOnly = state(inTrip.copy(requests = requests.filter { !it.replyExpected }))

    // ---- D7
    private val arrived = inTrip.copy(
        state = TripState.ARRIVED,
        progress = null,
        requests = requests,
        replies = emptyList(),
        agreedDropoff = AgreedDropoff(1, DropoffBy.Driver, LatLng(37.5649, 126.9803), DropoffKind.Entrance, EntranceType.Side, null, StreetName("을지로", "Eulji-ro"), "을지로 쪽 옆 출입구", "을지로 쪽 옆 출입구"),
        summary = ArrivalSummary(hotel, null, 2_520, 18_400, 0, NOW - 2_520_000, NOW),
    )
    val arrivedAgreed = state(arrived)
    val arrivedNoAgreement = state(arrived.copy(agreedDropoff = null))
    val arrivedDialog = state(arrived) { copy(endDialogOpen = true) }
    val arrivedOffline = state(arrived, connection = ConnectionStatus.Reconnecting)
}
