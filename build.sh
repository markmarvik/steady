#!/usr/bin/env bash
#
# build.sh - Smart wrapper for building Steady on Artix Linux (pure CLI)
# Usage:
#   ./build.sh                  # clean + assembleDebug (default)
#   ./build.sh assembleDebug
#   ./build.sh clean build
#   ./build.sh --help
#
# Handles JAVA_HOME automatically and gives clear guidance for Android SDK.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

info()  { echo -e "${BLUE}[build.sh]${NC} $*"; }
warn()  { echo -e "${YELLOW}[build.sh]${NC} $*"; }
error() { echo -e "${RED}[build.sh]${NC} $*" >&2; }
success() { echo -e "${GREEN}[build.sh]${NC} $*"; }

show_help() {
  cat <<EOF
Steady build helper for Artix Linux (pure terminal — no Android Studio needed)

Usage:
  ./build.sh [gradle args...]

Examples:
  ./build.sh
  ./build.sh clean assembleDebug
  ./build.sh installDebug

What it does:
  - Finds or installs Java 17
  - Sets JAVA_HOME
  - Checks your Android SDK
  - Prints Linux-specific instructions if licenses or packages are missing
  - Runs ./gradlew for you

Important:
  - We use the Linux tool called "sdkmanager" (an executable).
  - Ignore any "sdkmanager.bat" messages — those are only for Windows.
  - Licenses are accepted with:  yes | sdkmanager --licenses

If you get license errors, just run the commands that build.sh prints.
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

# 1. Ensure we have a Java 17 JDK
find_or_install_java() {
  # If JAVA_HOME is already valid, use it
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    info "Using existing JAVA_HOME=$JAVA_HOME"
    return 0
  fi

  # Try common Artix/Arch locations for java-17-openjdk
  local candidates=(
    "/usr/lib/jvm/java-17-openjdk"
    "/usr/lib/jvm/default"
    "/usr/lib/jvm/default-runtime"
    "/opt/java-17-openjdk"
    "$HOME/.sdkman/candidates/java/current"
  )

  for cand in "${candidates[@]}"; do
    if [[ -x "$cand/bin/java" ]]; then
      # Verify it's at least Java 17
      if "$cand/bin/java" -version 2>&1 | grep -q 'version "17'; then
        export JAVA_HOME="$cand"
        info "Detected JDK 17 at $JAVA_HOME"
        return 0
      fi
    fi
  done

  # Not found — try to install on Artix/Arch
  if command -v pacman >/dev/null 2>&1; then
    warn "Java 17 not found. Installing jdk17-openjdk via pacman..."
    sudo pacman -S --needed --noconfirm jdk17-openjdk
    # After install, the path is reliable
    if [[ -x /usr/lib/jvm/java-17-openjdk/bin/java ]]; then
      export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
      success "Installed and using JAVA_HOME=$JAVA_HOME"
      return 0
    fi
  fi

  # Last resort: search PATH
  if command -v java >/dev/null 2>&1; then
    local java_bin
    java_bin="$(command -v java)"
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$java_bin")")")"
    export JAVA_HOME
    warn "Falling back to JAVA_HOME=$JAVA_HOME (from PATH)"
    return 0
  fi

  error "Could not find or install Java 17."
  echo
  echo "On Artix Linux, run:"
  echo "  sudo pacman -S jdk17-openjdk"
  echo "  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk"
  echo
  echo "Then re-run this script."
  exit 1
}

# 2. Android SDK check (non-fatal, gradle will give a good error if missing)
check_android_sdk() {
  local sdk_dir=""
  local needs_sdk=false

  # Only be noisy about SDK for actual build tasks
  for arg in "$@"; do
    if [[ "$arg" == *assemble* || "$arg" == *build* || "$arg" == *install* || "$arg" == *apk* ]]; then
      needs_sdk=true
      break
    fi
  done

  if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME" ]]; then
    sdk_dir="$ANDROID_HOME"
  elif [[ -f local.properties ]]; then
    sdk_dir="$(grep '^sdk.dir=' local.properties | cut -d= -f2- || true)"
  fi

  if [[ -z "$sdk_dir" || ! -d "$sdk_dir/cmdline-tools" ]]; then
    if $needs_sdk; then
      warn "Android SDK not found or not configured."
      echo
      echo "Copy and edit the example:"
      echo "  cp local.properties.example local.properties"
      echo "  # then edit sdk.dir=..."
      echo
      echo "One-time Android SDK setup on Artix Linux (NO Android Studio needed):"
      cat <<'EOT'
# 1. Install basic tools (if missing)
sudo pacman -S --needed wget unzip

# 2. Download command-line tools (only the first time)
export ANDROID_HOME=$HOME/android-sdk
mkdir -p $ANDROID_HOME/cmdline-tools
cd $ANDROID_HOME/cmdline-tools

wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
unzip -q cmdline-tools.zip
mv cmdline-tools latest

# 3. Accept licenses + install required packages
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses

$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;34.0.0" \
  "build-tools;35.0.0"

# 4. Tell the project where your SDK lives
echo "sdk.dir=$ANDROID_HOME" > ~/steady/local.properties
EOT
      echo
      echo "After that, just run from the project:"
      echo "  cd ~/steady"
      echo "  ./build.sh"
      echo
      echo "Important: We use the Linux 'sdkmanager' tool."
      echo "Ignore any mentions of sdkmanager.bat — that is only for Windows."
    else
      warn "Android SDK not configured (ok for this command)"
    fi
  else
    success "Android SDK detected at $sdk_dir"

    # Detect common broken layout (only local.properties inside cmdline-tools)
    if [[ -f "$sdk_dir/cmdline-tools/local.properties" && ! -d "$sdk_dir/cmdline-tools/latest" ]]; then
      warn "Your Android SDK layout is broken (cmdline-tools is not properly installed)."
      echo
      echo "Please run the setup script:"
      echo "  ./scripts/setup-android-sdk.sh"
      echo
      echo "Or do it manually (copy these commands):"
      cat <<EOT
export ANDROID_HOME=$sdk_dir
cd \$ANDROID_HOME/cmdline-tools

wget -c https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
unzip -o cmdline-tools.zip
rm -f cmdline-tools.zip
rm -rf latest
mv cmdline-tools latest

yes | \$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses

\$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;34.0.0" \
  "build-tools;35.0.0"

echo "sdk.dir=$sdk_dir" > ~/steady/local.properties
EOT
      echo
      return 0
    fi

    # Normal case - check licenses
    ensure_android_licenses "$sdk_dir"
  fi
}

# Helper: make sure the needed SDK licenses are accepted (Linux version)
ensure_android_licenses() {
  local sdk_dir="$1"
  local sdkmanager="$sdk_dir/cmdline-tools/latest/bin/sdkmanager"

  if [[ ! -x "$sdkmanager" ]]; then
    return 0
  fi

  # Check if common license files exist
  if [[ -f "$sdk_dir/licenses/android-sdk-license" && -f "$sdk_dir/licenses/android-sdk-preview-license" ]]; then
    return 0
  fi

  warn "Some Android SDK licenses are not accepted yet."
  echo
  echo "This is normal the first time. Run these commands:"
  echo
  cat <<EOT
export ANDROID_HOME=$sdk_dir
yes | $sdkmanager --licenses

$sdkmanager \
  "platforms;android-35" \
  "build-tools;34.0.0" \
  "build-tools;35.0.0"
EOT
  echo
  echo "Then come back and run:"
  echo "  cd ~/steady && ./build.sh"
  echo
  echo "Note: On Linux we use 'sdkmanager' (not sdkmanager.bat — that's only for Windows)."
  echo
}


# 3. Main
find_or_install_java

# Make sure java is on PATH for gradle
export PATH="$JAVA_HOME/bin:$PATH"

# Make gradlew executable if it isn't
if [[ -f gradlew && ! -x gradlew ]]; then
  chmod +x gradlew
fi

# Default task if none given
if [[ $# -eq 0 ]]; then
  info "No arguments given — defaulting to: clean assembleDebug"
  set -- clean assembleDebug
fi

check_android_sdk "$@"

info "JAVA_HOME=$JAVA_HOME"
info "Running: ./gradlew $*"

exec ./gradlew "$@"
