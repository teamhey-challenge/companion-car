# companion-car

Pleos 차량용 캐릭터 AI 컴패니언 "헤이파리"(임시 이름 `companion-*`)의 차량 앱 저장소다. HEY 시절 `hey-car@8b8de8c`의 기록을 이어받았고, 택시 운행 화면·경로 시작 상태 기계·HEY 중계 서버 연결(`RelayLink`, `TripRepository`)은 지웠다. 지금 화면은 자리표시 홈(`CompanionHome`)과 설정(D8)뿐이다.

## 재사용 중인 부분

| 위치 | 내용 |
|---|---|
| `core/vehicle` | Pleos 차량 게이트웨이(주행 상태·기어·문), Pleos TTS(`PleosSpeechEngine`)·STT(`PleosVoiceInput`), `SpeechQueue` |
| `core/session` | `/v1/talk` 클라이언트: `TalkLink`(등록 → 세션 → 티켓 WebSocket, 재연결 1·2·4·8·15초), `TalkRepository`(한 턴: Pleos STT → 서버 → 응답). 주행 중에도 듣기·턴은 끊지 않는다(AC-4.4-18, 4.5-07). 정차 때만 쓰는 명령(`ParkedCommands`) 구조 |
| `core/designsystem` | Compose 부품·테마·Paparazzi 시험 (토큰은 `shared/android`) |
| `core/model` | 설정·메시지 모델 |
| `feature/settings` | 설정 화면 |

음성 인식은 Pleos 내장 STT(ON_DEVICE `Locale.KOREA`)다. 답은 서버가 글자로 보내고, `reply.start.audio`가 `none`이면 Pleos TTS가 읽는다.

## 등록 비밀(provision secret)

APK에는 비밀이 없다(APK는 `https://hey.lastmile.ltd/download/latest.apk`로 공개된다). 처음 연결할 때 기기가 `X-Provision-Secret`로 등록하려면 시연 기기에 파일 하나가 있어야 한다.

- 경로: `/data/data/ai.hey.car/files/provision.secret` (앱 전용, 권한 600) — 첫 줄에 비밀 값만.
- 넣는 법(디버그 빌드): `adb shell run-as ai.hey.car sh -c 'umask 077; cat > files/provision.secret' < 파일` (값을 화면에 찍지 않는다). run-as가 안 되는 기기는 `/sdcard/Android/data/ai.hey.car/files/provision.secret`.
- 값은 서버의 `COMPANION_PROVISION_SECRET`(`~/.config/companion/server.env`)과 같다. 저장소·로그·보고서에 쓰지 않는다. 등록이 끝나면 차량 토큰은 앱이 저장하고, 이 파일은 다시 읽지 않는다.

## 빌드

```sh
git clone --recurse-submodules git@github.com:darren-pleos-challenge/companion-car.git
./check.sh    # 조립·단위 시험·Paparazzi 스냅숏 검증 (nice, --max-workers=2)
```

- JDK 17, Android SDK(compileSdk 36)가 필요하다. `shared/`는 `companion-shared` v0.3.0에 고정한 서브모듈이다.
- 에뮬레이터·실차 시험에는 Pleos 에뮬레이터(Android Automotive)가 필요하다. 마이크가 없는 환경에서는 디버그 빌드의 텍스트 주입(`DemoVoice`)으로 음성 입력을 대신한다.
- 패키지 이름 `ai.hey.*`는 앱 이름이 확정될 때까지 바꾸지 않는다.
<<<<<<< HEAD
- `docs/`에는 HEY 시절 문서가 남아 있다. 컴패니언 기준은 기획 저장소의 PRD가 정본이다.

## CI와 스냅숏 기록

- PR의 `ci`는 Gradle 빌드 캐시(main이 채움, PR은 읽기 전용)와 구성 캐시(`--configuration-cache`)로 diff가 건드리지 않은 모듈의 컴파일·시험·스냅숏 검증을 건너뛴다. main은 항상 전체 `./check.sh`를 돌리고 디버그 APK를 `car-apk` 이름으로 올린다(배포 타이머가 이 이름을 쓴다). 문서(`*.md`, `docs/`)만 바꾼 PR은 CI를 돌리지 않는다.
- 스냅숏 PNG는 로컬에서 기록하지 않는다. CI의 `record-snapshots` 워크플로가 만든다.
  - PR에 `record-snapshots` 라벨을 붙이거나(같은 저장소의 브랜치만), `gh workflow run record-snapshots.yml -f branch=<브랜치>`로 실행한다.
  - 모든 모듈에 `recordPaparazziDebug`를 돌려 바뀐 `*/src/test/snapshots` PNG를 그 브랜치에 커밋 하나(`snapshots: record on CI`)로 푸시하고, 이어서 `ci`를 그 브랜치에 다시 돌린다. 끝나면 라벨은 자동으로 지워진다.
  - 푸시된 뒤에는 `git pull --rebase`로 받아 이어서 작업한다. 바뀐 PNG는 PR diff에서 눈으로 확인한다.


## 링크·라이선스

- 서버: [companion-server](https://github.com/darren-pleos-challenge/companion-server), 폰 웹: [companion-web](https://github.com/darren-pleos-challenge/companion-web)
- 코드는 MIT(`LICENSE`). 글꼴·SDK·런타임 고지는 `THIRD_PARTY.md`. Pleos SDK는 이 저장소에 들어 있지 않고 Playground 약관을 따른다.

