# Android build and emulator smoke check

Verified September 27, 2026 on Windows with Flutter 3.47.5, Java 21 and the
installed Android API 36 Google Play x86_64 image. This check establishes a debug
APK build, installation, activity startup and registered callback handler. It
does not establish a successful native OIDC sign-in or session recovery.

## Observed results

- `flutter build apk --debug` passed. Gradle installed NDK 28.2.13676358, Android
  Platform 35 (required by a plugin) and CMake 3.22.1 using existing SDK license
  acceptance. No license hashes were fabricated or new acceptance entered.
- The existing Medium Phone emulator booted with WHPX acceleration but its data
  partition was 96% full; installation failed with insufficient internal space.
  It was run read-only and stopped without deleting its data.
- A separate `Bliss_Test` AVD in ignored `.work/android-avd` used the same system
  image with a fresh 8 GB data partition. It booted with 6.8 GB free.
- `adb install -r` succeeded. After first boot completed, a cold launch returned
  `Status: ok` for `com.bliss.bliss_mobile/.MainActivity`. Flutter and its Dart VM
  started; no fatal AndroidRuntime or unhandled Flutter exception was observed in
  the app-scoped startup log. The first launch during boot timed out; the completed
  cold-launch check took approximately 11 seconds. This is not a performance test.
- Android's activity query resolved `com.bliss.mobile:/oauthredirect` to
  `net.openid.appauth.RedirectUriReceiverActivity` in the Bliss package.
- ADB reverse mappings connected device localhost ports 8088 and 8180 to the host.

The ignored artifact is `apps/mobile/build/app/outputs/flutter-apk/app-debug.apk`.
Its SHA-256 is
`63d1320adaf3a3287212072261ddd434b7dfc1987f83999cbf33628d9ca6c6bf`.
It is a debug build, not a signed distribution release. Generated Kotlin session
files are ignored alongside Gradle/build outputs.

## Repeat and complete native verification

Start the backend using the root README. Start an Android emulator with sufficient
free internal space, or connect a USB-debuggable device. From `apps/mobile`, run:

```sh
flutter build apk --debug
adb devices -l
# Replace emulator-5554 with the selected device ID.
adb -s emulator-5554 reverse tcp:8088 tcp:8088
adb -s emulator-5554 reverse tcp:8180 tcp:8180
adb -s emulator-5554 install -r build/app/outputs/flutter-apk/app-debug.apk
adb -s emulator-5554 shell am start -W -n com.bliss.bliss_mobile/.MainActivity
```

Use a visible device for the remaining acceptance checks. Keep generated demo
credentials private; use the existing customer/provider identities.

1. Browse the real catalog; verify loading and retry behavior if the API stops.
2. Sign in through the system browser and return through the AppAuth callback.
   Confirm the correct account and capabilities appear.
3. Force-stop and reopen Bliss without clearing app data. Confirm the stored
   refresh token restores the session and the API accepts refreshed credentials.
4. Sign out, force-stop and reopen. Confirm private activity is unavailable and
   another account can sign in without inheriting the previous account's data.
5. Complete the customer/provider booking flow, including return from the browser,
   status refresh, chat and a review. Check background/resume and back navigation.
6. Repeat native authentication checks on iOS from a macOS host.

Record actual outcomes before closing #7. Callback registration alone does not
prove PKCE exchange, secure-storage recovery or identity-provider logout. Android
command-line tools are still absent; Flutter doctor's overall license status can
remain unknown even though Gradle accepted the packages against existing licenses.
