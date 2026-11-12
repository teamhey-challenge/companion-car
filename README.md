# hey-car

HEY 차량 앱(Pleos Connect, AAOS 가로, Kotlin + Jetpack Compose). 모노레포 `hey-taxi`의 `apps/car-android`를 기록째 떼어 냈다. 계약은 `docs/architecture.md`, 부품 목록은 `docs/components.md`.

## 클론과 공유 코드

```sh
git clone --recurse-submodules git@github.com:darren-pleos-challenge/hey-car.git   # 이미 클론했다면: git submodule update --init
```

`shared/`는 `hey-shared` 서브모듈(태그 `v0.1.0` 고정)이다. 토큰은 `includeBuild("shared/android")`로 `ai.hey:tokens-compose`에 의존하고, 프로토콜 fixture는 `shared/packages/protocol/fixtures`를 `:core:model` 시험이 읽는다. 버전 올리기: `git -C shared fetch --tags && git -C shared checkout <새 태그>` 후 포인터를 커밋.

## 로컬 점검 (CI 없음)

```sh
./check.sh   # :app:assembleDebug + check(시험, lint, checkDesignSystemUsage) + Paparazzi verify 3개
```

relay 통합 시험은 `HEY_RELAY_DIR`(기본 `../hey-relay`)의 `hey-relay`를 `bun`으로 띄운다. 그 폴더에서 `bun install`(서브모듈 초기화 포함)이 끝나 있어야 하며, 폴더가 없거나 `bun`이 없으면 이유를 출력하고 건너뛴다. JDK 17, Android SDK 필요. 스냅숏 기록: `./gradlew recordPaparazziDebug`.

릴리스(로컬 전용, CI 없음): `./gradlew :app:assembleRelease -PrelayUrl=https://…` → `dist/apk/TEAM_HEY_release.apk`(팀 이름이 정해지면 `-PteamName=…`). R8 축소·리소스 축소, https만 허용. 서명 키는 저장소 밖 `~/.config/hey/signing.properties`(권한 600)에서 읽고, 없으면 복사 단계가 실패한다. 버전은 `gradle.properties`의 `hey.versionCode`/`hey.versionName` 한 곳.
