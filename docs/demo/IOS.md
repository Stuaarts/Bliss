# iOS simulator validation

The first iOS gate compiles the normal Flutter app and exercises the real iOS
Keychain and AppAuth plugins in three separate simulator app processes. It uses
synthetic tokens and a loopback OIDC fixture, without demo-account credentials or
the marketplace backend. It does not establish real browser PKCE login,
identity-provider logout, account switching or marketplace UI acceptance.

## Reproduce on macOS

Install Xcode with an iOS Simulator runtime and Flutter 3.47.5, then run from the
repository root:

```sh
bash infrastructure/scripts/test-ios-session.sh
```

The script rejects other operating systems, builds the actual app first and
checks the built callback scheme `com.bliss.mobile`. It creates a disposable
`Bliss_Test_iOS_*` iPhone simulator, selecting an installed compatible runtime.
It never selects, erases or deletes an existing simulator. Its exit handler shuts
down and deletes only the UUID it created, including when a test fails.

The test entry point is [native_session_test.dart](../../apps/mobile/integration_test/native_session_test.dart).
The runner configures the fixture's phase-specific Dart defines, then executes
the same installed bundle through Flutter's native XCTest bridge in a new app
process. Test parallelization is disabled so Xcode uses the supplied simulator,
with no explicit uninstall or reset between phases:

1. `seed` refuses to overwrite an existing session, writes a synthetic refresh
   token to real secure storage and reads it back.
2. `restore` reads that token in a new process, uses the native AppAuth refresh
   request against an in-app loopback server, checks the grant/client/token,
   verifies rotation in Keychain and signs out locally.
3. `signed-out` starts another process, confirms the token was removed and
   checks that restoring a session produces no access token.

The same simulator is shut down and booted between phases, retaining the
installed app and Keychain. This also isolates simulator launch state between
the independent XCTest invocations. Each test command has a five-minute deadline.
A deadline or test failure fails the gate without an automatic retry, preserving
Xcode logs, result bundles and recent Runner simulator logs for diagnosis.
The script requires the expected phase's XCTest to pass and exactly one test
with zero failures, so an empty test bundle cannot satisfy the gate.
Build, configuration, boot and cleanup commands
also have deadlines; the native CI step has a 25-minute limit within its
30-minute job limit so artifact upload has time to run.

Runner's Debug, Profile and Release configurations use the same Keychain
[entitlements](../../apps/mobile/ios/Runner/Runner.entitlements). The empty
`keychain-access-groups` array follows the pinned
[secure-storage plugin setup](https://pub.dev/packages/flutter_secure_storage/versions/11.2.0).
No cross-app Keychain group, Apple signing team or distribution profile is added.

## CI and evidence

The [iOS workflow](../../.github/workflows/ios.yml) runs on standard `macos-15`
when mobile code, its runner script or workflow changes. It can also be run
manually after landing on main. It does not run again on the pull-request event;
the branch push supplies the iOS result for that commit. Concurrency cancels an
obsolete run, and the job has a 30-minute limit.

Each invocation writes a new directory under `.work/ios-session` so old phase
logs cannot be mistaken for evidence from a later failed run. Private CI
artifacts retain the commit, tool versions, selected simulator/runtime,
normal app build, three fixture-phase logs and XCTest result bundles for seven days. No real account
tokens, credentials or signed application archive belong in those artifacts.
The Windows workstation can check formatting, analysis and script syntax; only
the macOS execution supplies native iOS evidence.

Initial execution: [iOS simulator run](https://github.com/Stuaarts/Bliss/actions/runs/37884919565)
passed on October 9, 2026 at `8cce162`. The runner reported Xcode 16.4 / build
16F6, Flutter 3.47.5 and an iPhone 11 simulator using iOS 26.2. All evidence below
comes from that run's downloaded artifact and job log:

| Check | Observed result |
| --- | --- |
| Normal app, including Swift Package Manager plugins | Built `Runner.app`; Xcode build 42.0 seconds |
| Built bundle and callback scheme | `com.bliss.blissMobile` / `com.bliss.mobile` verified |
| `seed`, first process | One test passed using real secure storage |
| `restore`, second process | One test passed: persisted seed, native refresh, rotation and local logout |
| `signed-out`, third process | One test passed: token absent and no restored access token |

The next execution at `e3afdfd`, [run 37885539835](https://github.com/Stuaarts/Bliss/actions/runs/37885539835),
compiled the app in 36.4 seconds and passed `seed`, then stalled after the restore
build (18.8 seconds), before the restore test started. It was cancelled to recover
logs and is **not a passing fixture run**. The runner now reboots the same simulator
between phases, adds bounded command waits and retains launch diagnostics.

[Run 37886765697](https://github.com/Stuaarts/Bliss/actions/runs/37886765697)
at `0e7d2b4` passed seed and restore (including actual native refresh/rotation),
then timed out before signed-out's test body started. Verbose output shows a
launched app PID followed by an indefinite VM-service wait. The watchdog correctly
failed the job and retained diagnostics. Reboots alone did not make the Flutter
CLI launch channel reliable.

The current runner instead uses Flutter's official
[native XCTest bridge](https://github.com/flutter/flutter/blob/3.47.5/packages/integration_test/README.md#ios-device-testing),
implemented in [RunnerTests.m](../../apps/mobile/ios/RunnerTests/RunnerTests.m).
It replaces the template's empty test, uses the app host's static plugin linkage
and obtains the same Dart assertions directly through XCTest,
without waiting for Flutter CLI VM-service discovery. This revision needs its
own complete native pass before merge. The original successful run above used
three `flutter test --no-uninstall` invocations; its evidence is preserved,
separately from the subsequent launch failures and native-bridge result.

The first XCTest build at `49a4915` (run 37887805680) failed before tests because
the generated static plugin package had been linked to both Runner and RunnerTests.
The test target now uses Runner's existing static linkage, following
[Flutter's Swift package test guidance](https://docs.flutter.dev/packages-and-plugins/swift-package-manager/for-plugin-authors#how-to-update-unit-tests-in-a-plugins-example-app).
This build failure is not counted as native test evidence.

The corrected host linkage at `1d197d8` (run 37888126778) built and executed the
seed XCTest, which failed Flutter's semantics-handle invariant when iOS enabled
accessibility during the test. For the native XCTest runner only, setup now waits
up to 30 seconds for platform semantics before the tester records its handle
baseline. The listener is removed in finally, and normal leak checks remain
enabled. Storage and auth assertions are unchanged; a timeout still fails the gate.

## Remaining acceptance under #7/#12

- Real Keycloak browser sign-in and callback return for customer and provider.
- Process-death session recovery, background/resume, identity-provider logout
  and account switching with the expected capabilities and no stale private data.
- The actual request-to-review marketplace UI, chat and simulated settlement
  against the real API, plus direct cancellation, dispute and connection recovery.
- Physical-device and signed-distribution validation are separate from simulator
  acceptance; neither is implied by a successful simulator job.
