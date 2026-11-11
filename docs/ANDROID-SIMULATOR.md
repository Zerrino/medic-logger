# Android test environment on the Mac M3

Dedicated virtual device **Medic Logger Android**, AVD `Medic_Logger_Android`, Android 16/API 36, AOSP ARM64. It uses the Mac's native ARM virtualization and does not require Google Play or a Google account. The emulator and image are installed from Google's official SDK repository.

Project-local SDK: `.tools/android-sdk`. Virtual device files: `.tools/android-avd`. No physical phone is needed or targeted by these instructions.

## Start

Double-click `scripts/Open-Android-Simulator.command` in Finder, or run:

```sh
zsh scripts/run-android-simulator.sh
```

The launcher keeps the existing virtual device and its data. Starting it again while the same emulator is running is safe.

The emulator has the reserved development serial **`emulator-5556`**. Always specify that serial when installing or testing:

```sh
adb -s emulator-5556 install -r artifacts/Medic-Logger.apk
adb -s emulator-5556 shell am start -n fr.mediclogger.app/.MainActivity
```

The `.apk` is the Android release; the corrected iOS app is a separate native project. Tests must never use an unqualified `adb` command that might target a connected physical phone.

## Verified here

The current Android 1.4.2 (8) release is installed and launches successfully. Its crash log was empty. During the original 1.4.0 setup, an isolated QA build passed 35 native migration/effectiveness checks in temporary databases; both QA packages were removed afterwards. Only the release app remains. See `artifacts/review/android-simulator-results.txt` and `docs/VERIFICATION.md` for the separate review scopes.

## Recreate this virtual device

The project-local Android SDK command-line tools are installed at `.tools/android-sdk/cmdline-tools/latest/bin`. The emulator needs `emulator` and `system-images;android-36;default;arm64-v8a` in the project SDK. Keep the installed SDK licenses; do not silently accept additional licenses for a different image.

```sh
mkdir -p .tools/android-avd
ANDROID_AVD_HOME="$PWD/.tools/android-avd" \
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
"$PWD/.tools/android-sdk/cmdline-tools/latest/bin/avdmanager" create avd --name Medic_Logger_Android \
  --package 'system-images;android-36;default;arm64-v8a' \
  --device pixel_8 --path "$PWD/.tools/android-avd/Medic_Logger_Android.avd"
```

Do not recreate or wipe an existing AVD to run ordinary tests. Its data persist between launches. The launcher reserves 2 GB of RAM and leaves Android audio disabled for the development session.

Official references: [Android Emulator command line](https://developer.android.com/studio/run/emulator-commandline) and [AVD Manager](https://developer.android.com/tools/avdmanager).
