# HEY 부품·화면 목록 (2026-11-12 초판, 임시 디자인 시스템)

`docs/architecture.md` 4절 규칙을 따른다. 이 목록에 없는 부품이 필요하면 이 문서에 먼저 추가하고 DS에 만든 뒤 화면에서 쓴다. 화면 코드에서 부품을 새로 그리지 않는다.

디자인 방향: 목업 `proposal/heyyy-flow.html`의 아이보리(`#f4f3ed`)·라임(`#d2f264`)·딥그린(`#344c37`). 승객 웹은 밝은 테마 기본, 차량 앱은 어두운 테마 기본(밤 운행, 눈부심). 디자이너 자료가 오면 토큰 값만 바꾸고 이름은 유지한다. 둥근 모서리는 절제한다(카드 16, 버튼 12, 칩 full). 그림자는 시트·떠 있는 막대에만. 승객 웹 본문 폭은 480 상한.

## 1. 화면 목록

### 승객 웹 (P, 휴대폰 세로 360~430 기준, 데스크톱에서는 가운데 480 열)

| ID | 이름 | 진입 | 핵심 동작 |
|---|---|---|---|
| P0 | 차량 확인·시작 | QR `/c/<carCode>` | 언어 고르기, 탑승 인원(1~4) 고르기 → HEY의 Y가 늘어남, 연결 요청 |
| P0g | 일행 참여 | 초대 링크 `/t/<inviteCode>` | 언어 고르기, 참여 |
| P1 | 기사 승인 대기 | P0 연결 요청 | 기다림(5분 카운트다운), 취소. 거절·만료·차량 바쁨 상태 |
| P2 | 탑승 카드 작성 | 승인됨(대표 승객) | 목적지 검색·선택, 조건 고르기(짐·덜 걷기·만날 입구·실내 온도), 기사님께 보내기 |
| P3 | 기사 확인 중 | 카드 보냄 | 보낸 카드 보기, 고치기(바뀐 부분만 전달), 기사 재확인 요청 띠 |
| P4 | 운행 중 | 경로 시작됨 | 탑승 카드, 남은 시간·거리(stale 표시), 통행료 안내, 요청 보내기(프리셋 8개·직접 입력), 대화 내역(상태 셋), 일행 초대, 메뉴(목적지 바꾸기·언어·나가기) |
| P5 | 하차 위치 확인 | 기사 제안 도착(시트) | 지도에 제안 지점·이유, 여기 좋아요/다른 곳을 원해요/위치를 모르겠어요 |
| P5p | (삭제됨) 3.0.0에서 승객 핀·내릴 곳 직접 고르기가 없어졌다 | — |
| P6 | 도착 카드 | 도착 | 합의된 하차 위치, 짐 안내, 이동 시간·거리, 미터기·영수증 확인 안내, 불편 신고 안내, 닫기 |
| P7 | 연결 끝·오류 | 종료, 오류 코드 | 끝난 운행, 차량 오프라인, 찾을 수 없는 QR, 운행 정원 초과 |

### 기사 차량 앱 (D, 가로 2560×1440 @160dpi = 1600×900dp 기준, 1280×720dp도 깨지지 않음)

| ID | 이름 | 진입 | 핵심 동작 |
|---|---|---|---|
| D1 | 대기 | 앱 실행, 운행 끝 | QR(고정 차량 코드)과 4개 언어 안내, 연결 상태, 설정 |
| D2 | 연결 요청 | 승객 연결 요청 | 언어·인원 보기, 승인/거절(정차 중만). 주행 중이면 음성만, 정차하면 화면 |
| D3 | 탑승 카드 확인 | 카드 받음 | 한국어 요약(목적지 크게·주소·원어 이름 작게·조건), 경로 시작, 다시 확인 요청, 내릴 곳 제안하기, 다시 듣기 |
| D4 | 내릴 곳 제안 | D3, D6 | 후보(출입구 목록 + 목적지 기본 위치) 고르기, 이유 고르기, 보내기. 승객 응답 상태 |
| D5 | 주행 중 | 주행 전환 | 상태 "주행 중 · 음성으로 알려 드려요", 방금 읽은 요청 1줄, 목적지·남은 시간, 대기 요청 수. 입력 없음 |
| D6 | 정차 중 요청 | 주행→정차 시 답할 것이 있을 때 | 요청 목록(다시 듣기), 빠른 답장 6개, 승객 핀 제안 수락/어려움 |
| D7 | 도착·운행 끝 | 도착 | 합의된 하차 위치, 운행 요약, 운행 끝내기(확인), 다음 승객 |
| D8 | 설정 | D1 | 받을 프리셋 켜기/끄기, 음성 간격, 음성 속도, 화면 테마, 차량 번호 뒤 4자리 |

## 2. 웹 부품 (`packages/ui-web`, `hey-passenger-web` 저장소)

모두 `className`·`style`을 받지 않는다. 문구는 문자열 prop으로 받되 호출하는 화면이 `t()`로 만든 값만 넘긴다(화면 쪽 `jsx-no-literals`). 아이콘은 `Icon name`으로만.

### 기반
| 부품 | 주요 prop | 상황 이야기 |
|---|---|---|
| `ThemeProvider` | `theme: "light" \| "dark"`, `lang` | 라이트·다크 |
| `AppFrame` | `header`, `footer?`(고정 행동 막대), `children` | 짧은 내용·긴 내용(스크롤)·고정 막대 있음/없음 |
| `Stack` | `gap: 4\|8\|16\|24\|32`, `align?: "stretch"\|"start"\|"center"`, `as?: "div"\|"section"\|"ul"\|"ol"` | 간격별 |
| `Inline` | `gap`, `wrap?`, `justify?: "start"\|"between"\|"end"\|"center"`, `align?` | 줄바꿈 |
| `Section` | `title?`, `action?`, `children` (블록 간 24 고정) | 제목 있음/없음 |
| `Divider` | `inset?` | — |
| `VisuallyHidden` | `children` | — |

### 글자·브랜드
| 부품 | 주요 prop | 상황 |
|---|---|---|
| `Text` | `variant: display\|title\|heading\|body\|bodyStrong\|label\|caption`, `tone?: default\|muted\|subtle\|accent\|danger\|success\|onAccent`, `as?`, `lang?`, `truncate?: 1\|2` | 모든 변형, 띄어쓰기 없는 일본어·중국어 120자, 줄임 |
| `Wordmark` | `partySize: 1\|2\|3\|4`, `size: "md"\|"lg"`, `animate?` | 1~4, 늘어나는 동작(움직임 줄이기 시 즉시) |
| `Icon` | `name: IconName`, `size?: "md"\|"lg"`, `label?` | 전체 아이콘 표 |

`IconName`: `arrow-left, arrow-right, check, close, chevron-down, chevron-right, search, pin, flag, door, mic, volume-off, music, thermometer-cool, thermometer-warm, car, clock, route, toll, speaker, message, send, user, users, globe, menu, info, warning, error, refresh, map, qr, card, plus, minus, link, logout`.

### 행동
| 부품 | 주요 prop | 상황 |
|---|---|---|
| `Button` | `variant: primary\|accent\|secondary\|ghost\|danger`, `size?: "md"\|"lg"`, `icon?`, `loading?`, `disabled?`, `href?`(링크 모드), `onClick` | 변형×상태(기본·누름·비활성·로딩·초점), 긴 라벨 |
| `IconButton` | `icon`, `label`(필수, 보이지 않는 이름), `variant?: ghost\|secondary` | — |
| `SegmentedControl<T>` | `options: {value,label}[]`, `value`, `onChange`, `label` | 2~4개, 긴 라벨 |
| `PartySizePicker` | `value: 1..4`, `onChange` | 1·4(끝 비활성) |
| `ChoiceChip` | `selected`, `icon?`, `label`, `onToggle` | 선택·해제·비활성 |
| `ChipGroup` | `label`, `children`(ChoiceChip) | 1개·많음(줄바꿈) |
| `PresetTile` | `icon`, `label`, `kind: "notice"\|"question"`, `onPress`, `disabled?` | 긴 라벨, 비활성(보낸 직후 중복 방지) |
| `PresetGrid` | `children` (2열 고정) | 8개 |
| `LanguageOption` | `lang`, `nativeName`, `selected`, `onSelect` | — |
| `LanguageList` | `children` | 4개 |

### 입력
| 부품 | 주요 prop | 상황 |
|---|---|---|
| `SearchField` | `label`, `value`, `onChange`, `placeholder`, `loading?`, `onClear` | 빈 값·입력 중·로딩 |
| `TextComposer` | `label`, `value`, `onChange`, `maxLength`, `onSend`, `sending?`, `error?` | 빈 값·글자 수 초과 직전·초과·보내는 중·오류 |
| `ToggleRow` | `label`, `description?`, `checked`, `onChange` | — |

### 표시
| 부품 | 주요 prop | 상황 |
|---|---|---|
| `Card` | `tone?: "default"\|"accent"\|"inverse"\|"sunken"`, `children`, `padding: 16\|24` | 톤별 |
| `StatusPill` | `tone: neutral\|info\|success\|warning\|danger\|accent`, `label`, `icon?` | 톤별 |
| `RequestStatus` | `state: "sending"\|"received"\|"spoken"\|"answered"\|"failed"`, `replyExpected`, `labels` | 알림형 끝(spoken)·질문형 각 단계·실패 |
| `Banner` | `tone: info\|warning\|danger\|success`, `title?`, `message`, `action?` | 톤별, 긴 글 |
| `EmptyState` | `icon`, `title`, `message?`, `action?` | — |
| `Spinner` | `label` | — |
| `Skeleton` | `lines: 1..4` | — |
| `KeyValueRow` | `label`, `value`, `icon?` | 긴 값 |
| `ConnectionIndicator` | `status: "online"\|"reconnecting"\|"offline"`, `labels` | 셋 |
| `Countdown` | `endsAt`, `label` (mm:ss 갱신, 끝나면 `onEnd`) | — |
| `PlateBadge` | `plateSuffix` | — |

### HEY 도메인 부품
| 부품 | 주요 prop | 상황 |
|---|---|---|
| `ScreenHeader` | `partySize`, `plateSuffix?`, `connection`, `connectionLabels`, `connectionDetail?`(오프라인 사유 한 줄), `back?`, `menu?` | 인원 1~4, 오프라인(사유 포함) |
| `PlaceResultRow` | `nameLocal`, `nameKo`, `area`, `lang?`, `selected?`, `onSelect` | 긴 이름, 원어=한국어 같을 때 |
| `PlaceResultList` | `children`, 상태(`loading`/`empty`)는 부모가 EmptyState·Skeleton으로 | 0·1·8개 |
| `DestinationSummary` | `nameLocal`, `nameKo`, `addressKo`, `area?` | 긴 주소 |
| `ConditionSummary` | `items: {icon,label}[]` | 0·1·4개 |
| `BoardingCard` | `destination`, `dropoff?`, `conditions`, `status`, `footer?` | 작성 중·보냄·확인됨·운행 중, 하차 미정/제안 중/합의 |
| `ShowDriverCard` | `textKo`, `caption` (기사님께 보여 주는 큰 한국어) | 긴 한국어 |
| `TripProgress` | `etaMin`, `distanceKm`, `stale`, `labels` | 정상·stale·없음 |
| `TollNotice` | `hasToll`, `tollFare?`, `labels` | 통행료 있음/없음 |
| `MessageBubble` | `from: "me"\|"driver"\|"system"`, `text`, `lang?`, `subText?`(원문), `status?`·`replyExpected?`·`statusLabels?`, `time`, `listen?`, `retry?` | 긴 글, 각 상태, 실패(다시 보내기) |
| `MessageList` | `children`, 새 항목 도착 시 아래로(사용자가 위로 스크롤했으면 유지) | 0·1·많음 |
| `MapView` | `label`, `center`, `zoom?`, `markers: {id,kind: "destination"\|"candidate"\|"proposal"\|"mine", point, label}[]`, `selectedId?`, `onSelect?`, `onPlacePin?`(핀 모드), `locked?`, `height: "md"\|"lg"`, `fallbackText`(타일 실패 시 문구, 화면이 `t()`로 넘김), `tileUrl?`. Leaflet + OSM 타일(저작자 표시 필수), 차량 실시간 위치는 그리지 않는다 | 마커 1·여러 개·핀 모드·잠금, 지도 타일 실패(대체 표시) |
| `DropoffCandidateRow` | `label`, `distanceLabel?`, `selected`, `onSelect` | 긴 라벨 |
| `DropoffProposalCard` | `proposal: {label,status}`, `reasonText?`, `distanceLabel?`, `labels`, `onRespond`, `onLater?`, `responding?`, `disabled?` | 대기·응답 중·잠김·응답함(ok/elsewhere/unsure)·대체됨 |
| `ArrivalSummary` | `dropoff: {heading,label}`, `duration?`, `distance?`, `conditions`, `notes` | 짐 있음/없음, 값 없음 |
| `InviteCard` | `url`, `title`, `message`, `copyLabel`, `copiedLabel`, `onCopy`, `copied` | 복사 전·후, 긴 링크 |

### 겹침
| 부품 | 주요 prop | 상황 |
|---|---|---|
| `BottomSheet` | `open`, `onClose`, `title`, `children`, `footer?`, `dismissible?` | 짧은·긴 내용, 닫기 불가(응답 필요) |
| `Dialog` | `open`, `title`, `message`, `confirm`, `cancel`, `tone?: "default"\|"danger"` | — |
| `Toast` | `message`, `tone?` (아래 고정, 4초) | — |
| `Menu` | `items: {id,label,icon,tone?}[]`, `onSelect` (BottomSheet 안에서 씀) | — |

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
| `DropoffCandidateRow` | `label`, `distanceText?`, `selected`, `onSelect` | — |
| `ReasonChips` | `reasons`, `selected`, `onSelect` | 6개 |
| `ProposalStatus` | `state(Pending,Ok,Elsewhere,Unsure,Superseded)` | 다섯 |
| `RequestRow` | `textKo`, `kind(Notice,Question,Free)`, `state`, `time`, `onReplay` | 각 상태, 긴 직접 입력 |
| `QuickReplyGrid` | `replies: List<QuickReplyUi>`, `onReply`, `enabled` | 3개, 비활성 |
| `QuickReplyGroup` | `title`, `replies`, `onReply`, `enabled`, `loadingId?` — 제목 있는 한 묶음. "답장"(yes·cannot·moment, 열린 질문이 있을 때만)과 "기사님이 먼저"(stopHere·fiveMin·cardOk, `QUICK_REPLY_META` 묶음) 둘 다 이 부품 | 답장·기사님이 먼저·로딩 |
| `VoiceConfirmDialog` | `title`, `textKo`, `send`, `retry`, `cancel`, `sending`, `enabled`, `notice?`(보내기가 거절된 이유, 예: 번역 실패. 막 뒤 배너에 가려지지 않게 카드 안에 그린다) — 인식된 한국어 문장을 보내기 전에 확인(정차 중 D6). `HeyDialog`와 같은 막+카드 | 기본·긴 문장·보내는 중 |
| `PassengerPinCard` | (삭제됨) 3.0.0에서 승객 핀 제안이 없어져 부품도 없다 | — |
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

## 4. Storybook 구성

루트 Storybook 하나가 웹과 차량을 함께 보여 준다. 사이드바 순서:

1. `소개/` — 읽는 법, 검수판(부품별 검수 상태 표: 초안·검수 중·승인), 화면 흐름 지도.
2. `토큰/` — 색(라이트·다크 나란히), 간격, 모서리, 글자, 누름 크기.
3. `웹 부품/기반·글자·행동·입력·표시·HEY·겹침` — 2절 부품, 이야기마다 위 상황 전부.
4. `승객 화면/P0…P7` — 실제 화면 컴포넌트를 가짜 저장소(`TripStore` 주입)로 렌더. 휴대폰 뷰포트(390×844) 기본, 상호작용 `play` 포함.
5. `차량 부품/…`, `차량 화면/D1…D8` — Paparazzi가 찍은 실제 Compose 렌더 PNG를 생성 스크립트(`storybook/car/generate.mjs`)가 이야기로 만든다. 1600×900dp, 다크·라이트.
