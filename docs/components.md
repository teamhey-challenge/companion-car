# HEY 부품·화면 목록 (2026-11-12 초판, 임시 디자인 시스템)

`docs/architecture.md` 4절 규칙을 따른다. 이 문서는 **기사 차량 화면(D)과 차량 부품(`:core:designsystem`)** 만 소유한다. 승객 웹 화면(P)과 웹 부품은 `hey-passenger-web/docs/components.md`. 이 목록에 없는 부품이 필요하면 이 문서에 먼저 추가하고 DS에 만든 뒤 화면에서 쓴다. 화면 코드에서 부품을 새로 그리지 않는다.

디자인 방향(목업 색·테마 기본값·모서리·그림자)은 `hey-passenger-web/docs/components.md` 서두가 소유한다. 차량 앱은 어두운 테마 기본(밤 운행, 눈부심).

## 1. 화면 목록

### 승객 웹 (P0~P7)

`hey-passenger-web/docs/components.md` §1이 소유한다(사본 없음).

### 기사 차량 앱 (D, 가로 2560×1440 @160dpi = 1600×900dp 기준, 1280×720dp도 깨지지 않음)

| ID | 이름 | 진입 | 핵심 동작 |
|---|---|---|---|
| D1 | 대기 | 앱 실행, 운행 끝 | QR(고정 차량 코드)과 6개 언어 안내, 연결 상태, 설정 |
| D2 | 연결 요청 | 승객 연결 요청 | 언어·인원 보기, 승인/거절(정차 중만). 주행 중이면 음성만, 정차하면 화면 |
| D3 | 탑승 카드 확인 | 카드 받음 | 한국어 요약(목적지 크게·주소·원어 이름 작게·조건), 경로 시작, 다시 확인 요청, 내릴 곳 제안하기, 다시 듣기 |
| D4 | 내릴 곳 제안 | D3, D6 | 후보(출입구 목록 + 목적지 기본 위치) 고르기, 이유 고르기, 보내기. 승객 응답 상태 |
| D5 | 주행 중 | 주행 전환 | 상태 "주행 중 · 음성으로 알려 드려요", 방금 읽은 요청 1줄, 목적지·남은 시간, 대기 요청 수. 입력 없음 |
| D6 | 정차 중 요청 | 주행→정차 시 답할 것이 있을 때, 또는 D3에서 "답장하기" | 요청 목록(다시 듣기), 빠른 답장(답장 3개는 열린 질문이 있을 때만·기사님이 먼저 3개), 음성으로 답하기(정차 중) |
| D7 | 도착·운행 끝 | 도착 | 합의된 하차 위치, 운행 요약, 운행 끝내기(확인), 다음 승객 |
| D8 | 설정 | D1 | 받을 프리셋 켜기/끄기, 음성 간격, 음성 속도, 화면 테마, 차량 번호 뒤 4자리 |

## 2. 웹 부품 (이 저장소 밖)

`packages/ui-web`의 웹 부품 목록(prop·상황 이야기, 부품 API 유니언)은 `hey-passenger-web/docs/components.md` §2·§5가 소유한다. 이 문서에는 사본을 두지 않는다.

## 3. 차량 부품 (`:core:designsystem`, Compose)

`:feature:*`는 이 모듈의 공개 함수만 쓴다. 모든 부품은 `modifier: Modifier = Modifier`를 받아 그대로 잇기만 하고 바깥 여백을 더하지 않는다. 문구는 `String`으로 받되 화면이 `stringResource` 또는 서버 한국어 문장만 넘긴다.

생성 토큰 모양(`HeyTokens.kt`, hey-shared `android/`의 `ai.hey:tokens-compose`, 생성기 `shared/packages/tokens/build.mjs`):
```kotlin
object HeyTokens {
  object Space { val s4 = 4.dp; val s8 = 8.dp; val s16 = 16.dp; val s24 = 24.dp; val s32 = 32.dp }
  object Radius { val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp; val full = 999.dp }
  object Border { val hairline = 1.dp; val strong = 2.dp; val focus = 3.dp }
  object Size { val touchCar = 76.dp; val touchCarPrimary = 96.dp; val iconCar = 40.dp; /* … 모든 size 키 */ }
  object Motion { const val fast = 120; const val base = 200; const val slow = 320 }
  object CarType { val hero = HeyTypeSpec(72.sp, 88.sp, 700, -0.02f); /* title, heading, body, label, caption */ }
}
data class HeyTypeSpec(val size: TextUnit, val lineHeight: TextUnit, val weight: Int, val trackingEm: Float)
data class HeyColors(val bgPage: Color, val bgSurface: Color, /* 토큰 color 키 전부를 camelCase로 */)
val HeyLightColors: HeyColors; val HeyDarkColors: HeyColors
```

| 부품 | 주요 인자 | 스냅숏 상황 |
|---|---|---|
| `HeyTheme(dark = true)` | `CompositionLocal`로 `HeyColors`·글꼴(Pretendard) 제공 | 라이트·다크 |
| `VStack` / `HStack` | `gap: HeyGap`(`G4,G8,G16,G24,G32`), `align` | — |
| `CarFrame` | `statusStrip`, `driving: Boolean`, `content`, `actionRail?` | 정차·주행(행동 막대 숨김) |
| `StatusStrip` | `wordmarkParty`, `drivingLabel`, `connection`, `clock?` | 인원 1~4, 오프라인 |
| `HeyText` | `text`, `variant: HeyTextVariant(Hero,Title,Heading,Body,Label,Caption)`, `tone: HeyTone(Default,Muted,Subtle,Accent,Danger,Success,OnAccent)`, `maxLines` | 긴 한국어 |
| `HeyWordmark` | `partySize`, `large` | 1~4 |
| `HeyIcon` | `icon: HeyIcons.X`, `contentDescription`, `size` | 표 |
| `HeyButton` | `text`, `onClick`, `variant(Primary,Accent,Secondary,Danger,Ghost)`, `size(Regular,Large)`, `icon?`, `enabled`, `loading` | 변형×상태 |
| `HeyIconButton` | `icon`, `contentDescription`, `onClick` | — |
| `HeyCard` | `tone(Default,Raised,Accent,Sunken)`, `content` | — |
| `HeyStatusPill` | `text`, `tone` | — |
| `HeyBanner` | `tone`, `title`, `message?` | — |
| `HeyDivider` | — | — |
| `HeySwitchRow` | `label`, `checked`, `onCheckedChange`, `enabled` | — |
| `HeyStepperRow` | `label`, `value`, `range`, `onChange`, `format` | 끝값 |
| `HeySegmented` | `options`, `selected`, `onSelect` | — |
| `HeyDialog` | `title`, `message`, `confirm`, `dismiss` | — |
| `HeyQrCode` | `content: String`, `size: Dp`(부모가 줌) | 실제 URL |
| `LanguageGreetingStrip` | `lines: List<String>` (QR 안내 6개 승객 언어, 문장은 hey-shared `car.scanQr`에서 빌드 때 생성), `columns = 2` | 2열×3행 격자, 1280에서 스크롤 없음 |
| `JoinRequestCard` | `langLabel`, `partySize`, `expiresInSec`, `onApprove`, `onDecline`, `enabled`(정차 중만) | 정차·주행(비활성) |
| `KoreanSummary` | `destinationKo`, `addressKo`, `nameLocal`, `conditions: List<ConditionLine>` | 조건 0·4개, 긴 주소 |
| `ConditionLine` | `icon`, `text`, `changed: Boolean`(바뀐 부분 강조) | 바뀜 표시 |
| `ConditionLineRow` | `line: ConditionLine` (한 줄 그리기; `KoreanSummary`가 씀) | 바뀜 표시 |
| `DropoffCandidateRow` | `label`, `distanceText?`, `selected`, `onSelect` | — |
| `ReasonChips` | `reasons`, `selected`, `onSelect` | 6개 |
| `ProposalStatus` | `state(Pending,Ok,Elsewhere,Unsure,Superseded)` | 다섯 |
| `RequestRow` | `textKo`, `kind(Notice,Question,Free)`, `state`, `time`, `onReplay` | 각 상태, 긴 직접 입력 |
| `QuickReplyGrid` | `replies: List<QuickReplyUi>`, `onReply`, `enabled` | 3개, 비활성 |
| `QuickReplyGroup` | `title`, `replies`, `onReply`, `enabled`, `loadingId?` — 제목 있는 한 묶음. "답장"(yes·cannot·moment, 열린 질문이 있을 때만)과 "기사님이 먼저"(stopHere·fiveMin·cardOk, `QUICK_REPLY_META` 묶음) 둘 다 이 부품 | 답장·기사님이 먼저·로딩 |
| `VoiceConfirmDialog` | `title`, `textKo`, `send`, `retry`, `cancel`, `sending`, `enabled`, `notice?`(보내기가 거절된 이유, 예: 번역 실패. 막 뒤 배너에 가려지지 않게 카드 안에 그린다) — 인식된 한국어 문장을 보내기 전에 확인(정차 중 D6). `HeyDialog`와 같은 막+카드 | 기본·긴 문장·보내는 중 |
| `DrivingPanel` | `lastSpokenKo?`, `destinationKo`, `etaText`, `pendingCount` | 요청 없음·있음 |
| `EtaBlock` | `etaText`, `distanceText`, `stale` | — |
| `TripSummaryCard` | `rows: List<Pair<String,String>>` | — |
| `EmptyPanel` | `icon`, `title`, `message?` | — |
| `HeyScrollColumn` | `gap: HeyGap`, `content` (CarFrame 안에서 목록 구역만 세로 스크롤, 스크롤 막대 표시) | 항목 많음 at 1280×720dp |
| `HeySection` | `title`, `content` (제목 + 묶음, 묶음 사이 24) | — |
| `HeyNotice` | `text`, `tone` (화면 위 짧은 일회성 안내, 4초 뒤 사라짐, 움직임 줄이기 시 즉시) | — |
| `HeyTextField` | `label`, `value`, `onValueChange`, `keyboard(Text,Number,Uri)`, `enabled`, `placeholder?`, `error?` | 값·빈 값+안내·오류·비활성·긴 값 |
| `HeyLayered` | `overlay?`(HeyDialog), `notice?`(HeyNotice), `content` (슬롯이 null이면 그리지 않음) | 다이얼로그·알림 |
| `QrBlock` | `content: String` (320dp 고정 크기 QR) | 실제 URL |
| `QuickReplyGrid` 추가 인자 | `loadingId?` (그 답장만 로딩, 나머지 비활성) | 보내는 중 |

## 4. Storybook 구성 (이 저장소 밖)

루트 Storybook은 `hey-passenger-web`이 소유한다. 거기서 `HEY_CAR_DIR`(기본 `../hey-car`)의 Paparazzi PNG를 읽어 `차량 부품/…`, `차량 화면/D1…D8` 이야기를 만든다(`storybook/car/generate.mjs`, 1600×900dp, 다크·라이트). 이 저장소가 소유하는 것은 PNG 자체(`./gradlew recordPaparazziDebug`)와 `docs/diffs.md`다.
