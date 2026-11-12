# HEY 개발 계약 (2026-11-12 초판)

이 문서는 저장소 `hey-car`(차량 앱)의 모든 작업자가 따르는 계약이다. 원래 모노레포 `hey-taxi`의 문서를 분리하면서 옮겼고, 규칙(2~4절)은 그대로다. 바뀐 것은 저장소 구조·명령·경로뿐이다: 공유 패키지는 `hey-shared`(서브모듈 `shared/`), 중계 서버는 `hey-relay`, 승객 웹은 `hey-passenger-web`이다. 아래에 `apps/`·`packages/` 경로가 나오면 그 저장소의 같은 경로를 뜻한다. 제품 설명은 기획 저장소 `darren-pleos-challenge/pleos-challenge`의 `proposal/hey/`(서비스 기획서·정책서·화면별 정의서)에 있다. 여기에는 코드가 지켜야 할 구조, 이름, 경계만 적는다. 바꿀 때는 이 문서를 먼저 고치고 코드를 맞춘다.

## 0. 한 문장과 범위

길에서 잡은 택시에서도 외국인 승객과 기사님이 목적지와 내릴 곳을 서로 다른 언어로 같은 뜻으로 맞추게 해 주는 서비스다. 구성은 셋이다.

| 구성 | 위치 | 기술 |
|---|---|---|
| 승객 웹 (휴대폰 브라우저, 설치 없음) | `hey-passenger-web` 저장소 | Vite + React 19 + TypeScript, `@hey/ui-web`만으로 화면 조립 |
| 기사 차량 앱 (Pleos Connect, AAOS 가로) | 이 저장소(`hey-car`) 루트 | Kotlin + Jetpack Compose, `:core:designsystem`만으로 화면 조립 |
| 중계 서버 | `hey-relay` 저장소 | Bun + TypeScript (`Bun.serve` REST + WebSocket), 메모리 저장 |

공유 패키지: `hey-shared` 저장소의 `packages/tokens`(디자인 토큰 정본), `packages/protocol`(메시지·상태 타입과 ID 목록), `packages/copy`(승객 언어 문구 카탈로그), 그리고 `hey-passenger-web`의 `packages/ui-web`(웹 디자인 시스템). 이 저장소에서는 `shared/`(git 서브모듈, 태그 고정)로 본다.

## 1. 기획자 답변 전 기본값

기획자 질문 Q1~Q30(`pleos-challenge` 브랜치 `heyyy`의 `HANDOFF-planner.md`)에 답이 오기 전까지는 각 질문의 추천안을 기본값으로 구현한다. 답이 오면 이 표를 고치고 해당 값만 바꾼다. 값은 코드 한 곳(`packages/protocol/src/config.ts`, 차량 앱 `core/model/HeyConfig.kt`)에만 둔다.

| Q | 기본값 | 코드에 닿는 곳 |
|---|---|---|
| Q2 | 앱 이름 HEY, 탑승 인원 1~4명에 따라 HEY·HEYY·HEYYY·HEYYYY | `Wordmark`(웹), `HeyWordmark`(차량) |
| Q3 | 대표 장면은 하차 위치 제안·확인 | 화면 우선순위 |
| Q4 | 1차 상황은 공항→호텔 | 시연 데이터 |
| Q10 | 승객 언어 en, ja, zh-Hans (+ 검수용 ko) | `packages/copy` |
| Q11 | 탑승 카드 조건: 짐 있음, 덜 걷기, 만나기로 한 입구, 실내 온도(시원하게·따뜻하게) | `ConditionId` |
| Q12 | 빠른 답장: 네 / 잠시만요 / 5분 걸려요 / 여기서 세울게요 / 카드 돼요 / 어려워요 | `QuickReplyId` |
| Q13 | 요금은 경로 통행료만 승객 언어로 안내, 미터 요금은 표시하지 않음 | `TollNotice` |
| Q14 | 승객 지도는 목적지·하차 지점만, 차량 위치 없음 | `MapView` |
| Q15 | 기사 음성 답장은 빼고 버튼만 | 차량 앱 STT 미사용 |
| Q16 | 하차 위치는 기사·승객 모두 제안, 승객 핀은 정차 중 기사가 확인 | `DropoffProposal.by` |
| Q18 | Y 늘리기는 승객 웹 상단과 차량 화면 모두 | 두 Wordmark |
| Q19 | 슬로건 "내릴 곳까지, 같은 뜻으로." | `copy` |
| Q21 | 저장소 이름 `hey-taxi` | — |

## 2. 상태와 흐름

### 2.1 차량과 QR

차량마다 고정 QR 하나가 조수석 뒷면에 붙는다. QR 내용은 `https://<웹 주소>/c/<carCode>`이다. `carCode`는 차량 앱이 처음 실행될 때 서버에 등록하고(`POST /v1/cars`) 받는 8자리 영숫자(혼동 문자 0 O 1 I L 제외)이며 바뀌지 않는다. 차량 앱은 `driverToken`(128비트)을 기기에만 저장한다.

QR은 연결 요청만 만든다. 기사님이 정차 중 한 번 승인해야 운행(trip)이 열리고, 운행이 끝나면 그 운행의 모든 기기 토큰이 폐기된다. 지난 승객이 같은 QR로 다시 들어오면 새 연결 요청이 될 뿐 끝난 운행을 볼 수 없다. 운행 중이거나 다른 연결 요청이 대기 중일 때 QR을 찍으면 `CAR_BUSY`로 "이미 운행 중이에요. 일행이면 대표 승객에게 초대 링크를 받으세요"가 뜬다. 일행은 대표 승객 화면의 초대 링크(`/t/<inviteCode>`)로 들어오며 기사 승인이 필요 없다. 한 운행의 기기는 4대까지다.

연결 요청 5분(`join.requestTtlSec`)은 주행 중인 시간도 센다. 요청이 오면 차량은 음성으로 한 번 알리고, 승인·거절 버튼은 정차 중에만 눌린다.

차량 등록(`carId`, `carCode`, `driverTokenHash`, `plateSuffix`)은 서버 재시작 뒤에도 남아야 고정 QR이 유지되므로 서버 로컬 파일(`HEY_DATA_DIR/cars.json`, 기본 `./data`)에 저장한다. 운행·기기·대화는 메모리에만 두고 재시작하면 사라진다(승객 화면은 `TRIP_ENDED`).

권한: 대표 승객(`role: "lead"`)만 탑승 카드 보내기·고치기, 하차 제안 응답, 핀 제안, 초대 링크 보기, 운행 나가기(=운행 종료)를 한다. 일행(`role: "guest"`)은 카드·하차 상태를 보고 요청(프리셋·직접 입력)을 보낼 수 있다. 일행의 `trip.leave`는 그 기기만 빠진다. 대표가 아닌 기기의 대표 전용 메시지는 `NOT_LEAD`.

### 2.2 운행 상태

```
REQUESTED --기사 승인--> CONNECTED --카드 보냄--> CARD_SENT --경로 시작--> IN_TRIP --도착--> ARRIVED --기사 종료--> ENDED
REQUESTED --거절·5분 경과--> (요청 폐기, 승객에게 DECLINED/EXPIRED)
CARD_SENT --기사 재확인 요청--> CONNECTED
IN_TRIP --승객 목적지 변경--> CARD_SENT
모든 상태 --기사 종료·대표 승객 나가기·4시간--> ENDED
```

`TripState = "REQUESTED" | "CONNECTED" | "CARD_SENT" | "IN_TRIP" | "ARRIVED" | "ENDED"`. 차량 쪽 주행 여부는 `DrivingState = "PARKED" | "DRIVING"`이며 차량 앱이 서버에 알린다(`driving.state`).

도착은 종료 후보일 뿐이다. 최종 종료는 기사님의 "운행 끝내기"이며, 문 열림이나 목적지 근처 정차만으로 끝내지 않는다.

### 2.3 하차 위치 제안 (대표 장면)

제안은 버전을 가진다. `DropoffProposal = { version, by: "driver" | "passenger", point: { lat, lng }, label, reasonId?, status }`. `status`는 기사 제안이면 `pending | ok | elsewhere | unsure | superseded`, 승객 핀이면 `pending | accepted | declined | superseded`다. 합의된 하차 위치(`agreedDropoff`)는 기사 제안 `ok` 또는 승객 핀 `accepted`인 가장 최근 버전이다.

- 기사 제안의 승객 응답은 `ok`(여기 좋아요) / `elsewhere`(다른 곳을 원해요) / `unsure`(위치를 모르겠어요) 셋이다. 응답 없음은 `pending`으로 남고 동의로 바꾸지 않는다.
- 새 제안이 오면 이전의 **대기 중** 제안은 `superseded`가 되고, 이전 응답을 새 버전에 재사용하지 않는다. 이미 응답이 끝난 제안은 그 응답(`ok`·`elsewhere`·`unsure`·`accepted`·`declined`)을 남긴다. 대기 중인 제안은 언제나 하나뿐이다.
- 승객 핀 제안(`by: "passenger"`)은 기사 화면에 쌓이고, 기사님이 정차 중 `accept`/`decline` 한다. 주행 중에는 음성으로 "승객이 내릴 곳을 지도에서 골랐어요. 정차하면 확인해 주세요"만 나간다.
- 합의된 하차 위치가 내비 목적지와 다르면 기사님이 다시 경로 시작을 눌러야 내비가 바뀐다. 승인 없이 경로를 바꾸지 않는다.

하차 후보 목록은 서버가 OSM Overpass로 목적지 반경 150m 안의 출입구(`entrance=*`) 노드를 찾아 만든다(`GET /v1/places/{placeId}/entrances`). 없으면 "목적지 기본 위치"만 준다.

### 2.4 요청과 답장

승객 요청은 프리셋 또는 직접 입력이다. 프리셋 한국어 음성 문장은 검수된 고정 문장(`packages/copy/ko.ts`의 `speech.*`)을 쓰고, 직접 입력은 서버가 번역해 한국어 문장으로 만든다. 요청 상태는 셋을 구분해 보여 준다: `received`(차량 수신) → `spoken`(음성 전달) → `answered`(기사 응답). 알림형 프리셋은 `spoken`에서 끝나고, 질문형과 직접 입력은 `answered`까지 간다. 음성 완료를 기사 수락으로 표시하지 않는다. 차량이 오프라인이라 전달하지 못한 요청은 `failed`이며 승객 화면에 다시 보내기가 붙는다. 번역에 실패한 직접 입력은 요청으로 저장하지 않는다: 서버는 그 요청의 `clientRef`를 실은 `error(TRANSLATE_FAILED)`만 돌려주고 분당 한도(3회)를 쓰지 않는다. 클라이언트는 입력창에 쓴 글을 되살리고 오류를 보여 준다(`RATE_LIMITED`·`TOO_LONG`과 같은 경로). 직접 입력 분당 3회 제한은 기기별이다.

음성 우선순위 `high`는 탑승 카드 도착·변경, 하차 제안 응답, 승객 핀 제안, `stopHere` 프리셋이다. 나머지는 `normal`이며 도착 순서대로 `speech.gapSec` 간격을 둔다.

도착(`trip.arrive`)은 차량 앱이 내비 `onDestinationArrived`를 받거나, 이 이벤트가 오지 않는 경우를 대비해 기사님이 D5·D6의 "도착" 버튼을 누를 때 보낸다. 기사님이 끌 수 없는 프리셋은 `stopHere`와 `card`다.

## 3. 프로토콜

모든 타입의 정본은 `packages/protocol/src/*.ts`다. 차량 앱 `:core:model`은 그것을 손으로 옮긴 Kotlin 타입이며, `packages/protocol/fixtures/*.json`의 예시를 양쪽 시험이 읽어 같은 모양인지 확인한다.

### 3.1 REST

| 메서드 | 경로 | 호출자 | 요청 | 응답 |
|---|---|---|---|---|
| POST | `/v1/cars` | 차량(최초 1회) | `{ plateSuffix }` | `{ carId, carCode, driverToken, qrUrl }` |
| GET | `/v1/c/{carCode}` | 승객 | — | `{ plateSuffix, online, busy }` |
| POST | `/v1/c/{carCode}/join` | 승객 | `{ lang, partySize }` | `{ joinRequestId, deviceToken }` |
| POST | `/v1/t/{inviteCode}/join` | 일행 | `{ lang }` | `{ tripId, deviceToken }` |
| GET | `/v1/places?q=&lang=` | 승객 | `Authorization: Device <token>` | `Place[]` 최대 8개 (Nominatim) |
| GET | `/v1/places/{placeId}/entrances` | 승객·차량 | 토큰 | `DropoffCandidate[]` (Overpass) |
| GET | `/healthz` | — | — | `{ ok: true }` |

### 3.2 WebSocket

연결: `wss://<서버>/v1/ws?token=<driverToken|deviceToken>`. 봉투는 `{ type, id, ts, data }`이고 받는 쪽은 `{ type: "ack", id }`를 돌려준다. 같은 `id`는 한 번만 처리한다.

읽기 모델은 하나다. 서버는 변경이 있을 때마다 역할별 `trip.snapshot`을 보낸다. 승객용은 그 기기 언어로, 차량용은 한국어로 채워져 있다. 화면은 스냅숏만 그린다. 별도 이벤트는 음성(`say`)과 일회성 알림(`notice`)뿐이다.

| type | 방향 | data |
|---|---|---|
| `trip.snapshot` | 서버→각 기기 | 승객: `PassengerTripView`, 차량: `CarView` |
| `say` | 서버→차량 | `{ speechId, textKo, priority: "high" \| "normal" }` |
| `notice` | 서버→기기 | `{ noticeId, kind }` |
| `join.approve` / `join.decline` | 차량→서버 | `{ joinRequestId }` |
| `card.submit` | 대표 승객→서버 | `{ destination: Place, conditions: ConditionId[], entranceNote?, temperature?: "cooler" \| "warmer" }` |
| `card.update` | 승객→서버 | `card.submit`의 일부 필드. 바뀐 부분만 기사에게 음성 |
| `card.recheck` | 차량→서버 | `{}` |
| `route.started` | 차량→서버 | `{ durationSec, distanceM, tollFare }` |
| `route.failed` | 차량→서버 | `{ code }` |
| `trip.progress` | 차량→서버 | `{ etaSec, distanceM, stale }` (60초 간격) |
| `dropoff.propose` | 차량 또는 승객→서버 | `{ point, label, kind?, entranceType?, ref?, street?, reasonId?, candidateId? }` — 승객은 `candidateId`로 후보를 고르면 서버가 후보 구조·좌표를 쓴다(못 찾으면 핀) |
| `dropoff.respond` | 승객→서버 | `{ version, response: "ok" \| "elsewhere" \| "unsure" }` |
| `dropoff.decide` | 차량→서버 | `{ version, decision: "accept" \| "decline" }` |
| `request.send` | 승객→서버 | `{ kind: "preset" \| "free", presetId?, text?, clientRef }` — `clientRef`(1~80자)는 보낸 기기의 `RequestItem.clientRef`와 오류의 `clientRef`로 돌아온다 |
| `request.spoken` | 차량→서버 | `{ requestId }` |
| `reply.send` | 차량→서버 | `{ requestId?, replyId: QuickReplyId }` |
| `driving.state` | 차량→서버 | `{ driving: boolean }` |
| `trip.arrive` / `trip.end` | 차량→서버 | `{}` |
| `trip.leave` | 승객→서버 | `{}` |
| `device.lang` | 승객→서버 | `{ lang }` — 이후 스냅숏 문구가 새 언어로 온다 |
| `error` | 서버→발신자 | `{ code: ErrorCode, ref?, clientRef? }` |

### 3.3 ID 목록 (정본: `packages/protocol/src/catalog.ts`)

- `ConditionId`: `luggage`, `lessWalking`, `meetEntrance`, `temperature`
- `PresetId`(승객→기사): `slower`, `cooler`, `warmer`, `window`(질문), `card`(질문), `howLong`(질문), `stopHere`, `trunk`
- `QuickReplyId`(기사→승객): `yes`, `moment`, `fiveMin`, `stopHere`, `cardOk`, `cannot`
- `DropoffReasonId`: `frontNoStop`, `sideCloser`, `luggageEasier`, `parkingEntrance`, `acrossSafer`, `construction`
- `ErrorCode`: `CAR_NOT_FOUND`, `CAR_BUSY`, `CAR_OFFLINE`, `JOIN_DECLINED`, `JOIN_EXPIRED`, `TRIP_FULL`, `TRIP_ENDED`, `NOT_LEAD`, `RATE_LIMITED`, `TOO_LONG`, `TRANSLATE_FAILED`, `PLACE_NOT_FOUND`, `ROUTE_FAILED`, `STALE_VERSION`, `BAD_REQUEST`

### 3.4 정책값 (정본: `packages/protocol/src/config.ts`)

| 키 | 값 |
|---|---|
| `join.requestTtlSec` | 300 |
| `trip.maxSec` | 14400 |
| `trip.maxDevices` | 4 |
| `free.maxChars` | 100 |
| `free.perMinute` | 3 |
| `speech.gapSec` | 8 (기본, 설정 4~30) |
| `speech.dedupWindowSec` | 60 |
| `speech.queueMax` | 5 |
| `trip.progressPollSec` | 60 |
| `ws.ackTimeoutMs` | 5000 |
| `arrivalCard.keepHours` | 24 (승객 기기 `localStorage`만) |

## 4. 디자인 시스템 규칙 (예외 없음)

근거: 스킬 `product-foundations` 2절, darren-secretary D123·D124·D128, 사용자 지시 "디자인 시스템을 구축하고 그 컴포넌트만 사용해서 개발, 예외 없음"(2026-11-12).

1. 토큰 정본은 `shared/packages/tokens/hey.tokens.json`(hey-shared) 하나다. hey-shared에서 `bun run tokens`가 `packages/tokens/dist/tokens.css`(CSS 변수), `packages/tokens/dist/tokens.ts`, `android/src/main/kotlin/ai/hey/designsystem/token/HeyTokens.kt`를 생성한다. 차량 앱은 `HeyTokens.kt`를 복사하지 않고 `includeBuild("shared/android")`로 `ai.hey:tokens-compose`에 의존한다. 생성 파일은 손으로 고치지 않는다.
2. CSS 변수 이름: `--hey-color-<name>`(테마별 값은 `[data-theme="light"|"dark"]`), `--hey-space-<4|8|16|24|32>`, `--hey-radius-<sm|md|lg|xl|full>`, `--hey-border-<hairline|strong|focus>`, `--hey-type-<variant>-<size|line|weight|tracking>`, `--hey-size-<name>`, `--hey-sheet-<name>`, `--hey-motion-<name>`, `--hey-elevation-<name>`, `--hey-font-family`.
3. 간격은 4·8·16·24·32만 쓴다. 부품은 바깥 여백·폭·위치를 갖지 않는다. 간격은 부모 컨테이너(`Stack`/`Inline`, Compose `VStack`/`HStack`)가 `gap`으로 정한다.
4. 웹 부품은 `className`·`style`을 받지 않는다. 변형이 필요하면 이름 있는 prop(`variant`, `tone`, `size`)을 부품에 추가한다.
5. 부품 CSS에는 색·길이 리터럴이 없다. `var(--hey-*)`만 쓴다(`0`, `100%`, `auto`, `1fr`, `currentColor`는 허용). `bun run check:css`가 위반을 0개 기준으로 막는다.
6. `apps/passenger-web/src` 안에서는 HTML 태그 JSX(`<div>`, `<button>` 등 소문자 태그)와 `.css` 가져오기, `style=` 속성이 금지다. ESLint `no-restricted-syntax`가 오류로 막는다. 화면 폴더에는 CSS 파일이 없다.
7. 차량 앱 `:feature:*` 모듈은 `androidx.compose.material3`·`androidx.compose.foundation`에 의존하지 않는다(Gradle 의존성에 없음). 그래서 `Text`·`Button`·`Column`을 직접 쓸 수 없고 `:core:designsystem`의 `HeyText`·`HeyButton`·`VStack`만 쓸 수 있다. `Color(0x…)`, `.dp` 리터럴은 `:core:designsystem` 밖에서 쓰지 않는다(`./gradlew checkDesignSystemUsage`가 막는다).
8. 화면 문구는 카탈로그에서만 온다. 승객 웹은 `packages/copy`의 `t(key)`, 차량 앱은 `strings.xml`(`stringResource`)과 서버가 준 한국어 문장. JSX 안 문자열 리터럴은 ESLint `react/jsx-no-literals`로 막는다. 정책 설명이나 개발 메모를 화면 문구에 넣지 않는다.
9. 모든 부품은 Storybook(웹) 또는 Paparazzi 스냅숏(차량)에 상황별 이야기가 있어야 화면에서 쓸 수 있다. 상황: 기본, 긴 글(띄어쓰기 없는 일본어·중국어 포함, 120자 이상), 빈 값, 항목 0·1·많음, 비활성, 오류, 로딩, 라이트·다크. 예시 데이터는 실제 모양의 긴 값이다.
10. 누를 수 있는 요소는 겹치지 않는다. 링크처럼 이동하는 단추는 부품의 링크 모드로 그린다. 누름 면은 웹 44px 이상, 차량 76dp 이상(주 동작 96dp).
11. 휴대폰 하단 시트는 `BottomSheet` 하나가 모두 가진다: 아래 화면 검정 32% 막, 위 모서리 반경 24, 손잡이 32×4, 위쪽 64 띄움, 초점 가둠, Esc·뒤로 가기 한 겹씩, 닫으면 연 요소로 초점 복귀, 움직임 줄이기에서 0초.
12. 데이터는 하나의 도구로 읽는다. 승객 웹은 `useTrip()` 저장소 하나가 소켓·재연결·스냅숏을 소유하고, 내용이 같은 스냅숏은 이전 객체를 그대로 돌려준다. 차량 앱은 `TripRepository`의 `StateFlow<CarUiState>` 하나다. 재조회·재연결이 화면 위치·입력 중인 값을 바꾸지 않는다.
13. 앱 쪽 코드는 부품 내부를 고르지 못한다. 부품 CSS는 CSS Modules라 클래스 이름이 해시되고, `apps/**`에는 CSS 파일 자체가 없다(전역 CSS는 `packages/tokens/dist/tokens.css` 하나). `check:css`는 `packages/ui-web` 밖의 `.css`·`.module.css` 파일이 하나라도 있으면 실패한다. 허용 목록은 두지 않는다(darren-secretary에서 앱 전역 CSS가 부품 내부 클래스를 41곳 덮어써 부품 변경이 화면을 몰래 깨뜨렸다, 2026-11-12 협의).
14. 이야기는 "있다"가 아니라 "그 부품을 그렸다"로 센다. `bun run check:stories`는 Storybook 빌드의 `index.json`에서 부품마다 이야기가 있는지, 각 이야기 파일의 `component`가 그 부품인지, 2절 상황 이름(`Default`, `LongText`, `Empty`, `Many`, `Disabled`, `Error`, `Loading` 중 해당하는 것)이 빠졌는지 확인한다. 조립된 승객 화면 이야기는 390·360 폭에서 가로 넘침과 누름 면 겹침을 재는 별도 레이아웃 검사를 둔다.
15. 시각 기준 이미지는 CI 한 곳에서만 만든다. 로컬 `--update-snapshots`로 웹 기준 이미지를 커밋하지 않는다. 기준 이미지를 바꾸는 커밋에는 `docs/diffs.md`에 바뀐 이유를 한 줄씩 적는다. 차량 Paparazzi 스냅숏(JVM 렌더)은 로컬 기록을 허용하되 같은 규칙으로 이유를 적는다.

## 5. 저장소 구조

```
hey-car/
  settings.gradle.kts          includeBuild("shared/android"), 모듈 목록
  app/, core/{designsystem,model,session,vehicle}/, feature/{trip,settings}/
  shared/                      hey-shared 서브모듈 (태그 고정): packages/{tokens,protocol,copy}, android/ (ai.hey:tokens-compose)
  docs/                        architecture.md(이 문서), components.md(부품 목록)
  check.sh                     로컬 점검 한 번에 (아래 7절)

분리 전 모노레포 구조(참고):
hey-taxi/
  package.json                 bun workspaces
  .storybook/                  루트 Storybook (웹 부품 + 승객 화면 + 차량 갤러리)
  storybook/                   소개·규칙·검수판 MDX, car/ (Paparazzi 스냅숏 이야기, 생성)
  packages/tokens/             hey.tokens.json, build.mjs, dist/
  packages/protocol/           src/{types,catalog,config,index}.ts, fixtures/*.json
  packages/copy/               src/{en,ja,zh-Hans,ko}.ts, src/index.ts (t, Lang)
  packages/ui-web/             src/components/<Name>/{<Name>.tsx,<Name>.module.css,<Name>.stories.tsx}, src/index.ts
  apps/relay-server/           src/, test/
  apps/passenger-web/          src/{main.tsx,App.tsx,routes.tsx,store/,transport/,screens/<Screen>/<Screen>.tsx,<Screen>.stories.tsx}
  apps/car-android/            settings.gradle.kts, app/, core/{designsystem,model,session,vehicle}/, feature/{trip,settings}/
  docs/                        architecture.md(이 문서), components.md(부품 목록)
```

## 6. 차량 앱 구조

- `:core:vehicle` — `VehicleGateway` 인터페이스: `drivingState: StateFlow<DrivingStatus>`, `speak(textKo, priority)`, `startRoute(place)` + `routeState: StateFlow<RouteStartState>` + `cancelRoute()` + `openNavi()`, `requestTrip(place): TripInfo?`, `retrySpeech()`, `retryNavigation()`, `gear`, `doors`. 구현 `PleosVehicleGateway`(Pleos SDK: TextToSpeech ON_DEVICE `Locale.KOREA`, NaviHelper `requestRoute`/`requestTrip`, `CarUxRestrictionsManager`, Vehicle Gear·Door)와 `FakeVehicleGateway`(Preview·시험·Pleos가 아닌 기기용). 화면은 이 인터페이스만 안다.
  - 결과는 참·거짓이 아니라 타입이다. 음성은 `SpeechResult.Delivered` / `Dropped(reason)`(Blank·QueueOverflow·EngineUnavailable·EngineError), 경로는 `RouteStartState`(아래). 서비스가 없을 때 성공으로 답하지 않는다.
  - 주행 상태는 `DrivingStatus.Unknown | Parked | Driving`이다. `Unknown`(차 서비스가 아직 답하지 않았거나 사라짐)은 어디서나 `Driving`과 똑같이 다룬다. 게이트웨이 기본값은 `Unknown`이다.
  - 내비 초기화 실패·요청 없는 내비 오류·음성 엔진 없음은 `VehicleEvent.NavigationUnavailable` / `SpeechDropped`로 올라가 D1 배너와 "다시 시도"(`retryNavigation`/`retrySpeech`)로 이어진다.
- `:core:session` — OkHttp WebSocket 클라이언트, 재연결(1·2·4·8·15초), ack, `TripRepository`(`StateFlow<CarUiState>`), 음성 큐(`speech.*` 정책).
  - 상태 기계는 하나다. `phaseOf(view, driving, local): CarPhase`가 스냅숏·주행 상태·로컬 플래그에서 화면 단계를 계산하고, 화면 선택은 그 결과만 본다(`when`은 빠짐없이, `else` 없음). 새 `TripState`를 더하면 이 함수가 컴파일되지 않는다.
  - 주행 잠금과 상태별 명령은 조건문이 아니라 타입이다. 손이 필요한 명령은 `ParkedCommands`에만 있고, 그 값은 저장소만 만들어(생성자가 모듈 안으로 닫혀 있다) `CarUiState.parked: ParkedCommands?`로만 내준다. 주행·미확인 단계에서는 `null`이라 호출할 대상이 없다. 정차 단계(`CarPhase.Parked`)는 `enabled: Set<CarCommand>`를 들고 있다: 그 상황에서 릴레이가 받는 명령만이다. `BoardingCard` 하나이던 단계는 `AwaitingCard`(CONNECTED)·`CardSent`(CARD_SENT)·`InTrip`(IN_TRIP)으로 나뉘어, CONNECTED에서는 경로 시작·하차 제안·도착이, IN_TRIP에서는 재확인이 켜지지 않는다. 질문이 있으면 CONNECTED·CARD_SENT·IN_TRIP·ARRIVED 어디서나 D6(`Requests`)이 열리고, `trip.end`는 D3·D6·D7 어디서나 확인창을 거쳐 보낼 수 있다. 화면은 버튼을 `enabled`에서만 켜고, 저장소의 각 명령도 지금 단계의 `enabled`를 다시 본다. 단계별 `enabled`가 `docs/transitions.json`(hey-relay의 `TRANSITIONS`에서 생성)의 부분집합인지는 `CarPhaseTransitionsTest`가 확인한다. 이 파일은 Gradle이 `HEY_RELAY_DIR`(기본 `../hey-relay`)의 것을 시험 자원으로 복사하고, 없으면 이 저장소에 커밋된 사본을 쓴다. 시험용 진입점은 운영 클래스에 없다: 저장소는 `RelayTransport` 생성자 인자로 연결을 받고, 시험은 기록용 가짜를 넣어 같은 리스너로 메시지를 흘린다.
  - 하차 제안은 `by`로 갈라진 두 타입이다: `DriverProposal`(상태 `pending|ok|elsewhere|unsure|superseded`)과 `PassengerPin`(`pending|accepted|declined|superseded`). 한 타입에서 다른 쪽 상태를 표현할 수 없다.
  - 합류 요청 남은 시간은 기기 시계가 아니라 릴레이 시계로 센다. 저장소가 스냅숏 봉투의 `ts`로 `clockOffsetMs`를 갱신하고(1초 안쪽 흔들림은 무시, 승객 웹의 `nextClockOffset`과 같은 규칙), D2는 `expiresAt - serverNow(...)`를 쓴다.
  - 명령 상태는 운행 하나에 속한다: 스냅숏의 `tripId`가 바뀌면 `commands`와 봉투 대응표를 비운다. 버튼 없이 보낸 메시지(읽음 보고 등)가 거절돼도 배너를 띄우지 않는다.
  - 버튼 하나의 진행은 `Map<CommandKey, CommandState>` 하나에 있다(`Idle`은 항목 없음). 해제 규칙은 셋뿐이다: ① 그 명령의 결과가 스냅숏에 보임 ② 릴레이가 그 봉투(`ref`)를 거절함 ③ 20초 안전 시간 초과. 무관한 스냅숏은 버튼을 풀지 않는다.
- `:core:model` — 프로토콜 Kotlin 타입(kotlinx.serialization), `HeyConfig`. 서버와 주고받는 `DrivingState`(`PARKED`/`DRIVING`)는 프로토콜 값이고, 앱 안의 `DrivingStatus`와 다른 타입이다.
- **주행 중에는 화면이 없다 — 음성 전용 (에뮬레이터 실측 2026-11-12, `.review/emu/16-driving.png`).** 주행 중에는 Pleos 시스템이 `distractionOptimized=true`인 서드파티 앱 위에도 "주행 중에는 사용할 수 없습니다" 화면을 덮는다. 그래서 D5(주행 화면)는 지금 빌드에서 승객·기사 누구에게도 보이지 않는다. 코드는 그대로 둔다(나중 빌드가 허용하면 보이고, 해롭지 않다). 대신 제품은 음성만으로 성립해야 한다: 승객 요청은 `say`로 TTS가 읽고, 연결 요청·승객 핀·도착도 음성으로 알리며, 주행 중에 꼭 봐야 하는 것은 없다. 눌러야 하는 것(승인·경로 시작·내릴 곳 확인·종료)은 모두 정차 중 D2·D3·D4·D6·D7에 있다. 새 기능은 "주행 중에 보이는 것"에 기대지 않는다.
  - **주행 중 들려야 하는 것은 표로 못 박았다.** 연결 요청·일행 합류·탑승 카드 도착과 변경·승객 핀·하차 제안 응답(좋아요/다른 곳/모르겠어요)·프리셋과 직접 입력 요청(질문 포함)·`stopHere`·승객 나감은 릴레이의 `say`가 읽는다(`RelayIntegrationTest.everyEventTheDriverMustKnowIsHeard`가 실제 릴레이로 하나씩 확인, `TripRepositoryTest.everyRelaySayIsSpoken`이 주행·정차·미확인 어디서나 읽히는지 확인). 릴레이에 `say`가 없는 도착(`ARRIVED`)은 차가 `LocalSpeech.ARRIVED`로 직접 읽는다(`everyTripStateTransitionSpeaksLocallyExactlyWhenTheRelayHasNoSentenceForIt`가 모든 상태 쌍을 돌려 도착 전이에서만 읽는지 확인). "도착 제안"이라는 별도 사건은 릴레이에도 차에도 없다.
  - 설정 저장은 `SaveResult.Saved | Failed(reason)`를 돌려주고, 실패하면 D8이 배너를 띄운다(값은 이번 실행에는 계속 쓴다).
- **경로 시작은 명시적 상태다 (`RouteStartState`, 실측 2026-11-12).** 내비(Navi42)는 `requestRoute`를 받으면 경로 미리보기를 띄우고 사람이 "안내 시작"을 눌러야 `onRouteStarted`가 온다. 한 번도 열린 적 없으면 `navi not ready`로 요청이 버려진다. 그래서 `Requested`(내비가 답할 때까지 최대 15초 기다리며 3초마다 `getRouteStateInfo`/`getCurrentLocationInfo`로 깨운다; 안 오면 `Failed(NAVI_NOT_READY)`) → `AwaitingNaviConfirm`(요청 전달됨, D3가 "내비에서 안내 시작을 눌러 주세요"와 취소를 보임) → `Started`(`onRouteStarted`) | `Failed(code)`(`onError` 코드, 전달 예외 `NAVI_ERROR`, 안내 시작을 3분 안에 안 누르면 `NAVI_CONFIRM_TIMEOUT`)이다. 확인 대기 중에는 짧은 시간 초과 실패가 없다. 취소하면 `Idle`이고 그 뒤에 오는 `onRouteStarted`는 무시한다. 릴레이에는 `Started`일 때만 `route.started`, `Failed`일 때만 `route.failed`를 한 번씩 보낸다(대기 중에는 아무것도 보내지 않는다). `NAVI_NOT_READY`이면 D3가 "내비 열기"(`PackageManager.getLaunchIntentForPackage("ai.umos.maps.android.navigation.app")`)와 "다시 시도"를 보인다. 상태 기계는 SDK에 닿지 않는 `RouteStartMachine`이고 `RouteStartMachineTest`가 검증한다.
- 시계 글자는 시스템 기본 시간대(`ZoneId.systemDefault()`)로 쓴다(`clockZone()`). 앱이 시간대를 따로 고르지 않는다. 미리보기·스냅숏만 `LocalClockZone`으로 Asia/Seoul을 고정한다.
- `:core:designsystem` — Compose 부품, `HeyTheme`, Paparazzi 시험. 토큰(`HeyTokens`)은 `ai.hey:tokens-compose`(hey-shared `android/`, includeBuild)가 준다.
- `:feature:trip` — D1~D7 화면. 화면은 `CarUiState.phase`로 갈라지고, 정차 단계의 버튼은 그 단계의 `enabled`에서만 켜진다. 손이 필요한 호출은 `state.parked?.…`이다. 시험·미리보기용 빈 명령은 `CarCommands` 쪽 `NoTripCommands`(debug)뿐이다. `:feature:settings` — D8.
- `:app` — `MainActivity`(`distractionOptimized`), 내비게이션, DI(수동 생성자 주입), 포그라운드 서비스.

빌드: AGP 8.11.2, Kotlin 2.1.0, Compose BOM 2025.12.01, Paparazzi 2.0.0-alpha02, Gradle 8.13, JDK 17, compileSdk 36, minSdk 28, targetSdk 34. Pleos Maven `https://nexus-playground.pleos.ai/repository/maven-releases/`, `android.car.jar`는 `compileOnly`.

## 7. 실행과 검증

- 서버: `bun run --cwd apps/relay-server dev` (포트 8787), 시험 `bun test`.
- 승객 웹: `bun run --cwd apps/passenger-web dev` (포트 5187), 서버 주소는 `VITE_RELAY_URL`.
- Storybook: 루트 `bun run sb`(6106), 정적 빌드 `bun run sb:build`.
- 차량 앱: 이 저장소 루트에서 `./check.sh`(= `nice -n 19 ./gradlew --max-workers=4 :app:assembleDebug check :feature:trip:verifyPaparazziDebug :feature:settings:verifyPaparazziDebug :core:designsystem:verifyPaparazziDebug`). 스냅숏 기록은 `./gradlew recordPaparazziDebug`. 클론은 `git clone --recurse-submodules`(또는 `git submodule update --init`). 중계 서버 통합 시험은 환경 변수 `HEY_RELAY_DIR`(기본 `../hey-relay`)의 relay를 `bun`으로 띄우며, 없으면 건너뛰고 이유를 출력한다. darren-vm 부하를 줄이기 위해 `org.gradle.workers.max=4`.
- 에뮬레이터는 맥북 `darren-mbp-14`에서만 띄운다(`~/pleos/emu-start.sh`, adb `emulator-5600`).
- 비밀값(번역 API 키 등)은 저장소에 넣지 않는다. 서버는 600 권한 파일 경로를 환경 변수 `HEY_SECRETS_FILE`로 받는다.

## 8. 메시지와 상태 (서버 정본)

2.2의 상태 흐름을 메시지 단위로 옮긴 표다. `src/state.ts`의 `TRANSITIONS`가 이 표를 그대로 옮긴 것이고, `test/state-machine.test.ts`가 두 표가 같은지 확인한다. 표에 없는 (상태, 메시지) 조합은 서버가 거부한다: 종료된 운행은 `TRIP_ENDED`, 그 밖은 `BAD_REQUEST`. 표에 없는 메시지 타입은 봉투 검사에서 `BAD_REQUEST`다.

### 8.1 상태별로 받는 메시지

| 상태 | 받는 메시지 |
|---|---|
| `REQUESTED` | `join.approve`, `join.decline`, `driving.state`, `trip.end`, `trip.leave`, `device.lang` |
| `CONNECTED` | `card.submit`, `card.update`, `driving.state`, `request.send`, `request.spoken`, `reply.send`, `trip.end`, `trip.leave`, `device.lang` |
| `CARD_SENT` | `card.submit`, `card.update`, `card.recheck`, `route.started`, `route.failed`, `dropoff.propose`, `dropoff.respond`, `dropoff.decide`, `request.send`, `request.spoken`, `reply.send`, `driving.state`, `trip.arrive`, `trip.end`, `trip.leave`, `device.lang` |
| `IN_TRIP` | `card.submit`, `card.update`, `route.started`, `route.failed`, `trip.progress`, `dropoff.propose`, `dropoff.respond`, `dropoff.decide`, `request.send`, `request.spoken`, `reply.send`, `driving.state`, `trip.arrive`, `trip.end`, `trip.leave`, `device.lang` |
| `ARRIVED` | `request.send`, `request.spoken`, `reply.send`, `driving.state`, `trip.end`, `trip.leave`, `device.lang` |
| `ENDED` | `driving.state` |
| `DECLINED` | `driving.state` |
| `EXPIRED` | `driving.state` |

`driving.state`는 운행 상태를 바꾸지 않는 차량 신호라 모든 상태에서 받는다. `join.approve`는 `REQUESTED`에서만, `card.recheck`는 `CARD_SENT`에서만 받는다(2.2의 화살표와 같다). 기사님은 종료되지 않은 모든 상태(`REQUESTED`~`ARRIVED`)에서 `trip.end`를 보낼 수 있다.

이 표는 `docs/transitions.json`(상태 → `{ type, role }[]`, `role`은 8.2의 값)으로도 내보낸다. `bun run export:transitions`가 `TRANSITIONS`에서 만들고, 시험이 커밋된 파일과 내보내기가 같은지 확인한다. 차량 앱과 승객 웹은 이 파일을 복사해, 화면이 켜 두는 명령이 표의 부분집합인지 자기 시험에서 확인한다. 승객 언어의 하차 지점 이름은 서버가 만든 `labelLocal` 하나뿐이고 클라이언트는 이름을 다시 만들지 않는다.

### 8.2 보낼 수 있는 쪽

`CLIENT_MESSAGE_ROLES`(hey-protocol)가 정본이고 서버는 이 표 하나로 역할을 본다. 대표가 아닌 기기의 대표 전용 메시지는 `NOT_LEAD`, 그 밖의 역할 위반은 `BAD_REQUEST`다. 일행은 `passenger` 값인 메시지(`request.send`, `trip.leave`, `device.lang`)만 보낼 수 있다. 현재 값은 넷이다.

| 값 | 보낼 수 있는 쪽 |
|---|---|
| `car` | 차량(`driverToken`)만 |
| `lead` | 대표 승객만 |
| `passenger` | 대표·일행 모두 |
| `car-or-lead` | 차량 또는 대표 승객 |
