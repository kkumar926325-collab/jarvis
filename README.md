# JARVIS

A personal Android assistant with permission-based phone control. Kotlin + Jetpack Compose, minSdk 26.
Everything stays on the phone: the app declares **no INTERNET permission**.

## What is implemented

| Module | How it works | Permission |
|---|---|---|
| Contacts | ContactsContract lookup by name | READ_CONTACTS, asked on first use |
| SMS | Read inbox on request; send only after an on-screen Confirm. Default "direct" mode uses SmsManager; "compose" mode opens your messaging app instead | READ_SMS / SEND_SMS, each asked on first use (compose mode needs none) |
| Calling | `ACTION_DIAL` after Confirm; you press the call button | none |
| Notifications | Optional `NotificationListenerService`; kept in memory only | You enable it in Android settings |
| Files | Storage Access Framework folder picker; read-only, one folder | none |
| Apps | List/open launchable apps; "manage" opens Android's app-info page | none |
| Battery & device | BatteryManager / StatFs / ActivityManager | none |
| Settings shortcuts | Opens settings screens (cannot flip switches) | none |
| Voice | Push-to-talk SpeechRecognizer + TTS replies for voice commands | RECORD_AUDIO, asked on first mic tap |
| Personal memory | Aliases ("mom" = contact), taught phrases, preferences, in private app storage | none |

Not implemented / by design: no default SMS or dialer role, no hands-free wake word, no silent
sending, no toggling Wi-Fi/Bluetooth, no deleting/writing files, no cloud AI. Speech recognition
may use Google's online service depending on the device (the permission text says so).

Note: READ_SMS / SEND_SMS are restricted on Google Play; this build is meant for sideloading.

## Architecture

- `core/` pure Kotlin: `Command`, `CommandParser`, `CommandExecutor`, `Outcome`/`PendingAction`
  (nothing sensitive runs until the user confirms), `AiBrain`.
- `modules/<name>/` one module each, depending only on a small interface (`SmsGateway`,
  `ContactsSource`, ...). `android/` holds the real implementations; `ui/` is Compose.
- Each module is unit-tested on the JVM with fakes (`app/src/test`).
- **Future AI**: implement `AiBrain` (text -> `Command`) and pass it to `JarvisContainer`. Modules
  still enforce permissions and confirmations, so a smarter brain cannot bypass them.

## Build

Push to GitHub: the workflow in `.github/workflows/build.yml` runs unit tests and builds
`app-debug.apk` (download from the run's Artifacts). Locally: `gradle testDebugUnitTest assembleDebug`.
