# iOS marketplace acceptance

This scenario drives the normal Flutter app through real Keycloak browser login,
customer request creation, a provider quote, quote acceptance, two-way booking
chat, scheduled work, customer confirmation, simulated payment capture and review
publication. It reopens the booking/chat after process termination, reopens the
saved review, backgrounds/resumes and terminates/relaunches again before logout.

## Reproduce

On macOS with Xcode/iOS Simulator, Java 21, Flutter 3.47.5 and Homebrew PostgreSQL
17 tools installed, run from the repository root:

```sh
python3 infrastructure/scripts/test-ios-oidc.py --scenario marketplace
```

The shared [browser runner](IOS_AUTH.md) creates its own loopback PostgreSQL
cluster, pinned Keycloak realm, packaged API and new simulator. Generated
customer/provider credentials belong only to that run. Business mutations go
through the native UI; no token injection, password grant, direct API mutation,
SQL mutation, clock adjustment or app-data reset is used to advance the scenario.
The normal two-minute schedule is respected. A generated title suffix identifies
the run in its fresh database.

The runner requires exactly one successful named XCTest, nine acceptance markers,
nine named post-authentication screenshots and read-only database corroboration:
one completed booking with accepted request/quote, agreed CAD 85.50 price,
the same workflow correlation observed with three persisted task checkpoints
during the scheduled human wait, simulated captured payment with one capture transaction,
the five-star saved review and the expected chat from two distinct senders.

The [workflow](../../.github/workflows/ios-marketplace.yml) retains sanitized logs
and named screenshots in the private `ios-marketplace-evidence` artifact for
seven days. Raw XCTest bundles/browser captures and generated credentials are
excluded and removed with owned service/simulator state. The authentication-only
and synthetic session gates remain separate and must continue passing.

## Status

First native execution pending. This document describes the required scenario;
it is not yet evidence of a passing iOS marketplace flow. #7/#12 remain open.
Secondary cancellation/dispute/connection-recovery paths, physical devices and
signed distribution remain separate acceptance gates.

## Execution history

The first attempt (`37952486236`, `0bb9a85`) created the request and quote but
failed a chat lookup: iOS exposes the displayed SelectableText body as a row value.
The lookup now checks displayed text/row labels and values, excluding the composer.
The second attempt (`37953684385`, `4c39f93`) passed the entire UI scenario in
359.194 seconds with nine checkpoints. Its final database check failed because
it expected an active workflow row after completion. Flow intentionally removes
completed instance/task rows; Bliss retains the booking's correlation and audit
history. The runner now observes the real scheduled human wait and requires that
same persisted correlation at completion. Neither failed job counts as full acceptance.
