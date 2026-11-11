package ai.hey.designsystem.gallery

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.ConditionLine
import ai.hey.designsystem.component.DropoffCandidateRow
import ai.hey.designsystem.component.DrivingPanel
import ai.hey.designsystem.component.EmptyPanel
import ai.hey.designsystem.component.EtaBlock
import ai.hey.designsystem.component.HeyBanner
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonSize
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyCard
import ai.hey.designsystem.component.HeyCardTone
import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.component.HeyDialog
import ai.hey.designsystem.component.HeyDialogAction
import ai.hey.designsystem.component.HeyDivider
import ai.hey.designsystem.component.HeyIcon
import ai.hey.designsystem.component.HeyIconButton
import ai.hey.designsystem.component.HeyIconSize
import ai.hey.designsystem.component.HeyQrCode
import ai.hey.designsystem.component.HeySegmentOption
import ai.hey.designsystem.component.HeySegmented
import ai.hey.designsystem.component.HeyStatusPill
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.HeyStepperRow
import ai.hey.designsystem.component.HeyScrollColumn
import ai.hey.designsystem.component.HeySection
import ai.hey.designsystem.component.HeyNotice
import ai.hey.designsystem.component.HeySwitchRow
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.HeyWordmark
import ai.hey.designsystem.component.JoinRequestCard
import ai.hey.designsystem.component.KoreanSummary
import ai.hey.designsystem.component.LanguageGreetingStrip
import ai.hey.designsystem.component.PassengerPinCard
import ai.hey.designsystem.component.ProposalState
import ai.hey.designsystem.component.ProposalStatus
import ai.hey.designsystem.component.QuickReplyGrid
import ai.hey.designsystem.component.QuickReplyUi
import ai.hey.designsystem.component.ReasonChips
import ai.hey.designsystem.component.RequestKind
import ai.hey.designsystem.component.RequestRow
import ai.hey.designsystem.component.RequestState
import ai.hey.designsystem.component.StatusStrip
import ai.hey.designsystem.component.TripSummaryCard
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.theme.HeyTone
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Where a story is rendered: a whole car display, or a part measured to its own height. */
enum class StoryFrame(val widthDp: Int, val heightDp: Int, val shrink: Boolean) {
    Screen1600(1600, 900, false),
    Screen1280(1280, 720, false),
    Part(1200, 1600, true),
}

class Story(val id: String, val frame: StoryFrame, val content: @Composable () -> Unit)

private val PartWidth: Dp = 1200.dp

/** Parts are shown on a page-coloured plate with the 32dp frame inset a real screen would give them. */
@Composable
private fun Plate(content: @Composable () -> Unit) {
    Box32 { content() }
}

@Composable
private fun Box32(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.width(PartWidth).background(HeyTheme.colors.bgPage).padding(32.dp),
    ) { content() }
}

private fun part(id: String, content: @Composable () -> Unit) = Story(id, StoryFrame.Part) { Plate(content) }

private val conditions4 = listOf(
    ConditionLine(HeyIcons.Luggage, "짐이 있어요"),
    ConditionLine(HeyIcons.Walk, "덜 걷고 싶어요"),
    ConditionLine(HeyIcons.Door, "만나기로 한 입구가 있어요: 3번 출구 앞"),
    ConditionLine(HeyIcons.ThermometerCool, "시원하게 해 주세요", changed = true),
)

private fun stripFor(party: Int, connection: HeyConnection, driving: Boolean = false): @Composable () -> Unit = {
    StatusStrip(
        wordmarkParty = party,
        drivingLabel = if (driving) "주행 중 · 음성으로 알려 드려요" else "정차 중",
        connection = connection,
        clock = "오후 11:42",
    )
}

private fun screen(id: String, frame: StoryFrame, content: @Composable () -> Unit) = Story(id, frame, content)

// ---------------------------------------------------------------- the stories

object Stories {
    val parts: List<Story> = buildList {
        // Type
        add(part("text-variants") {
            VStack(HeyGap.G8) {
                HeyTextVariant.entries.forEach { HeyText("${it.name} 가나다 라마바 사아자 ABC 123", variant = it, maxLines = 1) }
            }
        })
        add(part("text-tones") {
            VStack(HeyGap.G8) {
                HeyTone.entries.forEach { tone ->
                    HeyText(
                        "${tone.name} 톤 글자",
                        modifier = if (tone == HeyTone.OnAccent) Modifier.background(HeyTheme.colors.accent).padding(8.dp) else Modifier,
                        tone = tone,
                    )
                }
            }
        })
        add(part("text-long-ko") { HeyText(GalleryData.LONG_KO, maxLines = 2) })
        add(part("text-long-ja") { HeyText(GalleryData.LONG_JA, maxLines = 3) })
        add(part("text-long-zh") { HeyText(GalleryData.LONG_ZH, maxLines = 3) })
        add(part("text-empty") { HeyCard { HeyText("") } })
        add(part("wordmark") {
            VStack(HeyGap.G16) {
                (1..4).forEach { HStack(HeyGap.G32) { HeyWordmark(it); HeyWordmark(it, large = true) } }
            }
        })
        add(part("icons") {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) { HeyIcons.all.forEach { HeyIcon(it, it.name) } }
        })
        add(part("icons-large-tones") {
            HStack(HeyGap.G24) {
                HeyIcon(HeyIcons.Car, null, size = HeyIconSize.Large)
                HeyIcon(HeyIcons.Warning, null, size = HeyIconSize.Large, tone = HeyTone.Danger)
                HeyIcon(HeyIcons.Check, null, size = HeyIconSize.Large, tone = HeyTone.Success)
                HeyIcon(HeyIcons.Pin, null, size = HeyIconSize.Large, tone = HeyTone.Accent)
            }
        })
        add(part("divider") { VStack(HeyGap.G16) { HeyText("위"); HeyDivider(Modifier.fillMaxWidth()); HeyText("아래") } })

        // Buttons: variant x state
        HeyButtonVariant.entries.forEach { variant ->
            add(part("button-${variant.name.lowercase()}") {
                VStack(HeyGap.G16) {
                    HStack(HeyGap.G16) {
                        HeyButton("승인할게요", {}, variant = variant)
                        HeyButton("경로 시작", {}, variant = variant, icon = HeyIcons.Route)
                        HeyButton("승인할게요", {}, variant = variant, enabled = false)
                        HeyButton("승인할게요", {}, variant = variant, loading = true)
                    }
                    HStack(HeyGap.G16) {
                        HeyButton("승인할게요", {}, variant = variant, size = HeyButtonSize.Large)
                        HeyButton("승인할게요", {}, variant = variant, size = HeyButtonSize.Large, enabled = false)
                        HeyButton("승인할게요", {}, variant = variant, size = HeyButtonSize.Large, loading = true)
                    }
                }
            })
        }
        add(part("button-long-label") {
            VStack(HeyGap.G16) {
                HeyButton("승객이 고른 내릴 곳을 확인하고 이 위치로 경로를 다시 시작할게요", {}, Modifier.width(520.dp), size = HeyButtonSize.Large)
                HeyButton(GalleryData.LONG_JA, {}, Modifier.width(520.dp), variant = HeyButtonVariant.Secondary)
            }
        })
        add(part("icon-button") {
            HStack(HeyGap.G16) {
                HeyIconButton(HeyIcons.Speaker, "다시 듣기", {})
                HeyIconButton(HeyIcons.Close, "닫기", {})
                HeyIconButton(HeyIcons.Refresh, "새로고침", {}, enabled = false)
            }
        })
        add(part("switch-rows") {
            VStack(HeyGap.G8) {
                HeySwitchRow("손 흔들기 요청 받기", true, {}, Modifier.fillMaxWidth())
                HeySwitchRow("창문 요청 받기", false, {}, Modifier.fillMaxWidth())
                HeySwitchRow("카드 결제 질문 받기", true, {}, Modifier.fillMaxWidth(), enabled = false)
                HeySwitchRow(GalleryData.LONG_KO, true, {}, Modifier.fillMaxWidth())
            }
        })
        add(part("stepper-rows") {
            VStack(HeyGap.G8) {
                HeyStepperRow("음성 간격", 8, 4..30, {}, Modifier.fillMaxWidth(), format = { "${it}초" })
                HeyStepperRow("음성 간격 (최솟값)", 4, 4..30, {}, Modifier.fillMaxWidth(), format = { "${it}초" })
                HeyStepperRow("음성 간격 (최댓값)", 30, 4..30, {}, Modifier.fillMaxWidth(), format = { "${it}초" })
            }
        })
        add(part("segmented") {
            VStack(HeyGap.G16) {
                HeySegmented(
                    listOf(HeySegmentOption("dark", "어둡게"), HeySegmentOption("light", "밝게")),
                    "dark", {}, Modifier.width(520.dp),
                )
                HeySegmented(
                    listOf(HeySegmentOption(1, "느리게"), HeySegmentOption(2, "보통"), HeySegmentOption(3, "빠르게"), HeySegmentOption(4, "아주 빠르게로 읽어 주세요")),
                    2, {}, Modifier.fillMaxWidth(),
                )
            }
        })

        // Surfaces
        add(part("cards") {
            HStack(HeyGap.G16, align = HeyAlign.Start) {
                HeyCardTone.entries.forEach { tone ->
                    HeyCard(Modifier.width(240.dp), tone = tone) {
                        HeyText(tone.name, variant = HeyTextVariant.Heading)
                        HeyText("카드 안 글자", tone = HeyTone.Muted)
                    }
                }
            }
        })
        add(part("status-pills") {
            VStack(HeyGap.G16) {
                HStack(HeyGap.G8) { HeyStatusTone.entries.forEach { HeyStatusPill(it.name, it) } }
                HStack(HeyGap.G8) {
                    HeyStatusPill("연결됨", HeyStatusTone.Success, icon = HeyIcons.Check)
                    HeyStatusPill("주의가 필요한 아주 긴 상태 문구가 들어가요 정말로 길어요", HeyStatusTone.Warning, Modifier.width(520.dp))
                }
            }
        })
        add(part("banners") {
            VStack(HeyGap.G16) {
                HeyStatusTone.entries.filter { it != HeyStatusTone.Neutral && it != HeyStatusTone.Accent }.forEach {
                    HeyBanner(it, "승객이 목적지를 바꿨어요", Modifier.fillMaxWidth(), message = "새 목적지로 경로를 다시 시작해 주세요.")
                }
                HeyBanner(HeyStatusTone.Danger, "제목만 있는 배너", Modifier.fillMaxWidth())
                HeyBanner(HeyStatusTone.Warning, "긴 글", Modifier.fillMaxWidth(), message = GalleryData.LONG_KO + GalleryData.LONG_KO)
            }
        })
        add(part("empty-panel") {
            HStack(HeyGap.G32, align = HeyAlign.Start) {
                EmptyPanel(HeyIcons.Car, "승객을 기다리고 있어요", Modifier.width(480.dp), message = "QR로 연결 요청이 오면 알려 드려요.")
                EmptyPanel(HeyIcons.Message, "아직 요청이 없어요", Modifier.width(480.dp))
            }
        })
        add(part("dialog") {
            androidx.compose.foundation.layout.Box(Modifier.width(PartWidth).background(HeyTheme.colors.bgPage)) {
                VStack(HeyGap.G16, modifier = Modifier.padding(32.dp).height(256.dp)) {
                    HeyText("뒤에 있는 화면")
                    HeyText("뒤에 있는 화면 글자", tone = HeyTone.Muted)
                }
                HeyDialog(
                    title = "운행을 끝낼까요?",
                    message = "끝내면 이 운행의 승객 연결이 모두 끊어져요.",
                    confirm = HeyDialogAction("운행 끝내기") {},
                    dismiss = HeyDialogAction("계속 운행") {},
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    danger = true,
                )
            }
        })
        add(part("dialog-long") {
            androidx.compose.foundation.layout.Box(Modifier.width(PartWidth).background(HeyTheme.colors.bgPage)) {
                HeyDialog(
                    title = "승객이 고른 내릴 곳으로 경로를 바꿀까요?",
                    message = GalleryData.LONG_KO,
                    confirm = HeyDialogAction("경로 바꾸기") {},
                    dismiss = HeyDialogAction("그대로 갈게요") {},
                    modifier = Modifier.fillMaxWidth().height(480.dp),
                )
            }
        })

        // Strip
        add(part("status-strip") {
            VStack(HeyGap.G24) {
                (1..4).forEach { StatusStrip(it, "정차 중", HeyConnection.Online, clock = "오후 11:42") }
                StatusStrip(2, "주행 중 · 음성으로 알려 드려요", HeyConnection.Reconnecting)
                StatusStrip(1, "정차 중", HeyConnection.Offline, clock = "오후 11:42")
            }
        })

        // QR + greeting
        add(part("qr") {
            HStack(HeyGap.G32, align = HeyAlign.Start) {
                HeyQrCode(GalleryData.QR_URL, 360.dp)
                HeyQrCode(GalleryData.QR_URL + "/very/long/path/to/make/a/denser/code?x=1234567890abcdef", 360.dp)
            }
        })
        add(part("greeting-strip") { LanguageGreetingStrip(GalleryData.greetings, Modifier.width(720.dp)) })
        add(part("greeting-strip-empty") { LanguageGreetingStrip(emptyList(), Modifier.width(720.dp)) })

        // Join request
        add(part("join-parked") { JoinRequestCard("English", 2, 252, {}, {}, Modifier.width(720.dp)) })
        add(part("join-driving") { JoinRequestCard("日本語", 4, 38, {}, {}, Modifier.width(720.dp), enabled = false) })
        add(part("join-long-lang") { JoinRequestCard("简体中文 Chinese (Simplified) 中文普通话 대륙 표준어", 1, 300, {}, {}, Modifier.width(720.dp)) })

        // Summary
        add(part("korean-summary-4") {
            KoreanSummary(GalleryData.DESTINATION_KO, GalleryData.ADDRESS_KO, GalleryData.NAME_LOCAL, conditions4, Modifier.width(900.dp))
        })
        add(part("korean-summary-0") {
            KoreanSummary(GalleryData.DESTINATION_KO, GalleryData.ADDRESS_KO, GalleryData.NAME_LOCAL, emptyList(), Modifier.width(900.dp))
        })
        add(part("korean-summary-long") {
            KoreanSummary(
                "서울국제금융센터 지하 3층 주차장 B구역 동쪽 엘리베이터 홀",
                GalleryData.LONG_ADDRESS_KO, GalleryData.LONG_JA, conditions4.take(1), Modifier.width(900.dp),
            )
        })
        add(part("condition-line") {
            VStack(HeyGap.G8) {
                HeyCardless(ConditionLine(HeyIcons.Luggage, "짐이 있어요"))
                HeyCardless(ConditionLine(HeyIcons.ThermometerWarm, "따뜻하게 해 주세요", changed = true))
                HeyCardless(ConditionLine(HeyIcons.Door, GalleryData.LONG_KO, changed = true))
            }
        })

        // Dropoff
        add(part("dropoff-candidates") {
            VStack(HeyGap.G8) {
                DropoffCandidateRow("3번 출구 앞", true, {}, Modifier.fillMaxWidth(), distanceText = "40m")
                DropoffCandidateRow("1층 택시 승강장", false, {}, Modifier.fillMaxWidth(), distanceText = "120m")
                DropoffCandidateRow("목적지 기본 위치", false, {}, Modifier.fillMaxWidth())
                DropoffCandidateRow(GalleryData.LONG_ADDRESS_KO + GalleryData.LONG_ADDRESS_KO, false, {}, Modifier.fillMaxWidth(), distanceText = "1.2km")
            }
        })
        add(part("dropoff-candidates-one") { DropoffCandidateRow("목적지 기본 위치", true, {}, Modifier.fillMaxWidth()) })
        add(part("reason-chips") { ReasonChips(GalleryData.reasons, 1, {}, Modifier.fillMaxWidth()) })
        add(part("reason-chips-none-selected") { ReasonChips(GalleryData.reasons, null, {}, Modifier.fillMaxWidth()) })
        add(part("proposal-status") {
            VStack(HeyGap.G8) { ProposalState.entries.forEach { ProposalStatus(it) } }
        })

        // Requests
        add(part("request-rows") {
            VStack(HeyGap.G8) {
                RequestRow("천천히 가 주세요", RequestKind.Notice, RequestState.Spoken, "오후 11:40", {}, Modifier.fillMaxWidth())
                RequestRow("창문을 열어도 될까요?", RequestKind.Question, RequestState.Received, "오후 11:41", {}, Modifier.fillMaxWidth())
                RequestRow("카드 결제가 되나요?", RequestKind.Question, RequestState.Answered, "오후 11:42", {}, Modifier.fillMaxWidth())
                RequestRow(GalleryData.LONG_KO + GalleryData.LONG_KO, RequestKind.Free, RequestState.Spoken, "오후 11:43", {}, Modifier.fillMaxWidth())
            }
        })
        add(part("quick-replies") {
            QuickReplyGrid(
                listOf("네", "잠시만요", "5분 걸려요", "여기서 세울게요", "카드 돼요", "어려워요").map { QuickReplyUi(it, it) },
                {}, Modifier.fillMaxWidth(),
            )
        })
        add(part("quick-replies-disabled") {
            QuickReplyGrid(
                listOf("네", "잠시만요", "5분 걸려요", "여기서 세울게요", "카드 돼요", "어려워요").map { QuickReplyUi(it, it) },
                {}, Modifier.fillMaxWidth(), enabled = false,
            )
        })
        add(part("quick-replies-two") {
            QuickReplyGrid(listOf(QuickReplyUi("a", "네"), QuickReplyUi("b", "어려워요")), {}, Modifier.fillMaxWidth())
        })
        add(part("passenger-pin") { PassengerPinCard("롯데호텔 서울 정문 건너편", "180m", {}, {}, Modifier.fillMaxWidth()) })
        add(part("passenger-pin-long") { PassengerPinCard(GalleryData.LONG_ADDRESS_KO, "1.2km", {}, {}, Modifier.fillMaxWidth()) })

        // Trip glance
        add(part("eta-block") {
            VStack(HeyGap.G24) {
                EtaBlock("23분", "18.4km", stale = false)
                EtaBlock("23분", "18.4km", stale = true)
            }
        })
        add(part("driving-panel-empty") { DrivingPanel(GalleryData.DESTINATION_KO, "23분", 0, Modifier.fillMaxWidth()) })
        add(part("driving-panel-pending") {
            DrivingPanel(GalleryData.DESTINATION_KO, "23분", 2, Modifier.fillMaxWidth(), lastSpokenKo = "창문을 열어도 될까요?")
        })
        add(part("driving-panel-long") {
            DrivingPanel(GalleryData.LONG_ADDRESS_KO, "1시간 48분", 12, Modifier.fillMaxWidth(), lastSpokenKo = GalleryData.LONG_KO)
        })
        add(part("trip-summary") {
            TripSummaryCard(
                listOf("내릴 곳" to "3번 출구 앞", "이동 시간" to "42분", "이동 거리" to "38.2km", "통행료" to "9,400원"),
                Modifier.fillMaxWidth(),
            )
        })
        add(part("trip-summary-long") {
            TripSummaryCard(listOf("내릴 곳" to GalleryData.LONG_ADDRESS_KO, "통행료" to "없음"), Modifier.fillMaxWidth())
        })
        add(part("trip-summary-empty") { TripSummaryCard(emptyList(), Modifier.fillMaxWidth()) })
        add(part("section") {
            VStack(HeyGap.G24) {
                HeySection("받을 요청") {
                    HeySwitchRow("천천히 가 주세요", true, {}, Modifier.fillMaxWidth())
                    HeySwitchRow("창문 요청 받기", false, {}, Modifier.fillMaxWidth())
                }
                HeySection("음성") {
                    HeyStepperRow("음성 간격", 8, 4..30, {}, Modifier.fillMaxWidth(), format = { "${it}초" })
                }
                HeySection(GalleryData.LONG_KO) { HeyText("긴 제목 아래 묶음") }
                HeySection("비어 있는 묶음") {}
            }
        })
        add(part("notice") {
            VStack(HeyGap.G16) {
                HeyStatusTone.entries.filter { it != HeyStatusTone.Neutral && it != HeyStatusTone.Accent }.forEach {
                    HeyNotice("승객이 목적지를 바꿨어요", it, Modifier.fillMaxWidth())
                }
                HeyNotice(GalleryData.LONG_KO, HeyStatusTone.Warning, Modifier.fillMaxWidth())
            }
        })
        add(part("scroll-column-short") {
            HeyScrollColumn(HeyGap.G8, Modifier.fillMaxWidth().height(240.dp)) {
                DropoffCandidateRow("3번 출구 앞", true, {}, Modifier.fillMaxWidth(), distanceText = "40m")
            }
        })
    }

    val screens: List<Story> = buildList {
        listOf(StoryFrame.Screen1600, StoryFrame.Screen1280).forEach { frame ->
            val tag = if (frame == StoryFrame.Screen1600) "1600" else "1280"
            add(screen("d1-waiting-$tag", frame) { WaitingScreen(party = 1, connection = HeyConnection.Online) })
            add(screen("d1-waiting-offline-$tag", frame) { WaitingScreen(party = 1, connection = HeyConnection.Offline) })
            add(screen("d2-join-parked-$tag", frame) { JoinScreen(driving = false) })
            add(screen("d2-join-driving-$tag", frame) { JoinScreen(driving = true) })
            add(screen("d3-card-$tag", frame) { CardScreen(conditions4) })
            add(screen("d3-card-long-$tag", frame) { CardScreen(conditions4, long = true) })
            add(screen("d5-driving-$tag", frame) { DrivingScreen(pending = 2) })
            add(screen("d5-driving-none-$tag", frame) { DrivingScreen(pending = 0) })
            add(screen("d6-requests-$tag", frame) { RequestsScreen(many = false) })
            add(screen("d6-requests-many-$tag", frame) { RequestsScreen(many = true) })
            add(screen("d4-dropoff-$tag", frame) { DropoffScreen() })
            add(screen("d7-arrived-$tag", frame) { ArrivedScreen() })
            add(screen("empty-$tag", frame) { EmptyScreen() })
        }
    }

    val all: List<Story> get() = parts + screens
}

@Composable
private fun HeyCardless(line: ConditionLine) {
    ai.hey.designsystem.component.ConditionLineRow(line, Modifier.fillMaxWidth())
}

// ---------------------------------------------------------------- screen-level compositions

@Composable
private fun WaitingScreen(party: Int, connection: HeyConnection) {
    CarFrame(statusStrip = stripFor(party, connection), driving = false, actionRail = {
        HStack(HeyGap.G16, arrange = HeyArrange.End, modifier = Modifier.fillMaxWidth()) {
            HeyButton("설정", {}, variant = HeyButtonVariant.Secondary, icon = HeyIcons.Menu)
        }
    }) {
        HStack(HeyGap.G32, align = HeyAlign.Center, modifier = Modifier.fillMaxSize()) {
            HeyQrCode(GalleryData.QR_URL, 400.dp)
            VStack(HeyGap.G24, modifier = Modifier.grow()) {
                VStack(HeyGap.G8) {
                    HeyText("승객이 QR을 찍으면 연결돼요", variant = HeyTextVariant.Hero, maxLines = 2)
                    HeyText("조수석 뒤 QR을 안내해 주세요", variant = HeyTextVariant.Heading, tone = HeyTone.Muted, maxLines = 1)
                }
                LanguageGreetingStrip(GalleryData.greetings, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun JoinScreen(driving: Boolean) {
    CarFrame(statusStrip = stripFor(2, HeyConnection.Online, driving), driving = false) {
        VStack(HeyGap.G24, arrange = HeyArrange.Center, modifier = Modifier.fillMaxSize()) {
            JoinRequestCard("English", 2, 252, {}, {}, Modifier.fillMaxWidth(), enabled = !driving)
        }
    }
}

@Composable
private fun CardScreen(conditions: List<ConditionLine>, long: Boolean = false) {
    CarFrame(
        statusStrip = stripFor(2, HeyConnection.Online),
        driving = false,
        actionRail = {
            HStack(HeyGap.G16, modifier = Modifier.fillMaxWidth()) {
                HeyButton("다시 확인 요청", {}, variant = HeyButtonVariant.Secondary, size = HeyButtonSize.Large, icon = HeyIcons.Refresh)
                HeyButton("내릴 곳 제안", {}, variant = HeyButtonVariant.Secondary, size = HeyButtonSize.Large, icon = HeyIcons.Flag)
                HeyButton("다시 듣기", {}, variant = HeyButtonVariant.Ghost, size = HeyButtonSize.Large, icon = HeyIcons.Speaker)
                HeyButton("경로 시작", {}, Modifier.grow(), variant = HeyButtonVariant.Primary, size = HeyButtonSize.Large, icon = HeyIcons.Route)
            }
        },
    ) {
        HeyScrollColumn(HeyGap.G16, modifier = Modifier.fillMaxSize()) {
            if (long) {
                KoreanSummary("서울국제금융센터 지하 3층 주차장 B구역 동쪽 엘리베이터 홀", GalleryData.LONG_ADDRESS_KO, GalleryData.LONG_JA, conditions)
            } else {
                KoreanSummary(GalleryData.DESTINATION_KO, GalleryData.ADDRESS_KO, GalleryData.NAME_LOCAL, conditions)
            }
        }
    }
}

@Composable
private fun DrivingScreen(pending: Int) {
    CarFrame(statusStrip = stripFor(2, HeyConnection.Online, driving = true), driving = true, actionRail = {
        HeyButton("이 버튼은 주행 중 그려지지 않아요", {})
    }) {
        VStack(HeyGap.G24, arrange = HeyArrange.Center, modifier = Modifier.fillMaxSize()) {
            DrivingPanel(
                GalleryData.DESTINATION_KO,
                "23분",
                pending,
                Modifier.fillMaxWidth(),
                lastSpokenKo = if (pending > 0) "창문을 열어도 될까요?" else null,
            )
        }
    }
}

@Composable
private fun RequestsScreen(many: Boolean) {
    CarFrame(statusStrip = stripFor(2, HeyConnection.Online), driving = false, actionRail = {
        QuickReplyGrid(
            listOf("네", "잠시만요", "5분 걸려요", "여기서 세울게요", "카드 돼요", "어려워요").map { QuickReplyUi(it, it) },
            {}, Modifier.fillMaxWidth(),
        )
    }) {
        HeyScrollColumn(HeyGap.G16, modifier = Modifier.fillMaxSize()) {
            RequestRow("창문을 열어도 될까요?", RequestKind.Question, RequestState.Spoken, "오후 11:41", {}, Modifier.fillMaxWidth())
            RequestRow("천천히 가 주세요", RequestKind.Notice, RequestState.Spoken, "오후 11:40", {}, Modifier.fillMaxWidth())
            if (many) {
                RequestRow(GalleryData.LONG_KO, RequestKind.Free, RequestState.Received, "오후 11:39", {}, Modifier.fillMaxWidth())
                RequestRow("카드 결제가 되나요?", RequestKind.Question, RequestState.Answered, "오후 11:38", {}, Modifier.fillMaxWidth())
                RequestRow("에어컨을 조금 약하게 해 주세요", RequestKind.Notice, RequestState.Spoken, "오후 11:37", {}, Modifier.fillMaxWidth())
            } else {
                PassengerPinCard("롯데호텔 서울 정문 건너편", "180m", {}, {}, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DropoffScreen() {
    CarFrame(
        statusStrip = stripFor(1, HeyConnection.Online),
        driving = false,
        actionRail = {
            HStack(HeyGap.G16, modifier = Modifier.fillMaxWidth()) {
                ProposalStatus(ProposalState.Pending)
                
                HeyButton("이 위치로 보내기", {}, Modifier.grow(), size = HeyButtonSize.Large, icon = HeyIcons.Send)
            }
        },
    ) {
        HStack(HeyGap.G32, align = HeyAlign.Start, modifier = Modifier.fillMaxSize()) {
            HeyScrollColumn(HeyGap.G8, modifier = Modifier.grow()) {
                HeyText("내릴 곳 고르기", variant = HeyTextVariant.Title)
                DropoffCandidateRow("3번 출구 앞", true, {}, Modifier.fillMaxWidth(), distanceText = "40m")
                DropoffCandidateRow("1층 택시 승강장", false, {}, Modifier.fillMaxWidth(), distanceText = "120m")
                DropoffCandidateRow("목적지 기본 위치", false, {}, Modifier.fillMaxWidth())
            }
            VStack(HeyGap.G16, modifier = Modifier.grow()) {
                HeyText("이유", variant = HeyTextVariant.Title)
                ReasonChips(GalleryData.reasons, 1, {}, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ArrivedScreen() {
    CarFrame(
        statusStrip = stripFor(2, HeyConnection.Online),
        driving = false,
        actionRail = {
            HStack(HeyGap.G16, arrange = HeyArrange.End, modifier = Modifier.fillMaxWidth()) {
                HeyButton("다음 승객 받기", {}, variant = HeyButtonVariant.Secondary, size = HeyButtonSize.Large)
                HeyButton("운행 끝내기", {}, size = HeyButtonSize.Large, icon = HeyIcons.Check)
            }
        },
    ) {
        VStack(HeyGap.G24, modifier = Modifier.fillMaxSize()) {
            HeyBanner(HeyStatusTone.Success, "도착했어요", Modifier.fillMaxWidth(), message = "승객이 내리면 운행 끝내기를 눌러 주세요.")
            TripSummaryCard(
                listOf("내릴 곳" to "3번 출구 앞", "이동 시간" to "42분", "이동 거리" to "38.2km", "통행료" to "9,400원"),
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyScreen() {
    CarFrame(statusStrip = stripFor(1, HeyConnection.Online), driving = false) {
        EmptyPanel(HeyIcons.Car, "승객을 기다리고 있어요", Modifier.fillMaxSize(), message = "연결 요청이 오면 바로 알려 드려요.")
    }
}
