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
trigger all three native workflows. The ordinary mobile CI job first runs fifteen
Python tests: nine selection/CLI cases, including 18.5 alongside newer 26.2,
patch preference, unavailable/wrong-platform refusal, compatible device ranges,
missing-runtime diagnostics and paths containing spaces, plus three diagnostic
redaction cases for full/truncated secrets, JWTs and protocol query strings, and
three login-page readiness cases (fresh PKCE GET, incomplete/wrong realm refusal,
and redirect/error refusal).

## Deadlines and authentication

Flutter configuration allows 300 seconds for cold Swift-package resolution and
Xcode project enumeration; expiry still fails the check. Inventory is bounded to
180 seconds. Browser credential-form discovery allows
90 seconds and requires the realm heading, username/password fields and named
submit button before credentials are typed. The submit button is resolved again
across live browser surfaces after typing, within 30 seconds. This avoids a
single immediate lookup against a stale or loading accessibility hierarchy.
Browser/marketplace compilation uses bounded 600-second
`build-for-testing`, then bounded 900-second `test-without-building` on the same
scheme/destination. See [Apple's command-line test actions](https://developer.apple.com/library/archive/technotes/tn2339/_index.html).
The disposable JVM services start after compilation and establish fresh readiness
before UI execution, avoiding competition with cold Xcode builds. The
runner then performs one bounded 120-second host-only authorization-page GET to
initialize the cold Keycloak route/theme. It requires HTTP 200, the Bliss realm
heading, username/password inputs and a submit control. The request has a fresh
PKCE challenge/state/nonce and submits no credentials. A separate opener refuses
redirects and has no cookie processor; no browser cookies or sessions are shared.
Only fixed readiness metadata is retained. This is preparation of the disposable
server; every native scenario still must display and submit its own fresh form.
At `778a454`, Safari already showed its server-response error before the cold
authorization route's HTTP 200 arrived; increasing the UI wait cannot recover
that failed browser request. The host GET moves that initialization before it.
The marketplace observer starts after service/form readiness and before UI execution, so
its polling does not compete with the build. All workflow corroboration remains.
Boot readiness remains bounded to 180 seconds. The independent session workflow
builds each phase with a bounded 600-second `build-for-testing` action, then
retains its 300-second per-phase `test-without-building` deadline and all three
separate processes. Each Dart phase is compiled again; app data and Keychain
survive the owned simulator reboots. Existing
workflow/job deadlines remain in force. Expiry fails the gate and does not
silently retry a scenario.

The browser helper declines only the observed Save Password? / Not Now prompt
only while the expected profile is absent and the button is visibly within the
main window bounds. A named prompt is tapped at its observed window coordinate;
XCTest must not re-resolve/auto-scroll its disappearing AX button during the
browser animation. The application frame can include the transitioning sheet's
off-screen extents, so it is not a viewport guard. The helper rechecks the profile
before the coordinate tap. It stops inspecting optional prompts once the profile exists,
avoiding stale AX taps after callback completion. It still requires the real local
Keycloak form, realm identity, credentials, profile/capabilities, refresh, logout
and account switching. Account switching waits for either an enabled sign-in action or a restored
account to sign out; a transient disabled sign-in button cannot skip logout.
No cookie clearing, token injection or silent-login bypass is added. Only the simulator created by the invocation is shut down/deleted.

Logout allows 90 seconds for the real browser response and enabled sign-in
action, retaining consent, callback and subsequent fresh-login assertions. At
`7667a62`, the browser remains on a loading page at the 30-second deadline and
Keycloak logs cookie handling about 53 seconds after the tap. This is consistent
with slow OIDC handling; it does not prove a completed logout callback. The next
run must establish that outcome. This changes the bounded wait, not
the required logout outcome or the overall test/job deadlines.
Authenticated-profile recovery also allows 90 seconds: at `feaba91`, actual
token and profile-bootstrap 200 responses arrive after the former 30-second
account deadline. The expected profile/capabilities must still appear, and no
credentials, callbacks or API calls are injected to satisfy that assertion.
The disposable [Keycloak access log](https://www.keycloak.org/server/logging)
uses method, [relative path and response status](https://quarkus.io/guides/http-reference/#access-log).
The former `%U` formatter includes query data in observed output, so `%R`
replaces it and the artifact scrubber independently removes any access-log query.
It also removes XCTest's truncated secure-field keystrokes, beyond full-password
and JWT masking. Three regression cases run in ordinary CI. Logs exclude
protocol queries, headers, cookies and bodies, allowing completed OIDC
responses to be correlated with UI timing without recording protocol tokens.

Before browser scenarios, a simulator-side ephemeral `URLSession` reads the
canonical IPv4 OIDC discovery and API readiness URLs. The fixture uses
`127.0.0.1` consistently for Keycloak's issuer/hostname, API OIDC validation,
Flutter build defines and host probes, matching the services' IPv4-only binding.
It requires the expected issuer and readiness UP. It also records localhost and IPv6 loopback probe
status/error codes for both services; absent IPv6 listeners are diagnostic only.
Canonical discovery/readiness GETs poll within one shared 90-second readiness
window. Each request has at most ten seconds (capped to the remaining window),
with five seconds of XCTest completion allowance. Both expected issuer and UP
remain mandatory. The additional hostname/family probes are diagnostic only.
Bodies, headers, credentials and tokens are not logged, and
the probe does not share or clear browser cookies. Host readiness alone cannot
establish simulator reachability. A passing probe still requires all original
fresh-form, identity, UI, screenshot and database checks; it cannot establish
that Safari's separate transport will succeed.

At `8edd30d`, both simulator probes read localhost/IPv4 successfully while IPv6
connections are refused; browser auth still shows Safari's server-response error.
Marketplace instead signs in and reaches request creation, where an immediate
field assertion sees a prefix while the later UI tree's counter reports the full
expected length (137 of 160 characters remaining). Field input now waits up to ten seconds for
the exact full accessibility value, retaining the equality assertion and never
retyping/truncating the text. Explicit IPv4 fixture addresses remove hostname
ambiguity; they do not prove IPv6 alone caused the Safari error. Application
defaults, browser consent and all identity/business assertions remain unchanged.

Activity filter selection waits up to 90 seconds for the page's Refresh activity
button to become enabled, before its single chip tap. At `58a6424`, an initial
provider/activity GET completes just after the Open opportunities tap; the page
remains on Bookings and no opportunities GET occurs. The chip's sampled AX
enabled state did not establish settled page readiness. Waiting on the page's
actual loading control avoids a lost filter selection without retrying any
navigation or business write. The requested row must still load and be tapped.

## Verification

On any supported Python host, from the repository root:

```sh
python3 -m unittest discover -s infrastructure/scripts/tests
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
