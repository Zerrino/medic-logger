#!/bin/zsh
set -euo pipefail
project_root="${0:A:h:h}"
medic_sdk="$project_root/.tools/android-sdk"
medic_avds="$project_root/.tools/android-avd"
medic_name='Medic_Logger_Android'
medic_adb="${commands[adb]:-}"
if [[ -x "$medic_sdk/platform-tools/adb" ]]; then
  medic_adb="$medic_sdk/platform-tools/adb"
elif [[ -z "$medic_adb" && -x /opt/homebrew/bin/adb ]]; then
  medic_adb=/opt/homebrew/bin/adb
fi
if [[ -n "$medic_adb" ]] && "$medic_adb" -s emulator-5556 get-state >/dev/null 2>&1; then
  medic_running_avd="$("$medic_adb" -s emulator-5556 emu avd name | tr -d '\r')"
  if [[ "${medic_running_avd%%$'\n'*}" == "$medic_name" ]]; then
    print 'Medic Logger Android is already running.'
    exit 0
  fi
  print 'Emulator port 5556 is already used by another virtual device.'
  exit 1
fi
if [[ ! -x "$medic_sdk/emulator/emulator" || ! -f "$medic_sdk/system-images/android-36/default/arm64-v8a/system.img" ]]; then
  print 'Install the Android emulator and Android 36 AOSP arm64 system image first.'
  exit 1
fi
mkdir -p "$medic_avds"
if [[ ! -f "$medic_avds/$medic_name.ini" ]]; then
  print 'The Medic Logger AVD must be created first; see docs/ANDROID-SIMULATOR.md.'
  exit 1
fi
export ANDROID_AVD_HOME="$medic_avds"
export ANDROID_HOME="$medic_sdk"
export ANDROID_SDK_ROOT="$medic_sdk"
exec "$medic_sdk/emulator/emulator" -avd "$medic_name" -port 5556 -memory 2048 -no-boot-anim -no-audio "$@"
