# Bliss mobile

Flutter 3.47.5 / Dart 3.13.4. The first slice is a real HTTP catalog client with
category filters, bounded pagination, provider/offering details and retry states.
The account screen supports Keycloak sign-in, profile editing and sign-out.
Booking actions remain tracked in issue #7.

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

HTTP client and typed mappings live under `core/api` and `features/discovery`.
Prices remain decimal strings. UI code ignores stale responses and guards disposed
widgets. Tests use injected HTTP clients; they do not claim a live API demo.
