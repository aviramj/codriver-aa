#!/usr/bin/env bash
# Build the CoDriver AI debug APK. Requires an Android SDK: run
# ./setup-toolchain.sh first, or point ANDROID_SDK_ROOT at an existing SDK.
set -euo pipefail
cd "$(dirname "$0")"

if [ -f "$HOME/android-toolchain/env.sh" ]; then
  # shellcheck disable=SC1091
  source "$HOME/android-toolchain/env.sh"
fi

: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT not set — run ./setup-toolchain.sh first}"
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties

echo "→ Building debug APK…"
./gradlew assembleDebug --no-daemon

cp app/build/outputs/apk/debug/app-debug.apk ./codriver.apk
echo "✓ APK -> $PWD/codriver.apk ($(du -h codriver.apk | cut -f1))"
echo "  Install on your phone:  adb install -r codriver.apk"
