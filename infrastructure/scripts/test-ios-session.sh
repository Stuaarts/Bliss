#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != Darwin ]]; then
  echo "This check requires macOS, Xcode and an installed iOS Simulator runtime." >&2
  exit 1
fi
for tool in flutter xcrun python3; do
  command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 1; }
done

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
evidence="$root/.work/ios-session"
mkdir -p "$evidence"
cd "$root/apps/mobile"

xcodebuild -version | tee "$evidence/xcode.txt"
flutter --version | tee "$evidence/flutter.txt"
git rev-parse HEAD | tee "$evidence/commit.txt"
flutter pub get

# Compile the actual app before building the separate synthetic test entry point.
flutter build ios --simulator --debug 2>&1 | tee "$evidence/app-build.log"
python3 - <<'PY'
import plistlib
from pathlib import Path

with Path('build/ios/iphonesimulator/Runner.app/Info.plist').open('rb') as source:
    info = plistlib.load(source)
schemes = [scheme for item in info.get('CFBundleURLTypes', [])
           for scheme in item.get('CFBundleURLSchemes', [])]
assert 'com.bliss.mobile' in schemes, 'AppAuth callback scheme is missing'
assert info['CFBundleIdentifier'] == 'com.bliss.blissMobile'
print('Built app callback registration verified.')
PY

# Always create a disposable simulator; never select or erase an existing one.
xcrun simctl list --json > "$evidence/simulator-inventory.json"
selection="$(python3 - "$evidence/simulator-inventory.json" <<'PY'
import json
import sys

with open(sys.argv[1]) as source:
    inventory = json.load(source)
runtimes = [item for item in inventory['runtimes']
            if item.get('isAvailable') and '.iOS-' in item['identifier']]
if not runtimes:
    sys.exit('No available iOS Simulator runtime; install one through Xcode.')
runtime = max(runtimes, key=lambda item: tuple(map(int, item['version'].split('.'))))
parts = list(map(int, runtime['version'].split('.')))
parts += [0] * (3 - len(parts))
version = (parts[0] << 16) | (parts[1] << 8) | parts[2]
types = [item for item in inventory['devicetypes']
         if item['name'].startswith('iPhone')
         and item.get('minRuntimeVersion', 0) <= version
         and item.get('maxRuntimeVersion', 0xFFFFFFFF) >= version]
if not types:
    sys.exit('No iPhone simulator device type found.')
print(types[-1]['identifier'], runtime['identifier'])
PY
)"
read -r device_type runtime <<< "$selection"
device="$(xcrun simctl create "Bliss_Test_iOS_$$" "$device_type" "$runtime")"
[[ "$device" =~ ^[A-Fa-f0-9-]{36}$ ]] || { echo "Invalid created simulator UUID" >&2; exit 1; }
cleanup() {
  xcrun simctl shutdown "$device" >/dev/null 2>&1 || true
  xcrun simctl delete "$device" || true
}
trap cleanup EXIT
printf 'device=%s\ntype=%s\nruntime=%s\n' "$device" "$device_type" "$runtime" | tee "$evidence/device.txt"
xcrun simctl boot "$device"
xcrun simctl bootstatus "$device" -b

for phase in seed restore signed-out; do
  # The same app data and Keychain must survive all three independent processes.
  flutter test integration_test/native_session_test.dart -d "$device" \
    --no-uninstall --reporter expanded \
    --dart-define=OIDC_ISSUER=http://localhost:18180 \
    --dart-define=NATIVE_TEST_PHASE="$phase" 2>&1 | tee "$evidence/$phase.log"
done
echo "PASS: iOS app compile, callback registration, Keychain persistence, AppAuth refresh/rotation and logout."
echo "Real browser PKCE, identity-provider logout and marketplace UI are separate checks."
