#!/usr/bin/env bash
set -euo pipefail

# Signs an unsigned release APK locally on the owner's machine using their existing SDK apksigner and keystore.
# No keystore or password is ever stored in the repository.

UNSIGNED_APK="${1:-app-release-unsigned.apk}"
SIGNED_APK="${2:-medo-next-signed.apk}"
KEYSTORE_FILE="${MEDO_KEYSTORE_PATH:-$HOME/.medo/release.keystore}"
KEY_ALIAS="${MEDO_KEY_ALIAS:-medo}"

if [ ! -f "$UNSIGNED_APK" ]; then
  echo "Error: Unsigned APK not found at $UNSIGNED_APK"
  exit 1
fi

if [ ! -f "$KEYSTORE_FILE" ]; then
  echo "Error: Local keystore not found at $KEYSTORE_FILE. Set MEDO_KEYSTORE_PATH."
  exit 1
fi

APKSIGNER_BIN="${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/$(ls "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools" | tail -n 1)/apksigner"

"$APKSIGNER_BIN" sign \
  --ks "$KEYSTORE_FILE" \
  --ks-key-alias "$KEY_ALIAS" \
  --out "$SIGNED_APK" \
  "$UNSIGNED_APK"

"$APKSIGNER_BIN" verify --verbose "$SIGNED_APK"
echo "Successfully signed APK -> $SIGNED_APK"
