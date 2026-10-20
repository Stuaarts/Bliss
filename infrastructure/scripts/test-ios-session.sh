#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != Darwin ]]; then
  echo "This check requires macOS, Xcode and an installed iOS Simulator runtime." >&2
  exit 1
fi
for tool in flutter xcrun python3; do
  command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 1; }
done

# Flutter can wait indefinitely for a simulator launch/VM-service connection.
# Bound the whole command, including that pre-test wait, and kill only its group.
run_bounded() {
  python3 - "$@" <<'PY'
import os
import signal
import subprocess
import sys

process = subprocess.Popen(sys.argv[2:], start_new_session=True)
try:
    result = process.wait(timeout=int(sys.argv[1]))
except subprocess.TimeoutExpired:
    print(f'Command exceeded {sys.argv[1]} seconds: {sys.argv[2]}', file=sys.stderr, flush=True)
    try:
        os.killpg(process.pid, signal.SIGTERM)
        process.wait(timeout=10)
    except (ProcessLookupError, subprocess.TimeoutExpired):
        pass
    finally:
        # A child may ignore TERM even if its parent already exited.
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait()
    sys.exit(124)
sys.exit(result if result >= 0 else 128 - result)
PY
}

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
mkdir -p "$root/.work/ios-session"
evidence="$(mktemp -d "$root/.work/ios-session/run-XXXXXX")"
cd "$root/apps/mobile"

xcodebuild -version | tee "$evidence/xcode.txt"
flutter --version | tee "$evidence/flutter.txt"
git rev-parse HEAD | tee "$evidence/commit.txt"
run_bounded 300 flutter pub get

# Compile the actual app before building the separate synthetic test entry point.
run_bounded 600 flutter build ios --simulator --debug 2>&1 | tee "$evidence/app-build.log"
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
run_bounded 30 xcrun --sdk iphonesimulator --show-sdk-version > "$evidence/simulator-sdk.txt"
run_bounded 180 xcrun simctl list --json > "$evidence/simulator-inventory.json"
selection="$(python3 "$root/infrastructure/scripts/ios_simulator.py" \
  "$evidence/simulator-inventory.json" "$evidence/simulator-sdk.txt")"
read -r device_type runtime <<< "$selection"
device="$(xcrun simctl create "Bliss_Test_iOS_$$" "$device_type" "$runtime")"
[[ "$device" =~ ^[A-Fa-f0-9-]{36}$ ]] || { echo "Invalid created simulator UUID" >&2; exit 1; }
cleanup() {
  run_bounded 60 xcrun simctl shutdown "$device" >/dev/null 2>&1 || true
  run_bounded 60 xcrun simctl delete "$device" || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
printf 'device=%s\ntype=%s\nruntime=%s\n' "$device" "$device_type" "$runtime" | tee "$evidence/device.txt"
for phase in seed restore signed-out; do
  run_bounded 120 flutter build ios --config-only --simulator --debug \
    --target integration_test/native_session_test.dart \
    --dart-define=OIDC_ISSUER=http://localhost:18180 \
    --dart-define=NATIVE_XCTEST=true \
    --dart-define=NATIVE_TEST_PHASE="$phase" 2>&1 | tee "$evidence/$phase-config.log"
  run_bounded 60 xcrun simctl boot "$device"
  run_bounded 180 xcrun simctl bootstatus "$device" -b
  # The same app data and Keychain must survive all three independent processes.
  # XCTest reads integration_test results natively, without VM-service discovery.
  if ! run_bounded 300 xcodebuild -workspace ios/Runner.xcworkspace -scheme Runner \
    -configuration Debug -destination "platform=iOS Simulator,id=$device" \
    -destination-timeout 60 -parallel-testing-enabled NO -only-testing:RunnerTests \
    -resultBundlePath "$evidence/$phase.xcresult" test 2>&1 | tee "$evidence/$phase.log"; then
    run_bounded 30 xcrun simctl spawn "$device" log show --last 2m \
      --predicate 'process == "Runner"' > "$evidence/$phase-simulator.log" 2>&1 || true
    exit 1
  fi
  python3 - "$phase" "$evidence/$phase.log" <<'PY'
from pathlib import Path
import re
import sys

phase = {'seed': 'Seed', 'restore': 'Restore', 'signed-out': 'SignedOut'}[sys.argv[1]]
log = Path(sys.argv[2]).read_text()
name = 'testNativeSessionProcessPhase' + phase
assert re.search(r'Test Case .*' + name + r'.* passed', log), 'Expected native phase did not pass'
assert 'Executed 1 test, with 0 failures' in log, 'Expected exactly one successful native test'
print(f'Verified XCTest result: {name}')
PY
  # Reboot this same simulator to avoid carrying launch state into the next run.
  # This preserves the installed app and Keychain; no uninstall/reset is allowed.
  run_bounded 60 xcrun simctl shutdown "$device"
done
echo "PASS: iOS app compile, callback registration, Keychain persistence, AppAuth refresh/rotation and logout." | tee "$evidence/result.txt"
echo "Real browser PKCE, identity-provider logout and marketplace UI are separate checks."
