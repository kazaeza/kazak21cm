#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
BUILD_DIR="$ANDROID_DIR/build"
SDK_DIR="${ANDROID_HOME:-/opt/android-sdk}"
BUILD_TOOLS="$SDK_DIR/build-tools/35.0.0"
ANDROID_JAR="$SDK_DIR/platforms/android-35/android.jar"

AAPT2="$BUILD_TOOLS/aapt2"
AAPT="$BUILD_TOOLS/aapt"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

OUT_DIR="$ROOT_DIR/releases"
FINAL_APK="$OUT_DIR/nightcap-v1.3.0.apk"
KEYSTORE="$ANDROID_DIR/nightcap-signing.jks"
KEY_ALIAS="nightcap"
KEY_PASS="nightcap2026"

echo "==> [1/7] Building offline web bundle with Bun..."
bun run "$ROOT_DIR/scripts/build-web.ts"

echo "==> [2/7] Generating cosmic Android launcher icons..."
if [ ! -f "$ANDROID_DIR/res/mipmap-xxxhdpi/ic_launcher_background.png" ]; then
  python3 "$ROOT_DIR/scripts/generate-icons.py"
fi

echo "==> [3/7] Preparing Android build directories & assets..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/compiled-res" "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/assets/www" "$OUT_DIR"
cp -R "$ROOT_DIR/dist/." "$BUILD_DIR/assets/www/"

echo "==> [4/7] Compiling & linking Android resources with aapt2..."
"$AAPT2" compile --dir "$ANDROID_DIR/res" -o "$BUILD_DIR/compiled-res/resources.zip"

"$AAPT2" link \
  -I "$ANDROID_JAR" \
  --manifest "$ANDROID_DIR/AndroidManifest.xml" \
  --min-sdk-version 26 \
  --target-sdk-version 35 \
  --version-code 5 \
  --version-name "1.3.0" \
  --java "$BUILD_DIR/gen" \
  -A "$BUILD_DIR/assets" \
  -o "$BUILD_DIR/app-unsigned.apk" \
  "$BUILD_DIR/compiled-res/resources.zip"

echo "==> [5/7] Compiling MainActivity.java & R.java -> classes.dex..."
find "$ANDROID_DIR/src" "$BUILD_DIR/gen" -name "*.java" > "$BUILD_DIR/sources.txt"
javac \
  -source 11 \
  -target 11 \
  -encoding UTF-8 \
  -classpath "$ANDROID_JAR" \
  -d "$BUILD_DIR/classes" \
  @"$BUILD_DIR/sources.txt"

CLASS_FILES=()
while IFS= read -r -d '' f; do
  CLASS_FILES+=("$f")
done < <(find "$BUILD_DIR/classes" -name "*.class" -print0)

"$D8" \
  --min-api 26 \
  --lib "$ANDROID_JAR" \
  --output "$BUILD_DIR/dex" \
  "${CLASS_FILES[@]}"

echo "==> [6/7] Packaging classes.dex & zipaligning APK..."
python3 - "$BUILD_DIR/app-unsigned.apk" "$BUILD_DIR/dex/classes.dex" <<'PYEOF'
import sys, zipfile
apk_path, dex_path = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk_path, 'a', compression=zipfile.ZIP_DEFLATED) as zf:
    zf.write(dex_path, 'classes.dex')
PYEOF

"$ZIPALIGN" -p -f 4 "$BUILD_DIR/app-unsigned.apk" "$BUILD_DIR/app-aligned.apk"

echo "==> [7/7] Signing & verifying APK..."
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$KEY_PASS" \
    -keypass "$KEY_PASS" \
    -dname "CN=Nightcap, OU=Mobile, O=Kazaeza, L=Moscow, C=RU"
fi

"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEY_PASS" \
  --key-pass "pass:$KEY_PASS" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --v4-signing-enabled false \
  --out "$FINAL_APK" \
  "$BUILD_DIR/app-aligned.apk"

"$APKSIGNER" verify --verbose "$FINAL_APK"
echo "--- APK Metadata ---"
"$AAPT" dump badging "$FINAL_APK" | head -n 12
ls -lh "$FINAL_APK"
echo "✅ Built ready-to-install APK: $FINAL_APK"
