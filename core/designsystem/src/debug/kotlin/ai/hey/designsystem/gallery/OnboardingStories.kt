package ai.hey.designsystem.gallery

import ai.hey.designsystem.component.OnbAlign
import ai.hey.designsystem.component.OnbCaption
import ai.hey.designsystem.component.OnbDressChoice
import ai.hey.designsystem.component.OnbDressGrid
import ai.hey.designsystem.component.OnbHoldRing
import ai.hey.designsystem.component.OnbLights
import ai.hey.designsystem.component.OnbListenPill
import ai.hey.designsystem.component.OnbMeetTitle
import ai.hey.designsystem.component.OnbPanelText
import ai.hey.designsystem.component.OnbPanelTitle
import ai.hey.designsystem.component.OnbParkedBanner
import ai.hey.designsystem.component.OnbPolaroid
import ai.hey.designsystem.component.OnbPolaroids
import ai.hey.designsystem.component.OnbProgress
import ai.hey.designsystem.component.OnbScene
import ai.hey.designsystem.component.OnbSceneScope
import ai.hey.designsystem.component.OnbShot
import ai.hey.designsystem.component.OnbSide
import ai.hey.designsystem.component.OnbSidePanel
import ai.hey.designsystem.component.OnbSwatch
import ai.hey.designsystem.component.OnbTitle
import ai.hey.designsystem.component.QrPanel
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyGap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val LONG_LINE = "안녕, 나는 빛바다를 지키는 고래야. 여기 헤이리들은 길을 함께 달릴 사람 단짝을 찾고 있어. 너랑 꼭 맞는 헤이리를 찾아 줄게."
private const val NO_SPACE = "오늘퇴근길에는비가와서조용한음악을틀어드리고내일아침일정도함께알려드릴게요오늘퇴근길에는비가와서조용한음악을틀어드리고"

/** A 1200 x 675 dp window onto the onboarding parts, so each one is judged on the scene it sits on. */
private fun scenePart(id: String, shot: OnbShot, content: @Composable OnbSceneScope.() -> Unit) =
    Story(id, StoryFrame.Part) {
        OnbScene(shot, Modifier.width(1200.dp).height(675.dp), content = content)
    }

/** The onboarding scene parts: scene stage, lights, caption layer, hold ring, polaroids, side panels, banner. */
internal fun onboardingParts(): List<Story> = buildList {
    add(scenePart("onb-scene-title", OnbShot.Title) {
        OnbTitle("Hey", "길 위의 단짝, 헤이리를 만나러 가요", Modifier.place(OnbAlign.TopCenter).padding(top = 90.dp))
    })
    add(scenePart("onb-scene-whale-lights", OnbShot.Whale) {
        OnbLights(40, Modifier.fillMaxSize())
        OnbProgress("헤이리 찾는 중", 0.5f, Modifier.place(OnbAlign.TopCenter).padding(top = 32.dp))
    })
    add(scenePart("onb-caption", OnbShot.Whale) {
        OnbCaption(LONG_LINE, Modifier.place(OnbAlign.BottomCenter))
    })
    add(scenePart("onb-caption-listening", OnbShot.Whale) {
        OnbCaption(
            "차 안에서는 어떤 얘기가 좋아?", Modifier.place(OnbAlign.BottomCenter),
            heard = "요즘 캠핑 얘기가 좋아.", listening = true, listeningLabel = "듣고 있어요", skipLabel = "넘어갈래", onSkip = {},
        )
    })
    add(scenePart("onb-caption-long", OnbShot.Whale) {
        OnbCaption(NO_SPACE + NO_SPACE, Modifier.place(OnbAlign.BottomCenter), heard = NO_SPACE)
    })
    add(Story("onb-listen-pill", StoryFrame.Part) {
        VStack(HeyGap.G16, modifier = Modifier.width(900.dp).padding(32.dp)) {
            OnbListenPill("듣고 있어요", "넘어갈래", {})
            OnbListenPill("듣고 있어요", "", null)
        }
    })
    add(scenePart("onb-hold-ring-idle", OnbShot.Ring) {
        OnbHoldRing("길게 눌러 헤이리 만나기", 1800, {}, Modifier.place(OnbAlign.Center).width(420.dp).height(420.dp), previewProgress = 0f)
        OnbCaption("길게 눌러 헤이리 만나기", Modifier.place(OnbAlign.BottomCenter))
    })
    add(scenePart("onb-hold-ring-held", OnbShot.Ring) {
        OnbHoldRing("길게 눌러 헤이리 만나기", 1800, {}, Modifier.place(OnbAlign.Center).width(420.dp).height(420.dp), previewProgress = 0.7f)
    })
    add(scenePart("onb-meet-title", OnbShot.Sea) {
        OnbMeetTitle("헤이리", Modifier.place(OnbAlign.TopCenter).padding(top = 24.dp))
    })
    add(scenePart("onb-polaroids", OnbShot.Sea) {
        OnbPolaroids(
            "너를 이만큼 알아",
            listOf(OnbPolaroid(0, "지우라고 불러 줘"), OnbPolaroid(1, "출퇴근길엔 같이"), OnbPolaroid(2, "캠핑 얘기 많이 하자")),
            settled = true,
        )
    })
    add(scenePart("onb-polaroids-one", OnbShot.Sea) {
        OnbPolaroids("너를 이만큼 알아", listOf(OnbPolaroid(1, NO_SPACE)), settled = true)
    })
    add(scenePart("onb-panel-dress", OnbShot.Sea) {
        OnbSidePanel(OnbSide.End, compact = false) {
            OnbPanelTitle("헤이리 꾸미기")
            Box(Modifier.height(24.dp))
            OnbDressGrid(
                listOf(
                    OnbDressChoice("라이트 아쿠아", OnbSwatch.Aqua, true) {},
                    OnbDressChoice("살구 오렌지", OnbSwatch.Peach, false) {},
                    OnbDressChoice("비니", OnbSwatch.Beanie, true) {},
                    OnbDressChoice("버킷", OnbSwatch.Bucket, false) {},
                    OnbDressChoice("스카프", OnbSwatch.Scarf, false) {},
                ),
            )
        }
    })
    add(scenePart("onb-panel-qr", OnbShot.Sea) {
        OnbSidePanel(OnbSide.End, compact = false) {
            OnbPanelTitle("폰에서도 함께")
            OnbPanelText("찍으면 기억을 폰에서 보고 지울 수 있어요")
            Box(Modifier.height(24.dp))
            QrPanel(GalleryData.QR_URL, "K7M3Q9XD", "폰에서 QR을 찍어 주세요", qrSize = 240.dp)
        }
    })
    add(scenePart("onb-panel-compact", OnbShot.Sea) {
        OnbSidePanel(OnbSide.Start, compact = true) {
            OnbPanelTitle("약속하고 시작할까?", compact = true)
            OnbPanelText("목소리는 차 안에서 글자로만 바꾸고 보내거나 저장하지 않아요")
        }
    })
    add(scenePart("onb-parked-banner", OnbShot.Sea) {
        OnbParkedBanner("누르는 건 정차하면 이어서 할게", Modifier.place(OnbAlign.TopEnd).padding(24.dp))
    })
}
