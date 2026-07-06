#!/usr/bin/env bash
#
# scripts/setup-android-sdk.sh
# One-time setup for Android command line tools on Linux (Artix/Arch)
# Run this from the steady project:  ./scripts/setup-android-sdk.sh

set -e

echo "=== Android SDK Setup for Steady (Linux only) ==="
echo

if ! command -v wget >/dev/null || ! command -v unzip >/dev/null; then
  echo "Installing wget and unzip first..."
  sudo pacman -S --needed wget unzip
fi

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
echo "ANDROID_HOME will be: $ANDROID_HOME"
echo

mkdir -p "$ANDROID_HOME/cmdline-tools"
cd "$ANDROID_HOME/cmdline-tools"

echo "Downloading command line tools..."
wget -c https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip

echo "Extracting..."
unzip -o cmdline-tools.zip
rm -f cmdline-tools.zip

# The zip always creates a "cmdline-tools" folder. We need it as "latest"
if [ -d cmdline-tools ]; then
  echo "Moving into 'latest' folder..."
  rm -rf latest
  mv cmdline-tools latest
fi

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  echo "ERROR: sdkmanager not found after extraction."
  exit 1
fi

echo
echo "Accepting licenses..."
yes | "$SDKMANAGER" --licenses || true

echo
echo "Installing required packages..."
"$SDKMANAGER" \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;34.0.0" \
  "build-tools;35.0.0"

echo
echo "Writing local.properties in the project..."
echo "sdk.dir=$ANDROID_HOME" > "$(dirname "$0")/../local.properties"

echo
echo "=== Done! ==="
echo "Now run from the project root:"
echo "  ./build.sh"
echo
echo "If you still get license errors, run this again:"
echo "  yes | $SDKMANAGER --licenses"
