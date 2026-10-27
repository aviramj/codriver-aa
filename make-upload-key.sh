#!/usr/bin/env bash
# Generate a Play "upload key" and write keystore.properties for release builds.
#
# Run this ONCE, on a machine you control, and keep the generated .jks file safe
# (back it up). With Play App Signing, Google holds the real app-signing key;
# this upload key only signs what you upload, and Google can reset it if it's
# ever lost. Both the keystore and keystore.properties are gitignored.
set -euo pipefail
cd "$(dirname "$0")"

KEYSTORE="upload-keystore.jks"
ALIAS="upload"

if [ -f "$KEYSTORE" ]; then
  echo "✗ $KEYSTORE already exists — refusing to overwrite." >&2
  echo "  Delete it first if you really intend to create a new key." >&2
  exit 1
fi

read -r -s -p "Choose a keystore password (min 6 chars): " PW; echo
if [ "${#PW}" -lt 6 ]; then
  echo "✗ Password too short." >&2
  exit 1
fi

keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PW" -keypass "$PW" \
  -dname "CN=CoDriver AI, OU=Personal, O=CoDriver, C=US"

umask 077
cat > keystore.properties <<EOF
storeFile=$KEYSTORE
storePassword=$PW
keyAlias=$ALIAS
keyPassword=$PW
EOF

echo "✓ Created $KEYSTORE and keystore.properties (both gitignored)."
echo "  Back up $KEYSTORE somewhere safe, then run ./build-aab.sh"
