# Bliss mobile

Flutter 3.47.5 / Dart 3.13.4. The first slice is a real HTTP catalog client with
category filters, bounded pagination, provider/offering details and retry states.
Login and booking actions remain tracked in issue #7.

From this directory, with the API running in the `local` profile:

```sh
flutter pub get
flutter run -d chrome --web-port 3000 --dart-define=API_BASE_URL=http://localhost:8088
flutter analyze
flutter test
```

Android emulator: pass `--dart-define=API_BASE_URL=http://10.0.2.2:8088`.
Local HTTP is allowed only in the Android debug manifest. Release builds require
HTTPS. Physical devices require an explicitly reachable development API address;
the default backend binds loopback. iOS device validation requires macOS and is
not yet performed. Web is the first cross-platform demo target.

HTTP client and typed mappings live under `core/api` and `features/discovery`.
Prices remain decimal strings. UI code ignores stale responses and guards disposed
widgets. Tests use injected HTTP clients; they do not claim a live API demo.
