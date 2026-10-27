#!/usr/bin/env bash
# Build a signed release App Bundle (.aab) to upload to Google Play's Internal
# testing track. Requires keystore.properties (run ./make-upload-key.sh first)
# and an Android SDK (./setup-toolchain.sh, or set ANDROID_SDK_ROOT).
set -euo pipefail
cd "$(dirname "$0")"

if [ -f "$HOME/android-toolchain/env.sh" ]; then
  # shellcheck disable=SC1091
  source "$HOME/android-toolchain/env.sh"
fi
: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT not set — run ./setup-toolchain.sh first}"

if [ ! -f keystore.properties ]; then
  echo "✗ keystore.properties missing — run ./make-upload-key.sh first." >&2
  exit 1
fi

printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties

echo "→ Building signed release AAB…"
./gradlew bundleRelease --no-daemon

cp app/build/outputs/bundle/release/app-release.aab ./codriver-release.aab
echo "✓ AAB -> $PWD/codriver-release.aab ($(du -h codriver-release.aab | cut -f1))"
echo "  Upload it in Play Console ▸ Testing ▸ Internal testing ▸ Create new release."
echo "  (For later uploads, bump versionCode in app/build.gradle.kts first.)"
