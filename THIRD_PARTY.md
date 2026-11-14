# 제3자 라이선스 고지

이 저장소의 코드는 MIT(`LICENSE`)다. 아래 구성요소는 각자의 조건을 따른다.

## Pleos Playground SDK

- 대상: `ai.pleos.playground:Vehicle`, `NaviHelper`, `TextToSpeech`, `SpeechToText` (버전은 `gradle/libs.versions.toml`).
- 이 저장소에는 SDK 파일을 넣지 않는다. 빌드할 때 Pleos Playground 저장소(`settings.gradle.kts`)에서 받는다. 받으려면 Playground 계정과 서비스 이용약관 동의가 필요하다.
- SDK의 이용 조건은 Pleos Playground 서비스 이용약관을 따르며 MIT가 적용되지 않는다. 약관에 따라 이 저장소에는 SDK 성능·측정 결과를 싣지 않는다.

## 글꼴: Chiron GoRound TC

- SIL Open Font License 1.1, Copyright 2025 The Chiron GoRound TC Project Authors.
- 안드로이드용 부분 글꼴(`shared/android/src/main/res/font/chiron_goround_tc_*.ttf`)을 `shared/` 서브모듈로 가져온다. 라이선스 전문은 `shared/android/OFL-ChironGoRoundTC.txt`.
- 글꼴은 단독으로 판매하지 않으며, 부분 글꼴(subset)로 줄인 파생본이다.

## Rive 런타임

- 캐릭터 애니메이션에 `app.rive:rive-android`를 쓴다(쓰는 빌드에 한해). MIT 라이선스, Copyright Rive.
- `.riv` 캐릭터 파일은 이 팀이 만든 에셋이며 이 저장소의 MIT를 따른다.

## 오픈소스 라이브러리

Kotlin·AndroidX·Jetpack Compose·OkHttp·kotlinx 계열은 Apache License 2.0이다. Paparazzi(시험 전용)는 Apache License 2.0이다. 정확한 버전은 `gradle/libs.versions.toml`.
