# 헤이파리 차량 부품 목록 (v0.5, 2026-11-14)

화면 목록과 동작은 기획 저장소 `proposal/companion/PRD.md` §3·§4가 소유한다. 이 문서는 차량 부품(`:core:designsystem`, Compose)만 소유한다. `:feature:*`와 `:app`은 이 모듈의 공개 함수만 쓰고, 화면에서 색·글꼴·dp를 직접 쓰지 않는다. 부품은 `modifier: Modifier = Modifier`를 받고 바깥 여백을 더하지 않는다. 문구는 `String`으로 받으며 기본 문구가 있는 부품은 `strings.xml`(`hey_*`)에서 가져온다. 차량 터치 최소 76dp, 주 조작 96dp(확인 창 버튼 104dp, C5 큰 말하기 112dp). 글꼴은 `HeyTheme`의 Chiron GoRound TC, 색은 생성 토큰(`HeyColors`)만 쓴다. 라이트=바다, 다크=심해.

## v0.5 부품

| 부품 | 인자 | 비고 |
|---|---|---|
| `OceanBackground` | `animated`, `content` | 바다/심해 그라데이션 + 물방울 6개(목업 `oceanBubbles` 값). `animated=false` 또는 시스템 모션 줄이기면 정지. 정차 화면 C1~C4·C6~C8 |
| `PageBackground` | `content` | 장식 없는 평면 배경. C5·주행·신호 미상 |
| `CharacterSlot` | `size: CharacterSize`, `look = CharacterLook()`, `still = characterStill(look)`, `live?`, `contentDescription?` | `Stage` 580×620(C3/C4/C8), `Hero` 500×440(C1/C2), `Driving` 280×300(C5), `Small` 150×160. `live`에 Rive 뷰를 넘기면 정지 이미지 대신 그린다. 크기는 항상 고정 |
| `characterStill(look)` | `CharacterLook(bodyColor?, hat, neck)` | 꾸미기(D-41) 조합별 정지 이미지 12장(몸 2 × 모자 3 × 스카프 2, `res/drawable-nodpi/hey_character_still_b{0,1}_h{0,1,2}_n{0,1}.webp`). 프레임은 `hey_companion.riv` 자체를 정지 자세로 그려 뽑았다. `bodyColor` null이면 테마를 따른다(라이트 아쿠아, 다크 살구). C5는 항상 이 그림이다(D-11) |
| `CharacterStill` | `description`, `size`, `caption?` | 정지 + 아래 설명 한 줄(C1/C2) |
| `TalkButton` | `phase: TalkPhase`, `onClick`, `driving`, `enabled`, `label?`, `onHoldStart?`, `onHoldEnd?` | 96dp×320dp 이상, 28sp 라벨·32dp 아이콘(시안 `.talk-button`). Thinking·Speaking은 막힌 모양, 입력 없음. 정차: 길게 누르기 콜백, 주행: 탭 `onClick` |
| `StopListeningButton` | `onClick` | "그만 듣기"(D-24), 항상 누를 수 있음 |
| `PageHeading` | `title`, `subtitle?`, `hero` | 홈 인사 72sp(`hero`) 또는 제목 |
| `HeaderBar` | `title`, `onBack?`, `backLabel`, `stateLabel?`, `trailing?` | 뒤로 원형 76dp, 제목, "정차 중", 오른쪽 기억·설정 |
| `StatusBadge` | `phase`, `driving`, `connection`, `animated` | 듣는 중·말하는 중은 12막대 파형(글자는 접근성만), 생각 중, 연결 중, 오프라인. 주행 중 대기는 그리지 않음 |
| `ErrorKind`, `ErrorPanel` | `kind`, `onAction?`, `actionLabel?`, `title?`, `message?` | C8: Offline, Connecting, Microphone, Server, Recognition, Audio. `onAction=null`이면 버튼 없음, Connecting은 비활성 "연결 중". 캐릭터(sorry)는 화면이 옆에 놓는다 |
| `Subtitle` | `text`, `driving`, `large` | 3줄 제한·말줄임, `driving`이면 그리지 않음 |
| `ToggleRow` | `label`, `checked`, `onCheckedChange`, `description?`, `enabled`, `saving` | 96dp, `saving`이면 스피너 + 입력 차단 |
| `CheckRow` | `label`, `checked`, `onCheckedChange`, `enabled`, `error?` | C1 동의 체크 |
| `ChoiceRow<T>` | `options: List<HeySegmentOption<T>>`, `selected`, `onSelect`, `label?`, `enabled` | 적게/보통/자주/끄기, 반말/존댓말. 선택은 강조 배경 + 3dp 테두리 |
| `MemoryRow` | `kind`, `text`, `onDelete`, `deleteLabel`, `date?`, `tint`, `enabled` | C6 줄, 삭제 76dp 아이콘 버튼 |
| `SkeletonRow` | — | C6 loading, 모션 줄이기면 정지 |
| `ConfirmDialog` | `title`, `message`, `confirmText`, `dismissText`, `onConfirm`, `onDismiss`, `danger`, `busy` | 막 + 카드, 버튼 104dp, 전체 크기 Box 맨 위에 둔다 |
| `QrPanel` | `content`, `code`, `status`, `qrSize` | QR + 코드 글자 + 상태 줄 |

## 기반 부품 (유지)

`HeyTheme(dark)`, `HeyTokens`, `VStack`/`HStack`/`HeyGap`, `CarFrame`, `StatusStrip(drivingLabel, connection, title, clock)`, `HeyText`, `HeyIcon`/`HeyIconButton`, `HeyButton`, `HeyCard`, `HeyStatusPill`, `HeyBanner`, `HeyDivider`, `HeySwitchRow`, `HeyStepperRow`, `HeySegmented`, `HeyDialog`, `HeyQrCode`, `HeyScrollColumn`, `HeySection`, `HeyNotice`, `HeyTextField`, `HeyLayered`, `EmptyPanel`. 아이콘: `HeyIcons`(Trash, Pause, Book, Gear 추가).

택시 시대 부품(JoinRequestCard, ConditionLineRow, KoreanSummary, DropoffCandidateRow, ReasonChips, ProposalStatus, RequestRow, QuickReplyGrid/Group, VoiceConfirmDialog, EtaBlock, DrivingPanel, TripSummaryCard, LanguageGreetingStrip, QrBlock, HeyWordmark)는 삭제했다.

## Paparazzi

부품마다 `core/designsystem/src/debug/.../gallery/Stories.kt`에 이야기가 있고 라이트·다크가 모두 기록된다(기본, 비활성/상태, 띄어쓰기 없는 긴 한국어 `NO_SPACE_KO`). 스냅숏은 로컬에서 기록하지 않는다: PR에 `record-snapshots` 라벨을 붙이거나 `gh workflow run record-snapshots.yml -f branch=<branch>`. 화면 시트 `c3-home-*`, `c5-driving-*`는 부품만으로 조립한 비교용이다.
