# companion-car

앱 이름 **Hey**(캐릭터 헤이리), 팀 **Team Hey**. 저장소 이름 `companion-*`은 그대로다.

차 안에 사는 캐릭터 AI 컴패니언 "헤이리"의 **차량 앱** 저장소다. Pleos(Android Automotive) 차 화면에서 동작하며, 음성으로 대화하고 차량 상태(주행 상태·기어·문·내비 목적지까지 남은 거리 등)를 읽어 먼저 말을 걸고, 사용자를 기억한다(기억은 폰 앱에서 보고 지울 수 있다). 주행 중에는 음성만 쓰고 화면은 큰 글자·큰 버튼으로 단순하게 둔다. 패키지 `ai.hey.car`, minSdk 28 / targetSdk 34.

## 제출물과 주소

| 항목 | 위치 |
|---|---|
| 차량 앱 APK(릴리스 키 서명) | `apk/TeamHey_Hey_release.apk`, 항상 최신본은 https://hey.lastmile.ltd/download/latest.apk |
| 폰 앱 APK | companion-phone 저장소 `apk/TeamHey_Hey-phone_release.apk`, 최신본은 https://hey.lastmile.ltd/download/phone/latest.apk |
| 서버 | https://hey.lastmile.ltd/api (앱 기본값) |
| 파일 목록·SHA-256 | https://hey.lastmile.ltd/download/ |

APK를 다시 만들려면 `scripts/stage-apk.sh --release TeamHey Hey`(마지막 성공한 `release-apk` 실행의 산출물을 받는다).

## 화면 규격

- 디자인 기준 캔버스는 **1600x900 dp** 가로 화면이다. 앱은 창 너비를 이 캔버스에 맞춰 한 번 스케일한다(`MainActivity`).
- Pleos 에뮬레이터의 실제 앱 창은 1682x1210 px이며 스케일 후 **1251x900 dp**다.
- 멀티윈도우(내비 옆 분할)는 **840 dp**(1/2 너비)와 **560 dp**(1/3 너비)를 지원한다. 높이는 900 dp 기준이다. 1600·1251·840·560 네 너비 모두 화면별 스냅숏 시험이 있다.
- 버튼은 76 dp 이상, 주요 액션은 96 dp이고, 주행 중에는 음성만 쓴다.

## 테스트 환경

- 에뮬레이터: Pleos Connect 에뮬레이터 `pleos_connect_v2_1`(Android Automotive). 실차 시험 결과는 이 저장소에 없다.
- Pleos SDK(`ai.pleos.playground`): Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1, SpeechToText 3.3.1(`gradle/libs.versions.toml`).
- 빌드: JDK 17, Gradle 8.13(래퍼), Android Gradle Plugin 8.11.2, Kotlin 2.1.0, compileSdk 36. JDK 17과 Android SDK가 있으면 Android Studio 없이도 빌드된다.
- 마이크가 없는 환경에서는 디버그·e2e 빌드의 텍스트 주입(`DemoVoice`)으로 음성 입력을 대신한다. 릴리스에는 주입 경로가 없다.

## 빌드와 실행

```sh
git clone --recurse-submodules https://github.com/teamhey-challenge/companion-car.git
cd companion-car
./check.sh                      # 조립 + 단위 시험 + Paparazzi 스냅숏 검증
./gradlew :app:assembleDebug    # 디버그 APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`shared/`는 `companion-shared`를 고정한 서브모듈이다. 제출용 APK를 그대로 쓰려면 `adb install -r apk/TeamHey_Hey_release.apk`로 설치하고 Pleos 에뮬레이터 앱 목록에서 Hey를 연다.

## 심사자 에뮬레이터에서 차 등록하기

APK에는 비밀이 없다. 처음 연결할 때 서버가 차를 등록하려면 기기에 **등록 비밀 파일** 하나가 있어야 한다. 이 파일은 팀이 요청 시 따로 전달한다(저장소·APK에는 값이 없다).

- 앱 전용 경로: `/data/data/ai.hey.car/files/provision.secret`(권한 600), 첫 줄에 값만 적는다.
- 넣는 법(디버그 빌드): `adb shell run-as ai.hey.car sh -c 'umask 077; cat > files/provision.secret' < 받은파일`. `run-as`가 안 되는 기기는 `/sdcard/Android/data/ai.hey.car/files/provision.secret`에 둔다.
- 등록이 끝나면 차량 토큰은 앱이 저장하고 이 파일은 다시 읽지 않는다. 파일이 없으면 서버 대화는 되지 않는다.

## 사용하는 Pleos SDK

| SDK | 쓰는 곳 |
|---|---|
| Vehicle | 주행 상태·기어·문 등 차량 신호 읽기(`core/vehicle`) |
| NaviHelper | 목적지 유무·남은 거리·도착 이벤트(`core/vehicle`) |
| SpeechToText | 음성 인식. ON_DEVICE, `Locale.KOREA` |
| TextToSpeech | 서버 음성이 없을 때(`reply.start.audio`가 `none`) 답을 읽는다 |

답변 생성과 캐릭터 목소리(서버 TTS)는 서버가 맡는다. Pleos SDK는 이 저장소에 들어 있지 않고 Playground 약관을 따른다.

## 개인정보

- 음성 인식은 차량의 Pleos **온디바이스 STT**가 하며 마이크 오디오는 서버로 보내지 않는다. 서버에는 인식된 문장 글자만 간다.
- **좌표는 저장하지 않는다.** 내비 목적지 좌표는 "목적지가 있는가"를 판단하는 데만 쓰고, 서버에는 남은 거리(km)와 목적지 이름만 보낸다. 서버도 `ctx`의 좌표 키(`lat`·`lon`·`gps`·`location` 등)를 버린다.
- 기억은 사용자가 폰 앱에서 보고 지울 수 있다.

## 저장소 구성

| 저장소 | 내용 |
|---|---|
| [companion-car](https://github.com/teamhey-challenge/companion-car) | 차량 앱(이 저장소) |
| [companion-phone](https://github.com/teamhey-challenge/companion-phone) | 폰 앱(QR 연결·동의·기억 관리) |
| [companion-server](https://github.com/teamhey-challenge/companion-server) | 대화·기억·TTS 서버 |
| [companion-web](https://github.com/teamhey-challenge/companion-web) | 폰 웹(폰 앱의 백업)·디자인 시스템 Storybook |
| [companion-shared](https://github.com/teamhey-challenge/companion-shared) | 디자인 토큰·프로토콜(서브모듈 `shared/`) |

| 모듈 | 내용 |
|---|---|
| `core/vehicle` | Pleos 차량 게이트웨이, Pleos TTS·STT, `SpeechQueue` |
| `core/session` | `/v1/talk` 클라이언트(등록 → 세션 → 티켓 WebSocket, 재연결 1·2·4·8·15초) |
| `core/designsystem` | Compose 부품·테마·Paparazzi 시험(토큰은 `shared/android`) |
| `core/model` | 설정·메시지 모델 |
| `feature/*` | 홈·온보딩·설정·기억 화면 |

## CI와 스냅숏 기록

- PR의 `ci`는 Gradle 빌드 캐시와 구성 캐시로 diff가 건드리지 않은 모듈의 컴파일·시험·스냅숏 검증을 건너뛴다. main은 항상 전체 `./check.sh`를 돌리고 디버그 APK를 `car-apk` 이름으로 올린다. 문서(`*.md`, `docs/`)만 바꾼 PR은 CI를 돌리지 않는다.
- 스냅숏 PNG는 로컬에서 기록하지 않는다. CI의 `record-snapshots` 워크플로가 만든다. PR에 `record-snapshots` 라벨을 붙이거나 `gh workflow run record-snapshots.yml -f branch=<브랜치>`로 실행하면 바뀐 PNG를 그 브랜치에 커밋 하나로 푸시한다. 레코더는 PR 브랜치에서만 돈다.

## 라이선스

코드는 MIT(`LICENSE`). 글꼴·SDK·런타임 고지는 `THIRD_PARTY.md`.
