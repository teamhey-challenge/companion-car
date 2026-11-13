# companion-car

Pleos 차량용 캐릭터 AI 컴패니언 앱(임시 이름 `companion-*`)의 차량 앱 저장소다. HEY 시절 `hey-car@8b8de8c`의 기록을 그대로 이어받았고, 택시 운행 화면(`feature/trip`)과 내비 경로 시작 상태 기계(`RouteStartMachine`)는 지웠다. 지금 화면은 자리표시 홈(`CompanionHome`)과 설정(D8)뿐이다.

## 재사용 중인 부분

| 위치 | 내용 |
|---|---|
| `core/vehicle` | Pleos 차량 게이트웨이(주행 상태·기어·문), Pleos TTS(`PleosSpeechEngine`)·STT(`PleosVoiceInput`), `SpeechQueue` |
| `core/session` | 서버 연결(`RelayLink`, 재연결 1·2·4·8·15초), 정차 때만 쓰는 명령(`ParkedCommands`) 구조 |
| `core/designsystem` | Compose 부품·테마·Paparazzi 시험 (토큰은 `shared/android`) |
| `core/model` | 설정·메시지 모델 |
| `feature/settings` | 설정 화면 |

음성 인식·합성은 서버가 아니라 Pleos 내장 서비스(STT ON_DEVICE `Locale.KOREA`, Pleos TTS)를 쓴다.

## 빌드

```sh
git clone --recurse-submodules git@github.com:darren-pleos-challenge/companion-car.git
./check.sh    # 조립·단위 시험·Paparazzi 스냅숏 검증 (nice, --max-workers=2)
```

- JDK 17, Android SDK(compileSdk 36)가 필요하다. `shared/`는 `companion-shared` v0.1.0에 고정한 서브모듈이다.
- 에뮬레이터는 `darren-mbp-14`에서만 띄운다. darren-vm에서는 띄우지 않는다.
- 패키지 이름 `ai.hey.*`는 앱 이름이 확정될 때까지 바꾸지 않는다.
- `docs/`에는 HEY 시절 문서가 남아 있다. 컴패니언 기준은 기획 저장소의 PRD가 정본이다.
