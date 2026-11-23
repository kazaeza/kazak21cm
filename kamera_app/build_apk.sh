#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
BUILD_DIR="$ROOT_DIR/build"
SDK_DIR="${ANDROID_HOME:-/opt/android-sdk}"
BUILD_TOOLS="$SDK_DIR/build-tools/35.0.0"
PLATFORM_JAR="$SDK_DIR/platforms/android-35/android.jar"

KEYSTORE="${KAMERA_KEYSTORE:-$HOME/.android/kamera_dev.jks}"
KEY_ALIAS="kamera"
KEY_PASS="kamera2026"
OUT_APK="$ROOT_DIR/kamera-v0.3.apk"

echo "==> Cleaning build dir..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/obj" "$BUILD_DIR/dex" "$BUILD_DIR/res_flat"

echo "==> Generating launcher icons..."
python3 "$ROOT_DIR/generate_icons.py"

echo "==> Compiling Android resources with aapt2..."
"$BUILD_TOOLS/aapt2" compile \
  --dir "$ANDROID_DIR/res" \
  -o "$BUILD_DIR/res_flat/resources.zip"

echo "==> Linking resources & assets into base APK..."
"$BUILD_TOOLS/aapt2" link \
  -o "$BUILD_DIR/unaligned.apk" \
  -I "$PLATFORM_JAR" \
  --manifest "$ANDROID_DIR/AndroidManifest.xml" \
  -A "$ANDROID_DIR/assets" \
  --java "$BUILD_DIR/gen" \
  --min-sdk-version 24 \
  --target-sdk-version 35 \
  --version-code 3 \
  --version-name "0.3.0" \
  "$BUILD_DIR/res_flat/resources.zip"

echo "==> Compiling Java sources..."
javac \
  -source 11 -target 11 \
  -classpath "$PLATFORM_JAR" \
  -d "$BUILD_DIR/obj" \
  "$BUILD_DIR/gen/com/kamera/curing/R.java" \
  "$ANDROID_DIR/src/com/kamera/curing/MainActivity.java"

echo "==> Converting bytecode to DEX with d8..."
"$BUILD_TOOLS/d8" \
  --min-api 24 \
  --lib "$PLATFORM_JAR" \
  --output "$BUILD_DIR/dex" \
  $(find "$BUILD_DIR/obj" -name "*.class")

echo "==> Adding classes.dex into APK..."
(cd "$BUILD_DIR/dex" && zip -q -u "$BUILD_DIR/unaligned.apk" classes.dex)

echo "==> Running zipalign..."
"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  echo "==> Creating signing keystore at $KEYSTORE..."
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE" \
    -storepass "$KEY_PASS" \
    -keypass "$KEY_PASS" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=KAMERA App, OU=Craft, O=Kamera, L=Moscow, S=Moscow, C=RU"
fi

echo "==> Signing APK with apksigner (v1 + v2 + v3)..."
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEY_PASS" \
  --key-pass "pass:$KEY_PASS" \
  --out "$OUT_APK" \
  "$BUILD_DIR/aligned.apk"

echo "==> Verifying APK signature..."
"$BUILD_TOOLS/apksigner" verify --verbose "$OUT_APK"

ls -lh "$OUT_APK"
echo "==> Done! APK built at: $OUT_APK"
