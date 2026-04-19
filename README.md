# Darling Assistant App

Darling Assistant App is a Flutter + Android Kotlin MVP that keeps an offline voice listener alive in a foreground service and automatically silences or vibrates incoming calls when a spoken command is recognized.

## Project structure

```text
silentoapp/
├── android/
│   ├── app/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── assets/models/README.md
│   │       ├── kotlin/com/example/silentoapp/
│   │       │   ├── BootCompletedReceiver.kt
│   │       │   ├── CallStateMonitor.kt
│   │       │   ├── CommandRegistry.kt
│   │       │   ├── MainActivity.kt
│   │       │   ├── ServiceStateStore.kt
│   │       │   ├── SilentAssistantLogger.kt
│   │       │   ├── SilentAssistantService.kt
│   │       │   └── VoskModelManager.kt
│   │       └── res/
│   │           └── drawable/ic_stat_silent_assistant.xml
├── lib/
│   └── main.dart
├── pubspec.yaml
└── README.md
```

## What is implemented

- Flutter UI with:
  - Start Listening
  - Stop Listening
  - Live status display
  - Recent event log
- Flutter `MethodChannel` and `EventChannel` bridge
- Android foreground service with persistent notification
- Offline Vosk speech recognition with command grammar
- Command engine for:
  - `silent`
  - `mute`
  - `ring off`
  - `vibrate`
- Incoming call detection using `TelephonyManager`
- Legal ringtone control through `AudioManager`
- Runtime permission flow and settings redirects
- Battery optimization exclusion prompt
- Sticky service restart support and boot/package-replace restart

## Vosk model setup

1. Download a small English Vosk model from:
   - https://alphacephei.com/vosk/models
2. Use the folder named `vosk-model-small-en-us-0.15` or rename your model folder to that exact name.
3. Extract it into:

```text
android/app/src/main/assets/models/vosk-model-small-en-us-0.15/
```

4. Confirm these files exist after extraction:
   - `android/app/src/main/assets/models/vosk-model-small-en-us-0.15/am/final.mdl`
   - `android/app/src/main/assets/models/vosk-model-small-en-us-0.15/conf/model.conf`
   - `android/app/src/main/assets/models/vosk-model-small-en-us-0.15/graph/Gr.fst`

If you use a different model path, update `MODEL_ASSET_PATH` in
`android/app/src/main/kotlin/com/example/silentoapp/SilentAssistantService.kt`.

## Flutter and Android setup

1. Install Flutter and confirm `flutter doctor` is clean enough for Android builds.
2. Open the project root.
3. Run:

```bash
flutter pub get
```

4. Connect an Android device with telephony support.
   - Emulator testing is limited because real incoming calls and audio routing are inconsistent there.
5. Grant these permissions when prompted:
   - Microphone
   - Phone
   - Notifications
6. Approve Android settings prompts for:
   - Notification policy access
   - Ignore battery optimizations

## Run

```bash
flutter run
```

## Test flow

1. Launch the app.
2. Tap `Start Listening`.
3. Speak one of the supported commands clearly:
   - `silent`
   - `mute`
   - `ring off`
   - `vibrate`
4. Confirm the UI log shows the recognized command and an armed pending mode.
5. Trigger an incoming phone call to the device.
6. Confirm the service detects `RINGING`.
7. Confirm the ringtone changes to silent or vibrate automatically.
8. Confirm the UI updates to `Silent Triggered` or `Vibrate Triggered`.

## Notes

- This MVP uses only legal Android APIs and does not simulate hardware key presses.
- Reliable background microphone capture on modern Android depends on the app being started while in the foreground and kept alive as a microphone foreground service.
- Some OEM Android builds add extra battery restrictions beyond standard Android settings. If background capture is aggressive, manually whitelist the app in the vendor battery manager too.
