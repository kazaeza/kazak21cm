#!/usr/bin/env bash
# Idempotent Android SDK bootstrap for headless Linux (cloud sessions, CI-like boxes).
# Usage: muzykalka/scripts/setup-android-sdk.sh   (run from anywhere)
# Installs into $ANDROID_HOME (default: $HOME/android-sdk) and writes muzykalka/local.properties.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_ZIP="commandlinetools-linux-16111833_latest.zip"
PACKAGES=("platform-tools" "platforms;android-37.0" "build-tools;37.0.0")
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

mkdir -p "$SDK"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo ">> downloading Android command-line tools"
  tmp="$(mktemp -d)"
  curl -sSL --retry 4 -o "$tmp/cmdline.zip" "https://dl.google.com/android/repository/$CMDLINE_ZIP"
  unzip -q "$tmp/cmdline.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null || true
"$SDKMANAGER" --sdk_root="$SDK" --install "${PACKAGES[@]}" >/dev/null

echo "sdk.dir=$SDK" > "$PROJECT_DIR/local.properties"
echo ">> Android SDK ready at $SDK (local.properties written)"
