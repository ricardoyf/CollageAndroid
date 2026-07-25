#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-35.0.0}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-34}"

if [[ -z "$SDK_DIR" ]]; then
  echo "Define ANDROID_SDK_ROOT o ANDROID_HOME." >&2
  exit 1
fi

TOOLS_DIR="$SDK_DIR/build-tools/$BUILD_TOOLS_VERSION"
ANDROID_JAR="$SDK_DIR/platforms/$ANDROID_PLATFORM/android.jar"
BUILD_DIR="$PROJECT_DIR/build"
KEYSTORE="${KEYSTORE_PATH:-$PROJECT_DIR/.local/debug.keystore}"
APK="$BUILD_DIR/Reencuadrador-v5.apk"

for required in aapt d8 zipalign apksigner; do
  if [[ ! -x "$TOOLS_DIR/$required" ]]; then
    echo "Falta $TOOLS_DIR/$required" >&2
    exit 1
  fi
done

if [[ ! -f "$ANDROID_JAR" ]]; then
  LATEST_PLATFORM="$(find "$SDK_DIR/platforms" -mindepth 2 -maxdepth 2 -type f -name android.jar -printf '%h\n' | sort -V | tail -n 1)"
  ANDROID_JAR="$LATEST_PLATFORM/android.jar"
fi

if [[ ! -f "$ANDROID_JAR" ]]; then
  echo "No se encontró android.jar en $SDK_DIR/platforms" >&2
  exit 1
fi

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$(dirname "$KEYSTORE")"

"$TOOLS_DIR/aapt" package \
  -f \
  -m \
  -J "$BUILD_DIR/gen" \
  -M "$PROJECT_DIR/AndroidManifest.xml" \
  -S "$PROJECT_DIR/res" \
  -I "$ANDROID_JAR"

javac \
  -encoding UTF-8 \
  -source 8 \
  -target 8 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$BUILD_DIR/classes" \
  $(find "$PROJECT_DIR/src" "$BUILD_DIR/gen" -name '*.java')

"$TOOLS_DIR/d8" \
  --min-api 24 \
  --output "$BUILD_DIR/dex" \
  $(find "$BUILD_DIR/classes" -name '*.class')

"$TOOLS_DIR/aapt" package \
  -f \
  -M "$PROJECT_DIR/AndroidManifest.xml" \
  -S "$PROJECT_DIR/res" \
  -I "$ANDROID_JAR" \
  -F "$BUILD_DIR/unsigned.apk" \
  "$BUILD_DIR/dex"

"$TOOLS_DIR/zipalign" \
  -f \
  4 \
  "$BUILD_DIR/unsigned.apk" \
  "$BUILD_DIR/aligned.apk"

if [[ ! -f "$KEYSTORE" ]]; then
  keytool \
    -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

"$TOOLS_DIR/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$APK" \
  "$BUILD_DIR/aligned.apk"

"$TOOLS_DIR/apksigner" verify --verbose "$APK"
echo "$APK"
