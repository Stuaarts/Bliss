# Real iOS browser authentication

This gate drives the normal Flutter app through Apple's authentication browser
using a separate XCUITest process. It requires real Keycloak browser credentials
and an authenticated Bliss API profile; synthetic storage tokens cannot satisfy it.
Native execution is pending until the new macOS CI run passes.

## Reproduce on macOS

Install Java 21, Flutter 3.47.5, Xcode with an iOS Simulator runtime and Homebrew
PostgreSQL 17 tools (`brew install postgresql@17`). From the repository root:

```sh
python3 infrastructure/scripts/test-ios-oidc.py
```

The runner refuses non-macOS hosts and occupied local ports. It creates a fresh
PostgreSQL cluster/database, downloads the pinned Keycloak 26.7.4 distribution
and verifies its SHA-256 against the publisher's release digest, then imports
the existing Bliss realm template with generated disposable customer/provider
credentials. It packages and starts the actual Quarkus API with its local demo
profile, migrations and seed data. All services bind loopback; the Windows
development stack is neither contacted nor exposed. No Docker daemon is required.
Both packaging and runtime use the local profile because Quarkus fixes Flyway
locations at build time. A database read verifies the expected provider seed
before any browser test starts.

The test uses only a newly created `Bliss_Auth_iOS_*` simulator. It builds
`lib/main.dart` with the loopback API/issuer defines and the separate `BlissAuth`
scheme. The existing synthetic [native session gate](IOS.md) remains separate.
There is no test auth adapter, token injection, password grant, browser bypass,
app data reset or Keychain deletion between test steps.

## Required observations

1. Customer signs in through the rendered Bliss Keycloak form and returns to
   their authenticated profile without provider management controls.
2. Terminate Bliss, verify the process stopped, relaunch and recover the customer
   profile through the stored refresh token without another browser login.
3. Background and activate the existing app, retaining the same profile.
4. Complete identity-provider logout; terminate/relaunch and remain signed out.
5. Require a fresh credential form for provider sign-in, then verify the seeded
   Alex Demo profile and provider management capability.
6. Terminate/relaunch and recover the provider profile.
7. Log out, require fresh customer credentials and verify provider controls and
   the old provider profile are absent after switching accounts.
8. Log out again; terminate/relaunch and remain signed out.

The runner requires the named XCTest, exactly one successful test, all eight
checkpoints and their post-authentication screenshots. Commands have deadlines;
failures are not automatically retried. Cleanup stops only the services and
simulator created by this invocation and removes its private credential/state
directory. Existing databases and simulators are never reset.

## Evidence and boundaries

The [workflow](../../.github/workflows/ios-auth.yml) runs on path-scoped pushes
using a standard macOS runner and can be dispatched manually after landing on
main. Private artifacts under `.work/ios-oidc` retain the commit, tool versions,
service readiness, sanitized logs and only explicitly named post-authentication
screenshots for seven days. Raw XCTest bundles, automatic browser screenshots,
videos, realm imports, database contents and credentials are excluded. Generated
passwords and token-shaped values are redacted from retained text logs; CI masks
the generated passwords as well.

Real marketplace UI acceptance remains a following slice under #7/#12. Physical
devices, release signing and distribution are separate. A successful browser
test does not establish those results.

## Execution history

The first native run at `a51b530`,
[37943867260](https://github.com/Stuaarts/Bliss/actions/runs/37943867260), compiled
the new XCUITest target and ran real browser authentication. Customer login,
process recovery, background/resume and logout/restart checkpoints passed. Provider
login returned a valid profile named `provider`, failing the expected seeded
`Alex Demo` assertion. API logs confirmed the cause: packaging used the default
profile, so the runtime local profile could not add the build-time Flyway demo
location. This is a failing attempt, not full browser acceptance. The runner now
packages with the local profile and verifies the seed before native execution.
