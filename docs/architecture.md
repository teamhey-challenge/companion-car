# hey-car 개발 계약 (2026-11-12 초판)

> **2026-11-14 DV-07 단계 1:** 이 문서의 HEY 서버 계약·`TripRepository`·`CarPhase`·`RelayLink`·`transitions.json` 설명은 낡았다(코드는 지웠다). 지금 차량 앱의 서버 연결은 `core/session`의 `TalkLink`(등록 → 세션 → 티켓 WebSocket)와 `TalkRepository`(한 턴: Pleos STT → `/v1/talk` → 응답)이고, 프로토콜 정본은 `companion-shared` `packages/protocol`(v0.2.0)이다. 주행 중에도 듣기와 턴은 끊지 않는다(AC-4.4-18, AC-4.5-07). 차량 설정은 `serverUrl` 하나다. 이 문서의 나머지는 화면 작업(C1–C8) 때 다시 쓴다.

이 문서는 **hey-car가 소유하는 것**만 적는다: 차량 디자인 시스템 규칙(4절), 이 저장소의 구조(5절), 차량 앱 구조(6절), 실행과 검증(7절). 제품·프로토콜·서버 계약(범위, 기획자 답변 기본값, 상태 흐름, REST·WebSocket, ID 목록, 정책값, 서버 상태 표)은 사본을 두지 않고 `hey-relay/docs/architecture.md`가 정본이다. 웹 디자인 시스템 규칙과 웹 부품 목록은 `hey-passenger-web`, 부품 목록(차량)은 이 저장소의 `docs/components.md`다. 절 번호는 분리 전 `hey-taxi` 문서와 같게 유지했고 코드 주석이 그 번호를 쓴다. 제품 설명은 기획 저장소 `darren-pleos-challenge/pleos-challenge`의 `proposal/hey/`에 있다. 바꿀 때는 이 문서를 먼저 고치고 코드를 맞춘다.

## 0~3. 제품·프로토콜 계약 (이 저장소 밖)

| 절 | 내용 | 정본 |
|---|---|---|
| 0 | 범위와 구성 | `hey-relay/docs/architecture.md` §0 |
| 1 | 기획자 답변 기본값 (Q1~Q30) | 같은 문서 §1 |
| 2 | 상태와 흐름 (차량·QR, 운행 상태, 하차 제안, 요청과 답장·음성 우선순위) | 같은 문서 §2 |
| 3 | REST·WebSocket(재연결 1·2·4·8·15초, ack 포함)·ID 목록·정책값 | 같은 문서 §3, 타입·ID·정책값은 `hey-shared`의 `packages/protocol/src` |
| 8 | 상태별 받는 메시지·보낼 수 있는 쪽 | 같은 문서 §8, 표는 relay의 `docs/transitions.json` |

이 저장소가 서버 계약을 쓰는 방식:

- 차량 쪽 정책값은 `core/model/HeyConfig.kt`(`hey-shared`의 `packages/protocol/src/config.ts`를 옮긴 것)에만 둔다. 프로토콜 Kotlin 타입은 `:core:model`이 손으로 옮기고, `shared/packages/protocol/fixtures/*.json`을 시험이 읽어 같은 모양인지 확인한다.
- 승객 요청 음성은 relay의 `say`로 온다. 큐·간격·중복 제거(`speech.*`)는 `:core:vehicle`의 `SpeechQueue`가 정책값대로 지킨다.
- 차량은 하차 지점을 기사 제안으로만 보낸다(`dropoff.propose`). 승객 핀은 없다.
- 모든 답장(버튼·음성)은 정차 중에만 보낸다. `answer` 묶음은 열린 질문이 있을 때만 켜고 `requestId`가 없으면 relay가 가장 오래된 열린 질문에 붙인다(차량 UI도 같은 규칙).
- 도착(`trip.arrive`)은 내비 `onDestinationArrived`를 받을 때, 또는 이 이벤트가 없을 때를 대비해 기사님이 차량 화면의 "도착" 버튼(정차 중)을 누를 때 보낸다. 정확한 조건은 relay §2.4.
- 단계별 `enabled` 명령이 relay 표의 부분집합인지 `CarPhaseTransitionsTest`가 확인한다(6절).

## 4. 디자인 시스템 규칙 (예외 없음) — 차량

근거: 스킬 `product-foundations` 2절, 사용자 지시 "디자인 시스템을 구축하고 그 컴포넌트만 사용해서 개발, 예외 없음"(2026-11-12). 웹 쪽 규칙(CSS 변수 이름, `className` 금지, CSS 리터럴, Storybook 이야기, 시트 등)은 `hey-passenger-web/docs/architecture.md` §4가 소유한다. 번호는 분리 전과 같게 두었고, 이 저장소에 해당이 없는 번호는 비워 뒀다.

1. 토큰 정본은 `shared/packages/tokens/hey.tokens.json`(hey-shared) 하나다. 차량 앱은 `HeyTokens.kt`를 복사하지 않고 `includeBuild("shared/android")`로 `ai.hey:tokens-compose`에 의존한다. 생성(`bun run tokens`)은 `hey-shared` README가 소유한다. 생성 파일은 손으로 고치지 않는다.
3. 간격은 4·8·16·24·32만 쓴다(`HeyGap.G4…G32`). 부품은 바깥 여백·폭·위치를 갖지 않는다. 간격은 부모 컨테이너 `VStack`/`HStack`이 `gap`으로 정한다.
7. 차량 앱 `:feature:*` 모듈은 `androidx.compose.material3`·`androidx.compose.foundation`에 의존하지 않는다(Gradle 의존성에 없음). 그래서 `Text`·`Button`·`Column`을 직접 쓸 수 없고 `:core:designsystem`의 `HeyText`·`HeyButton`·`VStack`만 쓸 수 있다. `Color(0x…)`, `.dp` 리터럴은 `:core:designsystem` 밖에서 쓰지 않는다(`./gradlew checkDesignSystemUsage`가 막는다).
8. 화면 문구는 카탈로그에서만 온다: 차량 앱은 `strings.xml`(`stringResource`)과 서버가 준 한국어 문장, D1 QR 안내의 6개 언어 문장은 `hey-shared`의 `car.scanQr`에서 빌드 때 생성한다. 정책 설명이나 개발 메모를 화면 문구에 넣지 않는다.
9. 모든 차량 부품은 Paparazzi 스냅숏에 상황별 이야기가 있어야 화면에서 쓸 수 있다. 상황: 기본, 긴 글(띄어쓰기 없는 한국어 120자 이상), 빈 값, 항목 0·1·많음, 비활성, 오류, 로딩, 라이트·다크. 예시 데이터는 실제 모양의 긴 값이다.
10. 누를 수 있는 요소는 겹치지 않는다. 누름 면은 차량 76dp 이상(주 동작 96dp).
12. 차량 앱의 데이터는 `TripRepository`의 `StateFlow<CarUiState>` 하나다. 재조회·재연결이 화면 위치·입력 중인 값을 바꾸지 않는다.
15. 차량 Paparazzi 스냅숏(JVM 렌더)은 로컬 기록을 허용한다. 기준 이미지를 바꾸는 커밋에는 `docs/diffs.md`에 바뀐 이유를 한 줄씩 적는다.

## 5. 저장소 구조

```
hey-car/
  settings.gradle.kts          includeBuild("shared/android"), 모듈 목록
  app/, core/{designsystem,model,session,vehicle}/, feature/{trip,settings}/
  shared/                      hey-shared 서브모듈 (태그 고정): packages/{tokens,protocol,copy}, android/ (ai.hey:tokens-compose)
  docs/                        architecture.md(이 문서), components.md(D 화면·차량 부품), diffs.md(스냅숏 변경 이유)
  check.sh                     로컬 점검 한 번에 (7절)
```

다른 저장소의 구조 지도는 `hey-relay/docs/architecture.md` §5가 정본이다.

## 6. 차량 앱 구조

- `:core:vehicle` — `VehicleGateway` 인터페이스: `drivingState: StateFlow<DrivingStatus>`, `speak(textKo, priority)`, `startRoute(place)` + `routeState: StateFlow<RouteStartState>` + `cancelRoute()` + `openNavi()`, `requestTrip(place): TripInfo?`, `retrySpeech()`, `retryNavigation()`, `gear`, `doors`. 구현 `PleosVehicleGateway`(Pleos SDK: TextToSpeech ON_DEVICE `Locale.KOREA`, NaviHelper `requestRoute`/`requestTrip`, `CarUxRestrictionsManager`, Vehicle Gear·Door)와 `FakeVehicleGateway`(Preview·시험·Pleos가 아닌 기기용). 화면은 이 인터페이스만 안다.
  - 결과는 참·거짓이 아니라 타입이다. 음성은 `SpeechResult.Delivered` / `Dropped(reason)`(Blank·QueueOverflow·EngineUnavailable·EngineError), 경로는 `RouteStartState`(아래). 서비스가 없을 때 성공으로 답하지 않는다.
  - 주행 상태는 `DrivingStatus.Unknown | Parked | Driving`이다. `Unknown`(차 서비스가 아직 답하지 않았거나 사라짐)은 어디서나 `Driving`과 똑같이 다룬다. 게이트웨이 기본값은 `Unknown`이다.
  - 내비 초기화 실패·요청 없는 내비 오류·음성 엔진 없음은 `VehicleEvent.NavigationUnavailable` / `SpeechDropped`로 올라가 D1 배너와 "다시 시도"(`retryNavigation`/`retrySpeech`)로 이어진다.
- `:core:session` — OkHttp WebSocket 클라이언트, 재연결(1·2·4·8·15초), ack, `TripRepository`(`StateFlow<CarUiState>`), 음성 큐(`speech.*` 정책).
  - 상태 기계는 하나다. `phaseOf(view, driving, local): CarPhase`가 스냅숏·주행 상태·로컬 플래그에서 화면 단계를 계산하고, 화면 선택은 그 결과만 본다(`when`은 빠짐없이, `else` 없음). 새 `TripState`를 더하면 이 함수가 컴파일되지 않는다.
  - 주행 잠금과 상태별 명령은 조건문이 아니라 타입이다. 손이 필요한 명령은 `ParkedCommands`에만 있고, 그 값은 저장소만 만들어(생성자가 모듈 안으로 닫혀 있다) `CarUiState.parked: ParkedCommands?`로만 내준다. 주행·미확인 단계에서는 `null`이라 호출할 대상이 없다. 정차 단계(`CarPhase.Parked`)는 `enabled: Set<CarCommand>`를 들고 있다: 그 상황에서 릴레이가 받는 명령만이다. `BoardingCard` 하나이던 단계는 `AwaitingCard`(CONNECTED)·`CardSent`(CARD_SENT)·`InTrip`(IN_TRIP)으로 나뉘어, CONNECTED에서는 경로 시작·하차 제안·도착이, IN_TRIP에서는 재확인이 켜지지 않는다. 질문이 있으면 CONNECTED·CARD_SENT·IN_TRIP·ARRIVED 어디서나 D6(`Requests`)이 열리고, `trip.end`는 D3·D6·D7 어디서나 확인창을 거쳐 보낼 수 있다. 화면은 버튼을 `enabled`에서만 켜고, 저장소의 각 명령도 지금 단계의 `enabled`를 다시 본다. 단계별 `enabled`가 `docs/transitions.json`(hey-relay의 `TRANSITIONS`에서 생성)의 부분집합인지는 `CarPhaseTransitionsTest`가 확인한다. 이 파일은 Gradle이 `HEY_RELAY_DIR`(기본 `../hey-relay`)의 것을 시험 자원으로 복사하고, 없으면 이 저장소에 커밋된 사본을 쓴다. 시험용 진입점은 운영 클래스에 없다: 저장소는 `RelayTransport` 생성자 인자로 연결을 받고, 시험은 기록용 가짜를 넣어 같은 리스너로 메시지를 흘린다.
  - 하차 제안은 기사만 한다(3.0.0). `DropoffProposal` 한 타입이고 상태는 `pending|ok|elsewhere|unsure|superseded`다. `elsewhere`는 "기사님이 다른 곳을 제안해 달라"는 뜻이라 D4에 "승객이 다른 곳을 원해요"(`hey_proposal_elsewhere`)로 보이고, 릴레이가 같은 뜻을 음성으로 읽는다. 승객 핀·`PassengerPinCard`·`DecidePin`·`CommandKey.Pin`·`pendingPins`는 없다.
  - **답장은 두 묶음이다(`QuickReplyId.set`, 정본 `QUICK_REPLY_META`).** 답장(yes·cannot·moment): 열린 질문이 있을 때만 D6 위쪽에 보이고 그 질문에 답한다(`requestId` 필수). 기사님이 먼저(stopHere·fiveMin·cardOk): D6의 "먼저 말하기" 단추를 눌러 펼치며 질문 없이 언제든(정차 중) 보낸다(`requestId` 없음). D3 오른쪽 열의 "답장하기"(`Panel.Reply`)가 질문 없이 D6을 연다. 같은 `QuickReplyGroup` 부품 하나가 두 묶음을 그린다.
  - **음성 답장(Q15, 정차 중에만).** D6의 "음성으로 답하기"는 `VoiceInput`(Pleos SpeechToText ON_DEVICE, `Locale.KOREA` — `ko`만 쓰면 실패, `PleosVoiceInput`)을 한 번 듣는다: `Idle → (권한 없으면 NeedsPermission → MainActivity가 RECORD_AUDIO 요청 → voicePermissionResult) → Listening → Confirm(textKo) → 보내기 → Sent`. 인식된 문장은 `VoiceConfirmDialog`로 보여 주고 기사님이 "보내기"를 눌러야만 `reply.send {kind:"voice", textKo(1~100자)}`가 나간다(릴레이가 승객 언어로 번역, 실패하면 같은 `ref`의 `TRANSLATE_FAILED`). 차가 움직이면(주행·미확인) 듣던 것을 취소하고 인식 문장을 버린다: `startVoiceReply`는 `ParkedCommands`에만 있어 주행 중에는 호출할 대상이 없고, 버튼 답장도 같다. 주행 중 음성 출력만 남는다. 매니페스트: `pleos.car.permission.STT_SERVICE`, `android.permission.RECORD_AUDIO`(런타임 권한).
  - **시연용 인식기(디버그·`releaseE2e`만).** 이 빌드도 기본은 실제 Pleos SpeechToText(에뮬레이터의 호스트 마이크)로 듣는다. `app/src/e2e`의 `DemoVoice`가 `InjectableVoiceInput`으로 감싸 adb로만 문장을 주입한다(번들 문장 없음): `adb shell am broadcast -a ai.hey.car.DEBUG_VOICE -p ai.hey.car --es text "<한국어 문장>"`은 바로 다음 듣기 한 번에만 적용되고(빈 문자열이면 그 듣기가 "알아듣지 못했어요"), `--ez sticky true`를 더하면 매 듣기에 같은 문장을 쓴다(반복하는 E2E용). `DEBUG_VOICE_REAL` 브로드캐스트로 실제 마이크로 되돌린다. 실제 `release`에는 이 소스가 없고 `src/release`의 no-op 쌍둥이만 있다.
  - 합류 요청 남은 시간은 기기 시계가 아니라 릴레이 시계로 센다. 저장소가 스냅숏 봉투의 `ts`로 `clockOffsetMs`를 갱신하고(1초 안쪽 흔들림은 무시, 승객 웹의 `nextClockOffset`과 같은 규칙), D2는 `expiresAt - serverNow(...)`를 쓴다.
  - 명령 상태는 운행 하나에 속한다: 스냅숏의 `tripId`가 바뀌면 `commands`와 봉투 대응표를 비운다. 버튼 없이 보낸 메시지(읽음 보고 등)가 거절돼도 배너를 띄우지 않는다.
  - 버튼 하나의 진행은 `Map<CommandKey, CommandState>` 하나에 있다(`Idle`은 항목 없음). 해제 규칙은 셋뿐이다: ① 그 명령의 결과가 스냅숏에 보임 ② 릴레이가 그 봉투(`ref`)를 거절함 ③ 20초 안전 시간 초과. 무관한 스냅숏은 버튼을 풀지 않는다.
- `:core:model` — 프로토콜 Kotlin 타입(kotlinx.serialization), `HeyConfig`. 서버와 주고받는 `DrivingState`(`PARKED`/`DRIVING`)는 프로토콜 값이고, 앱 안의 `DrivingStatus`와 다른 타입이다.
- **주행 중 화면 조건: 태스크 루트는 `distractionOptimized=true`인 `MainActivity`여야 한다.** 비-DO 액티비티(`PreviewActivity`, 시스템 대화상자)가 태스크 루트가 되면 Pleos 홈이 주행 중 화면을 덮을 수 있으므로, 앱은 자기 독 아이콘으로 연 `MainActivity`를 루트로 쓴다. 그래도 제품은 화면 없이도 성립해야 한다: 요청은 음성으로 알리고, 주행 중에 꼭 봐야 하는 것은 없다. 눌러야 하는 것은 모두 정차 중 화면에 있다. 주행 중에는 버튼 입력과 음성 입력이 잠긴다(명령이 없고 마이크도 닫힌다). 새 기능은 "주행 중에 보이는 것"에 기대지 않는다. (플랫폼 동작 관찰 기록은 비공개 기획 저장소에 둔다.)
  - **주행 중 들려야 하는 것은 표로 못 박았다.** 연결 요청·일행 합류·탑승 카드 도착과 변경(조건: 만나는 입구·실내 온도·조용히·음악)·하차 제안 응답(좋아요/다른 곳을 원해요/모르겠어요)·프리셋과 직접 입력 요청(질문 포함)·`stopHere`·승객 나감은 릴레이의 `say`가 읽는다(`RelayIntegrationTest.everyEventTheDriverMustKnowIsHeard`가 실제 릴레이로 하나씩 확인). 릴레이에 `say`가 없는 세 사건은 차가 `LocalSpeech`로 직접 읽는다: 도착(`ARRIVED`), 운행 끝(`ENDED`, 사유와 무관 — "운행을 마쳤어요."), 승인 전에 사라진 연결 요청(`JOIN_EXPIRED`, 기사가 직접 처리한 요청은 제외). hey-shared에 이 문장들이 없어 차 로컬 문자열이다. `TripRepositoryTest`의 `aTripThatEndsForAnyReasonSpeaksTheEndOnce`·`arrivalSpeaksTheArrivalAndNothingElseSpeaksOnPlainTransitions`·`aJoinRequestThatLapsesIsSpokenButOneTheDriverDecidedIsNot`가 확인한다. "도착 제안"이라는 별도 사건은 릴레이에도 차에도 없다.
  - 설정 저장은 `SaveResult.Saved | Failed(reason)`를 돌려주고, 실패하면 D8이 배너를 띄운다(값은 이번 실행에는 계속 쓴다).
- **경로 시작은 명시적 상태다 (`RouteStartState`).** 내비는 `requestRoute`를 받으면 경로 미리보기를 띄우고 사람이 "안내 시작"을 눌러야 `onRouteStarted`가 온다. 내비가 준비되지 않으면 요청이 버려질 수 있다. 그래서 `Requested`(내비가 답할 때까지 최대 15초 기다리며 3초마다 `getCurrentLocationInfo`로 깨운다; 안 오면 `Failed(NAVI_NOT_READY)`) → `AwaitingNaviConfirm`(요청 전달됨, D3가 "내비에서 안내 시작을 눌러 주세요"와 취소를 보임) → `Started`(`onRouteStarted`) | `Failed(code)`(`onError` 코드, 전달 예외 `NAVI_ERROR`, 안내 시작을 3분 안에 안 누르면 `NAVI_CONFIRM_TIMEOUT`)이다. 요청이 아직 나가지 않은 `Requested` 동안 온 무관한 내비 오류(예: `CAUSE_LOW_BATTERY`)는 이 시도의 실패로 세지 않는다. 확인 대기 중에는 짧은 시간 초과 실패가 없다. 요청이 전달된 뒤 `Started` 없이 끝나면(취소·3분 초과·`onError`·새 운행·릴레이의 목적지/합의 지점 변경) 상태 기계가 `NaviHelper.cancelRoute()`로 내비의 미리보기를 닫는다(`abort`). 시도는 요청한 `Place`에 묶여서, 대기 중 `routeDestination()`이 바뀐 스냅숏이 오면 같은 방식으로 취소한다. 취소하면 `Idle`이고 그 뒤에 오는 `onRouteStarted`는 무시한다. 릴레이에는 `Started`일 때만 `route.started`, `Failed`일 때만 `route.failed`를 한 번씩 보낸다(대기 중에는 아무것도 보내지 않는다). `NAVI_NOT_READY`이면 D3가 "내비 열기"와 "다시 시도"를 보인다. 상태 기계는 SDK에 닿지 않는 `RouteStartMachine`이고 `RouteStartMachineTest`가 검증한다.
- 시계 글자는 시스템 기본 시간대(`ZoneId.systemDefault()`)로 쓴다(`clockZone()`). 앱이 시간대를 따로 고르지 않는다. 미리보기·스냅숏만 `LocalClockZone`으로 Asia/Seoul을 고정한다.
- `:core:designsystem` — Compose 부품, `HeyTheme`, Paparazzi 시험. 토큰(`HeyTokens`)은 `ai.hey:tokens-compose`(hey-shared `android/`, includeBuild)가 준다.
- `:feature:trip` — D1~D7 화면. 화면은 `CarUiState.phase`로 갈라지고, 정차 단계의 버튼은 그 단계의 `enabled`에서만 켜진다. 손이 필요한 호출은 `state.parked?.…`이다. 시험·미리보기용 빈 명령은 `CarCommands` 쪽 `NoTripCommands`(debug)뿐이다. `:feature:settings` — D8.
- `:app` — `MainActivity`(`distractionOptimized`), 내비게이션, DI(수동 생성자 주입), 포그라운드 서비스.

빌드: AGP 8.11.2, Kotlin 2.1.0, Compose BOM 2025.12.01, Paparazzi 2.0.0-alpha02, Gradle 8.13, JDK 17, compileSdk 36, minSdk 28, targetSdk 34. Pleos Maven `https://nexus-playground.pleos.ai/repository/maven-releases/`, `android.car.jar`는 `compileOnly`.

## 7. 실행과 검증

- 차량 앱: 이 저장소 루트에서 `./check.sh`(= `nice -n 19 ./gradlew --max-workers=4 :app:assembleDebug check :feature:trip:verifyPaparazziDebug :feature:settings:verifyPaparazziDebug :core:designsystem:verifyPaparazziDebug`). 스냅숏 기록은 `./gradlew recordPaparazziDebug`. 클론은 `git clone --recurse-submodules`(또는 `git submodule update --init`). 중계 서버 통합 시험은 환경 변수 `HEY_RELAY_DIR`(기본 `../hey-relay`)의 relay를 `bun`으로 띄우며, 없으면 건너뛰고 이유를 출력한다. 빌드 머신 부하를 줄이기 위해 `org.gradle.workers.max=4`.
- 에뮬레이터는 개발 맥북에서 직접 띄운다(`~/pleos/emu-start.sh`, adb `emulator-5600`). 종단 시험은 `hey-e2e`.
- 서버·승객 웹 실행과 비밀값 규칙은 각각 `hey-relay`, `hey-passenger-web`의 문서를 따른다(비밀값은 저장소에 넣지 않는다).
