# Reproducible native iOS checks

The native scripts create disposable iPhone simulators from installed runtimes.
`ios_simulator.py` selects an available iOS runtime with the same major/minor
release as `xcrun --sdk iphonesimulator --show-sdk-version`, preferring the highest
patch in that release. Its device type must support that runtime. If the matching
runtime is missing, the check fails with an installation message. It does not
download a runtime, silently choose another release, or use an existing device.

This is a reproducibility policy. Hosted evidence showed Xcode 16.4 building with
the 18.5 SDK while the old maximum-version selector chose iOS 26.2. Apple's
[SDK matrix](https://developer.apple.com/xcode/system-requirements) lists iOS 18.5
for Xcode 16.4. The version difference alone does not prove the cause of simulator
startup or launch stalls. The selected SDK and runtime are recorded for diagnosis;
a successful check establishes acceptance only for that selected configuration.

Both `test-ios-oidc.py` (real browser/marketplace) and `test-ios-session.sh`
(synthetic session persistence) use the same selector. Shared-helper changes
trigger all three native workflows. The ordinary mobile CI job first runs nine
platform-independent selection/CLI tests, including 18.5 alongside newer 26.2,
patch preference, unavailable/wrong-platform refusal, compatible device ranges,
missing-runtime diagnostics and paths containing spaces.

## Deadlines and authentication

Flutter configuration allows 300 seconds for cold Swift-package resolution and
Xcode project enumeration; expiry still fails the check. Inventory is bounded to
180 seconds. Browser credential-form discovery allows
90 seconds; combined browser/marketplace build and XCTest allows 900 seconds.
Boot readiness remains bounded to 180 seconds. The independent session workflow
retains its 300-second per-phase XCTest command deadline and all three separate
processes; app data and Keychain survive the owned simulator reboots. Existing
workflow/job deadlines remain in force. Expiry fails the gate and does not
silently retry a scenario.

The browser helper declines only the observed Save Password? / Not Now prompt
while awaiting the authenticated profile. It still requires the real local
Keycloak form, realm identity, credentials, profile/capabilities, refresh, logout
and account switching. No cookie clearing, token injection or silent-login bypass
is added. Only the simulator created by the invocation is shut down/deleted.

## Verification

On any supported Python host, from the repository root:

```sh
python3 -m unittest discover -s infrastructure/scripts/tests -p 'test_ios_simulator.py'
python3 infrastructure/scripts/test-ios-oidc.py --help
bash -n infrastructure/scripts/test-ios-session.sh
```

These verify selection policy, imports/CLI and shell syntax. Actual simulator,
Keychain and browser acceptance requires macOS and the native workflows. Earlier
hosted failures include inventory/boot deadlines, restore launch stalls and an
optional password-save modal. Consult the current PR and #7 backlog for exact-head
results; source adjustments and prior passes do not turn a failed run into a pass.

See [session acceptance](../demo/IOS.md), [browser authentication](../demo/IOS_AUTH.md),
[marketplace acceptance](../demo/IOS_MARKETPLACE.md) and
[client maintenance behavior](CLIENT_MAINTENANCE.md). Remaining secondary iOS,
actual maintenance UI, broader OS/device coverage and release gates stay open.
