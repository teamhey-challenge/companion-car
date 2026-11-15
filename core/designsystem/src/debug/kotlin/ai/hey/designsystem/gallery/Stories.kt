package ai.hey.designsystem.gallery

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.CharacterSize
import ai.hey.designsystem.component.CharacterSlot
import ai.hey.designsystem.component.CheckRow
import ai.hey.designsystem.component.EmptyPanel
import ai.hey.designsystem.component.HeaderBar
import ai.hey.designsystem.component.HeyBanner
import ai.hey.designsystem.component.HeyBannerAction
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
import ai.hey.designsystem.component.HeyLayered
import ai.hey.designsystem.component.HeyNotice
import ai.hey.designsystem.component.HeyQrCode
import ai.hey.designsystem.component.HeyScrollColumn
import ai.hey.designsystem.component.HeySection
import ai.hey.designsystem.component.HeySegmentOption
import ai.hey.designsystem.component.HeySegmented
import ai.hey.designsystem.component.HeyStatusPill
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.HeyStepperRow
import ai.hey.designsystem.component.HeySwitchRow
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.HeyTextField
import ai.hey.designsystem.component.OceanBackground
import ai.hey.designsystem.component.PageHeading
import ai.hey.designsystem.component.StatusStrip
import ai.hey.designsystem.component.TalkButton
import ai.hey.designsystem.component.TalkPhase
import ai.hey.designsystem.component.ChoiceRow
import ai.hey.designsystem.component.ConfirmDialog
import ai.hey.designsystem.component.ErrorKind
import ai.hey.designsystem.component.ErrorPanel
import ai.hey.designsystem.component.MemoryRow
import ai.hey.designsystem.component.QrPanel
import ai.hey.designsystem.component.SkeletonRow
import ai.hey.designsystem.component.StatusBadge
import ai.hey.designsystem.component.Subtitle
import ai.hey.designsystem.component.ToggleRow
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
import androidx.compose.foundation.layout.Box
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
    Box(Modifier.width(PartWidth).background(HeyTheme.colors.bgPage).padding(32.dp)) { content() }
}

private fun part(id: String, content: @Composable () -> Unit) = Story(id, StoryFrame.Part) { Plate(content) }

private fun screen(id: String, frame: StoryFrame, content: @Composable () -> Unit) = Story(id, frame, content)

/** Long Korean text with no spaces: the design system's worst case, shown at 2x the normal column width. */
private const val NO_SPACE_KO = "오늘퇴근길에는비가와서조용한음악을틀어드리고내일아침일정도함께알려드릴게요오늘퇴근길에는비가와서조용한음악을틀어드리고내일아침일정도함께알려드릴게요"

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
                        HeyButton("다시 연결", {}, variant = variant)
                        HeyButton("기억", {}, variant = variant, icon = HeyIcons.Book)
                        HeyButton("다시 연결", {}, variant = variant, enabled = false)
                        HeyButton("다시 연결", {}, variant = variant, loading = true)
                    }
                    HStack(HeyGap.G16) {
                        HeyButton("다시 연결", {}, variant = variant, size = HeyButtonSize.Large)
                        HeyButton("다시 연결", {}, variant = variant, size = HeyButtonSize.Large, enabled = false)
                        HeyButton("다시 연결", {}, variant = variant, size = HeyButtonSize.Large, loading = true)
                    }
                }
            })
        }
        add(part("button-long-label") {
            VStack(HeyGap.G16) {
                HeyButton(NO_SPACE_KO, {}, Modifier.width(520.dp), size = HeyButtonSize.Large)
            }
        })
        add(part("icon-button") {
            HStack(HeyGap.G16) {
                HeyIconButton(HeyIcons.Trash, "지우기", {})
                HeyIconButton(HeyIcons.Close, "닫기", {})
                HeyIconButton(HeyIcons.Refresh, "새로고침", {}, enabled = false)
            }
        })
        add(part("switch-rows") {
            VStack(HeyGap.G8) {
                HeySwitchRow("먼저 말 걸기", true, {}, Modifier.fillMaxWidth())
                HeySwitchRow("기억 저장", false, {}, Modifier.fillMaxWidth())
                HeySwitchRow("꺼진 항목", true, {}, Modifier.fillMaxWidth(), enabled = false)
            }
        })
        add(part("stepper-rows") {
            VStack(HeyGap.G8) {
                HeyStepperRow("음성 간격", 8, 4..30, {}, Modifier.fillMaxWidth(), format = { "${it}초" })
            }
        })
        add(part("segmented") {
            VStack(HeyGap.G16) {
                HeySegmented(
                    listOf(HeySegmentOption("dark", "어둡게"), HeySegmentOption("light", "밝게")),
                    "dark", {}, Modifier.width(520.dp),
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
                HeyStatusPill("연결됨", HeyStatusTone.Success, icon = HeyIcons.Check)
            }
        })
        add(part("banners") {
            VStack(HeyGap.G16) {
                HeyStatusTone.entries.filter { it != HeyStatusTone.Neutral && it != HeyStatusTone.Accent }.forEach {
                    HeyBanner(it, "연결이 잠시 끊겼어요", Modifier.fillMaxWidth(), message = "연결을 확인한 뒤 다시 이야기해요.")
                }
                HeyBanner(
                    HeyStatusTone.Danger, "지금은 답하기 어려워요", Modifier.width(560.dp),
                    message = "잠시 뒤에 다시 시도해 주세요.",
                    actions = listOf(HeyBannerAction("다시 시도", {}, primary = true), HeyBannerAction("닫기", {})),
                )
            }
        })
        add(part("empty-panel") {
            EmptyPanel(HeyIcons.Book, "저장된 기억이 없어요", Modifier.width(480.dp), message = "대화하면서 기억이 쌓여요.")
        })
        add(part("dialog") {
            Box(Modifier.width(PartWidth).background(HeyTheme.colors.bgPage)) {
                HeyDialog(
                    title = "이 기억을 지울까요?",
                    message = "지운 기억은 다시 불러올 수 없어요.",
                    confirm = HeyDialogAction("지우기") {},
                    dismiss = HeyDialogAction("그대로 둘게요") {},
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    danger = true,
                )
            }
        })
        add(part("status-strip") {
            VStack(HeyGap.G24) {
                StatusStrip("정차 중", HeyConnection.Online, clock = "오후 11:42")
                StatusStrip("주행 중 · 음성으로 알려 드려요", HeyConnection.Reconnecting)
                StatusStrip(null, HeyConnection.Offline, clock = "오후 11:42")
            }
        })
        add(part("qr") { HeyQrCode(GalleryData.QR_URL, 320.dp) })
        add(part("section") {
            VStack(HeyGap.G24) {
                HeySection("먼저 말 걸기") {
                    HeySwitchRow("켜기", true, {}, Modifier.fillMaxWidth())
                    HeySwitchRow("꺼진 항목", false, {}, Modifier.fillMaxWidth())
                }
            }
        })
        add(part("notice") {
            VStack(HeyGap.G16) {
                HeyStatusTone.entries.filter { it != HeyStatusTone.Neutral && it != HeyStatusTone.Accent }.forEach {
                    HeyNotice("지우지 못했어요. 기억은 그대로 남아 있어요.", it, Modifier.fillMaxWidth())
                }
            }
        })
        add(part("text-field") {
            HeyTextField("서버 주소", "https://hey.lastmile.ltd", {}, Modifier.fillMaxWidth())
        })
        add(part("layered-dialog") {
            HeyLayered(
                Modifier.width(PartWidth).height(320.dp),
                overlay = {
                    HeyDialog("동의를 철회할까요?", "기억과 대화 요약을 모두 지워요.", HeyDialogAction("철회") {}, HeyDialogAction("취소") {}, Modifier.fillMaxSize())
                },
                notice = { HeyNotice("저장했어요", HeyStatusTone.Success) },
            ) { VStack(HeyGap.G16, modifier = Modifier.padding(32.dp)) { HeyText("뒤에 있는 화면"); HeyText("뒤에 있는 화면 글자", tone = HeyTone.Muted) } }
        })

        // ---- v0.5 companion components (PR1)
        add(part("talk-button-states") {
            VStack(HeyGap.G16) {
                HStack(HeyGap.G16) {
                    TalkButton(TalkPhase.Idle, {})
                    TalkButton(TalkPhase.Listening, {})
                    TalkButton(TalkPhase.Thinking, {})
                }
                HStack(HeyGap.G16) {
                    TalkButton(TalkPhase.Speaking, {})
                    TalkButton(TalkPhase.Idle, {}, enabled = false)
                    TalkButton(TalkPhase.Listening, {}, label = null)
                }
                HStack(HeyGap.G16) {
                    TalkButton(TalkPhase.Idle, {}, driving = true)
                    TalkButton(TalkPhase.Listening, {}, driving = true)
                }
            }
        })
        add(part("talk-button-big") {
            VStack(HeyGap.G16) {
                TalkButton(TalkPhase.Idle, {}, driving = true)
                TalkButton(TalkPhase.Listening, {}, driving = true)
            }
        })
        add(part("talk-button-long") {
            TalkButton(TalkPhase.Idle, {}, Modifier.width(520.dp), label = NO_SPACE_KO)
        })
        add(part("page-heading") {
            VStack(HeyGap.G24) {
                PageHeading("기억", subtitle = "남기고 싶은 것만 남겨도 괜찮아요.")
                PageHeading("오늘은 어떤 하루였어요?", hero = true)
                PageHeading(NO_SPACE_KO, Modifier.width(600.dp), subtitle = NO_SPACE_KO)
            }
        })
        add(part("toggle-rows") {
            VStack(HeyGap.G8) {
                ToggleRow("먼저 말 걸기", true, {}, Modifier.fillMaxWidth(), description = "운전 중에는 말을 걸지 않아요.")
                ToggleRow("기억 저장", false, {}, Modifier.fillMaxWidth())
                ToggleRow("먼저 말 걸기", true, {}, Modifier.fillMaxWidth(), enabled = false)
                ToggleRow("기억 저장", true, {}, Modifier.fillMaxWidth(), saving = true)
                ToggleRow(NO_SPACE_KO, true, {}, Modifier.width(600.dp), description = NO_SPACE_KO)
            }
        })
        add(part("check-rows") {
            VStack(HeyGap.G8) {
                CheckRow("음성 대화 처리에 동의해요", true, {}, Modifier.fillMaxWidth())
                CheckRow("기억을 저장해도 좋아요", false, {}, Modifier.fillMaxWidth())
                CheckRow("꺼진 항목", false, {}, Modifier.fillMaxWidth(), enabled = false)
                CheckRow("음성 대화 처리에 동의해요", false, {}, Modifier.fillMaxWidth(), error = "필수 동의예요")
                CheckRow(NO_SPACE_KO, true, {}, Modifier.width(600.dp))
            }
        })
        add(part("header-bar") {
            VStack(HeyGap.G24) {
                HeaderBar("헤이리", stateLabel = "정차 중", trailing = {
                    HeyButton("기억", {}, variant = HeyButtonVariant.Soft, icon = HeyIcons.Book)
                    HeyButton("설정", {}, variant = HeyButtonVariant.Soft, icon = HeyIcons.Gear)
                })
                HeaderBar("헤이리", onBack = {}, backLabel = "뒤로", stateLabel = "정차 중")
            }
        })
        // ---- PR2 components
        add(part("choice-rows") {
            VStack(HeyGap.G24) {
                ChoiceRow(
                    listOf(HeySegmentOption(20, "적게"), HeySegmentOption(10, "보통"), HeySegmentOption(5, "자주"), HeySegmentOption(0, "끄기")),
                    10, {}, Modifier.fillMaxWidth(), label = "먼저 말 걸기",
                )
                ChoiceRow(listOf(HeySegmentOption(1, "반말"), HeySegmentOption(2, "존댓말")), 2, {}, Modifier.fillMaxWidth())
                ChoiceRow(listOf(HeySegmentOption(1, "반말"), HeySegmentOption(2, "존댓말")), 1, {}, Modifier.fillMaxWidth(), enabled = false)
                ChoiceRow(listOf(HeySegmentOption(1, NO_SPACE_KO), HeySegmentOption(2, "존댓말"), HeySegmentOption(3, "반말")), 1, {}, Modifier.width(600.dp))
            }
        })
        add(part("memory-rows") {
            VStack(HeyGap.G16) {
                MemoryRow("좋아하는 것", "비 오는 날, 잔잔한 음악을 좋아해요.", {}, "이 기억 지우기", Modifier.fillMaxWidth(), date = "11월 13일", tint = 0)
                MemoryRow("일상", "퇴근길에는 조용한 대화를 좋아해요.", {}, "이 기억 지우기", Modifier.fillMaxWidth(), date = "11월 12일", tint = 1)
                MemoryRow("다가오는 일", "금요일에 중요한 발표가 있어요.", {}, "이 기억 지우기", Modifier.fillMaxWidth(), date = "11월 11일", tint = 2)
                MemoryRow("일상", NO_SPACE_KO, {}, "이 기억 지우기", Modifier.width(600.dp), enabled = false)
            }
        })
        add(part("skeleton-rows") {
            VStack(HeyGap.G16) { SkeletonRow(); SkeletonRow(); SkeletonRow() }
        })
        add(part("confirm-dialog") {
            Box(Modifier.width(PartWidth).background(HeyTheme.colors.bgPage)) {
                ConfirmDialog("이 기억을 지울까요?", "지운 기억은 다시 불러올 수 없어요.", "지우기", "취소", {}, {}, Modifier.fillMaxWidth().height(420.dp), danger = true)
            }
        })
        add(part("confirm-dialog-long") {
            Box(Modifier.width(PartWidth).background(HeyTheme.colors.bgPage)) {
                ConfirmDialog(NO_SPACE_KO, NO_SPACE_KO, "지우기", "취소", {}, {}, Modifier.width(600.dp).height(640.dp), busy = true)
            }
        })
        add(part("status-badge") {
            VStack(HeyGap.G16) {
                StatusBadge(TalkPhase.Idle, animated = false)
                StatusBadge(TalkPhase.Listening, animated = false)
                StatusBadge(TalkPhase.Thinking)
                StatusBadge(TalkPhase.Speaking, animated = false)
                StatusBadge(TalkPhase.Thinking, driving = true)
                StatusBadge(TalkPhase.Idle, connection = HeyConnection.Reconnecting)
                StatusBadge(TalkPhase.Idle, connection = HeyConnection.Offline)
            }
        })
        ErrorKind.entries.forEach { kind ->
            add(part("error-panel-${kind.name.lowercase()}") { ErrorPanel(kind, {}, Modifier.width(600.dp)) })
        }
        add(part("error-panel-long") {
            ErrorPanel(ErrorKind.Server, {}, Modifier.width(600.dp), title = NO_SPACE_KO, message = NO_SPACE_KO, actionLabel = NO_SPACE_KO)
        })
        add(part("error-panel-no-action") { ErrorPanel(ErrorKind.Offline, null, Modifier.width(600.dp)) })
        add(part("subtitle") {
            VStack(HeyGap.G16) {
                Subtitle("어제 준비하던 발표는 잘 마쳤어요?")
                Subtitle(GalleryData.LONG_KO + GalleryData.LONG_KO, Modifier.width(600.dp))
                Subtitle(NO_SPACE_KO, Modifier.width(600.dp))
                Subtitle("주행 중에는 그려지지 않아요", driving = true)
            }
        })
        add(part("qr-panel") {
            HStack(HeyGap.G32, align = HeyAlign.Start) {
                QrPanel(GalleryData.QR_URL, "K7M3Q9XD", "폰에서 QR을 찍어 주세요")
                QrPanel(GalleryData.QR_URL, NO_SPACE_KO, NO_SPACE_KO, Modifier.width(400.dp), qrSize = 240.dp)
            }
        })
        CharacterSize.entries.forEach { size ->
            add(Story("character-${size.name.lowercase()}", StoryFrame.Part) {
                Plate { CharacterSlot(size) }
            })
        }
        addAll(onboardingParts())
    }

    val screens: List<Story> = buildList {
        listOf(StoryFrame.Screen1600, StoryFrame.Screen1280).forEach { frame ->
            val tag = if (frame == StoryFrame.Screen1600) "1600" else "1280"
            add(screen("c3-home-$tag", frame) { HomeSheet(TalkPhase.Idle) })
            add(screen("c5-driving-$tag", frame) { DrivingSheet() })
        }
    }

    val all: List<Story> get() = parts + screens
}

/** C3 composed from the parts only: header, greeting, character, talk button over the ocean background. */
@Composable
private fun HomeSheet(phase: TalkPhase) {
    OceanBackground(Modifier.fillMaxSize(), animated = false) {
        VStack(HeyGap.G24, modifier = Modifier.fillMaxSize().padding(horizontal = 80.dp, vertical = 40.dp)) {
            HeaderBar("헤이리", stateLabel = "정차 중", trailing = {
                HeyButton("기억", {}, variant = HeyButtonVariant.Soft, icon = HeyIcons.Book)
                HeyButton("설정", {}, variant = HeyButtonVariant.Soft, icon = HeyIcons.Gear)
            })
            HStack(HeyGap.G32, modifier = Modifier.fillMaxWidth().grow(), arrange = HeyArrange.SpaceBetween) {
                VStack(HeyGap.G24, modifier = Modifier.fillMaxSize().grow(), arrange = HeyArrange.SpaceBetween) {
                    PageHeading("오늘은 어떤 하루였어요?", hero = true)
                    TalkButton(phase, {})
                }
                CharacterSlot(ai.hey.designsystem.component.CharacterSize.Stage)
            }
        }
    }
}

/** C5: still character and the big talk button on the plain page background (no bubbles). */
@Composable
private fun DrivingSheet() {
    CarFrame(statusStrip = {}, driving = true) {
        VStack(HeyGap.G32, modifier = Modifier.fillMaxSize(), align = HeyAlign.Center, arrange = HeyArrange.Center) {
            CharacterSlot(ai.hey.designsystem.component.CharacterSize.Driving)
            TalkButton(TalkPhase.Idle, {}, driving = true)
        }
    }
}
