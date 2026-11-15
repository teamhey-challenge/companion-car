# companion-car

> 공모전 제출용 저장소다. 앱 Hey(캐릭터 헤이리), 팀 Team Hey. 저장소 이름 `companion-*`은 그대로다.

## 앱 소개

차 안에 사는 캐릭터 AI 컴패니언 "헤이리"의 **차량 앱**이다. Pleos(Android Automotive) 차 화면에서 동작하며, 음성으로 대화하고 차량 상태(주행 상태·기어·문·내비 목적지 거리 등)를 읽어 먼저 말을 걸고, 사용자를 기억한다(기억은 폰 앱에서 보고 지울 수 있다). 주행 중에는 음성만 쓰고, 화면은 큰 글자·큰 버튼으로 단순하게 둔다.

- 음성 인식(STT)과 차량 정보는 Pleos 내장 SDK, 답변 생성과 캐릭터 목소리(TTS)는 서버가 맡는다.
- 패키지 `ai.hey.car`, minSdk 28 / targetSdk 34, 버전 0.1.0.

## APK 위치

- 제출용 APK: `apk/TeamHey_Hey_release.apk`(릴리스 키 서명본). 다시 만들려면 `scripts/stage-apk.sh --release TeamHey Hey`(마지막 성공한 `release-apk` 실행의 산출물을 받는다). 파일이 크면 GitHub Releases에 올리고 여기에 링크를 적는다.
- 항상 최신 빌드: `https://hey.lastmile.ltd/download/latest.apk` (파일 목록과 SHA-256은 `https://hey.lastmile.ltd/download/`).

## 테스트 환경

- Pleos Playground 에뮬레이터(Android Automotive)에서 시험했다. 실차 시험 결과는 이 저장소에 없다.
- 서버는 팀이 운영하는 `https://hey.lastmile.ltd/api`(기본값). 처음 연결할 때 기기가 등록 비밀(아래 "등록 비밀")로 서버에 등록해야 해서, 비밀 없이 APK만 설치하면 대화는 되지 않는다. 심사용 시연은 시연 영상과 팀이 준비한 기기로 한다.
- 마이크가 없는 환경에서는 디버그 빌드의 텍스트 주입(`DemoVoice`)으로 음성 입력을 대신한다.

## 저장소 구성

| 저장소 | 내용 |
|---|---|
| [companion-car](https://github.com/teamhey-challenge/companion-car) | 차량 앱(이 저장소) |
| [companion-phone](https://github.com/teamhey-challenge/companion-phone) | 폰 앱(QR 연결·동의·기억 관리) |
| [companion-server](https://github.com/teamhey-challenge/companion-server) | 대화·기억·TTS 서버 |
| [companion-web](https://github.com/teamhey-challenge/companion-web) | 폰 웹(폰 앱의 백업)·디자인 시스템 Storybook |
| [companion-shared](https://github.com/teamhey-challenge/companion-shared) | 디자인 토큰·프로토콜(서브모듈 `shared/`) |

HEY 시절 `hey-car@8b8de8c`의 기록을 이어받은 저장소다(택시 운행 화면·경로 시작 상태 기계·HEY 중계 서버 연결은 지웠다). 모듈 구성은 아래와 같다.

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
git clone --recurse-submodules git@github.com:teamhey-challenge/companion-car.git
./check.sh    # 조립·단위 시험·Paparazzi 스냅숏 검증 (nice, --max-workers=2)
```

- JDK 17, Android SDK(compileSdk 36)가 필요하다. `shared/`는 `companion-shared` v0.3.0에 고정한 서브모듈이다.
- 에뮬레이터·실차 시험에는 Pleos 에뮬레이터(Android Automotive)가 필요하다. 마이크가 없는 환경에서는 디버그 빌드의 텍스트 주입(`DemoVoice`)으로 음성 입력을 대신한다.
- 패키지 이름 `ai.hey.*`는 앱 이름이 확정될 때까지 바꾸지 않는다.
- `docs/`에는 HEY 시절 문서가 남아 있다. 컴패니언 기준은 기획 저장소의 PRD가 정본이다.

## CI와 스냅숏 기록

- PR의 `ci`는 Gradle 빌드 캐시(main이 채움, PR은 읽기 전용)와 구성 캐시(`--configuration-cache`)로 diff가 건드리지 않은 모듈의 컴파일·시험·스냅숏 검증을 건너뛴다. main은 항상 전체 `./check.sh`를 돌리고 디버그 APK를 `car-apk` 이름으로 올린다(배포 타이머가 이 이름을 쓴다). 문서(`*.md`, `docs/`)만 바꾼 PR은 CI를 돌리지 않는다.
- 스냅숏 PNG는 로컬에서 기록하지 않는다. CI의 `record-snapshots` 워크플로가 만든다.
  - PR에 `record-snapshots` 라벨을 붙이거나(같은 저장소의 브랜치만), `gh workflow run record-snapshots.yml -f branch=<브랜치>`로 실행한다.
  - 모든 모듈에 `recordPaparazziDebug`를 돌려 바뀐 `*/src/test/snapshots` PNG를 그 브랜치에 커밋 하나(`snapshots: record on CI`)로 푸시하고, 이어서 `ci`를 그 브랜치에 다시 돌린다. 끝나면 라벨은 자동으로 지워진다.
  - 푸시된 뒤에는 `git pull --rebase`로 받아 이어서 작업한다. 바뀐 PNG는 PR diff에서 눈으로 확인한다.
  - 레코더는 PR 브랜치에서만 돈다. `main`(기본 브랜치)이나 빈 브랜치로 실행하면 첫 단계에서 바로 실패한다.


## 링크·라이선스

- 서버: [companion-server](https://github.com/teamhey-challenge/companion-server), 폰 웹: [companion-web](https://github.com/teamhey-challenge/companion-web)
- 코드는 MIT(`LICENSE`). 글꼴·SDK·런타임 고지는 `THIRD_PARTY.md`. Pleos SDK는 이 저장소에 들어 있지 않고 Playground 약관을 따른다.

