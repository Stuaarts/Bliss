# Bliss mobile

Flutter 3.47.5 / Dart 3.13.4. The first slice is a real HTTP catalog client with
category filters, bounded pagination, provider/offering details and retry states.
The account screen supports Keycloak sign-in, profile editing and sign-out.
Your activity connects customer requests, provider opportunities/quotes and both
booking paths to the real API. Booking details show status history, local schedule
and deadline times, simulated settlement and role/state-appropriate actions.
See [demo steps and evidence](../../docs/demo/MARKETPLACE.md).

From this directory, with the API running in the `local` profile:

```sh
flutter pub get
flutter run -d chrome --web-port 3000 --dart-define=API_BASE_URL=http://localhost:8088
flutter analyze
flutter test
```

Android local demo: run `adb reverse tcp:8088 tcp:8088` and
`adb reverse tcp:8180 tcp:8180`, then use the default localhost URLs. This preserves
the issuer URL from Keycloak discovery. Native login uses the system browser via
AppAuth and the registered `com.bliss.mobile:/oauthredirect` callback.
Local HTTP is allowed only in the Android debug manifest. Release builds require
HTTPS. Physical devices require an explicitly reachable development API address;
the default backend binds loopback. iOS device validation requires macOS and is
not yet performed. Web is the first cross-platform demo target.

Configure a real HTTPS identity provider using `--dart-define=OIDC_ISSUER=...`
and API using `--dart-define=API_BASE_URL=...`; register the matching redirect URLs.
Web tokens stay in memory, so a reload requires sign-in again (the identity server
may still have an SSO session). Only the PKCE transaction is temporarily stored in
sessionStorage; it expires after ten minutes and is removed on callback. Native
refresh tokens use platform secure storage; access tokens stay in memory. Native
Android/iOS code is analyzed but device login has not yet been verified.

HTTP clients and typed mappings live under `core`, `features/discovery` and
`features/marketplace` and `features/conversation`.
Prices remain decimal strings. UI code ignores stale responses and guards disposed
widgets. Tests use injected HTTP clients; they do not claim a live API demo.

Direct bookings, lifecycle actions and chat sends keep a UUID retry key and the original body
in memory until a definite response. An uncertain response freezes form inputs;
retry sends the original intent. These keys clear on sign-out, session expiry or
app reload: check Your activity before making another booking after restarting.
Request/quote creation has no client retry key, so an uncertain response asks the
user to check their list first. Quote acceptance is idempotent server-side.
Private routes are bound to the signed-in session; logout disposes their forms,
and late responses from an older session are rejected.

Booking → Messages opens a persisted conversation. Your activity → Messages lists
existing conversations. Visible chats poll every three seconds, refresh on demand,
and page through older messages using sequence cursors. Pending sends retain the
original text/key across route navigation within the same session. After a full
app reload, read the conversation before resending an unconfirmed message.
Authorization loss clears the visible history and composer. Chat is plain text;
attachments, push notifications and read receipts are not implemented.

Native validation remains in #7. On the September 22 workstation check, Flutter
reported missing Android command-line tools, unknown SDK license status, and no
connected Android/iOS device. iOS also requires a macOS host. The browser demo
does not establish native AppAuth callback or secure-storage behavior.
