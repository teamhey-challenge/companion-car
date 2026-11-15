#!/usr/bin/env bash
# Local check for the car app: build, unit tests, design-system usage scan, Paparazzi snapshot verification.
# Needs the submodule (git submodule update --init). The relay integration test uses HEY_RELAY_DIR (default ../hey-relay)
# and is skipped with a message when the relay checkout is absent.
set -euo pipefail
cd "$(dirname "$0")"
[ -f shared/android/settings.gradle.kts ] || git submodule update --init
exec nice -n 19 ./gradlew --max-workers=2 \
  :app:assembleDebug check \
  :feature:settings:verifyPaparazziDebug :feature:home:verifyPaparazziDebug :feature:memory:verifyPaparazziDebug :feature:onboarding:verifyPaparazziDebug :core:designsystem:verifyPaparazziDebug \
  "$@"
