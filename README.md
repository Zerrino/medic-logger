# Medic Logger

A native medication journal for **Android 8+ and iOS 17+**, Android **1.4.2** / iOS **1.4.2**. English is the default, with French, Portuguese and Chinese bundled offline. Exported field names remain in English. No account, analytics, backend or automatic synchronization. Android uses SQLCipher; iOS uses AES-256-GCM and a device-only Keychain key.

## Install

The ready-to-install, locally signed APK is `artifacts/Medic-Logger.apk`.

```sh
adb install -r artifacts/Medic-Logger.apk
adb shell am start -n fr.mediclogger.app/.MainActivity
```

You can also copy the APK to your phone, open it in Files, and allow that app to install it. Subsequent APKs must use the same signing key to preserve the installed application and its data.

For iOS, open [`ios/MedicLogger.xcodeproj`](ios/MedicLogger.xcodeproj) in Xcode and select the **Medic Logger iOS** simulator created on the Mac. The arm64 Simulator build is `artifacts/Medic-Logger-iOS-Simulator.zip`. See [iOS build, features and platform limits](ios/README.md). A Simulator `.app` is not an installable iPhone IPA; physical iPhone distribution needs your Apple signing team.

## Use

1. Open **Medicines**, then **Add a medicine**. Enter its name, optional short name, dose label, color and optional pack quantity. **Monitor effects** is off by default. Star it for **Quick log**.
2. On **Today**, tap its shortcut to log a dose immediately. Repeating the same medicine while a dose is active requires confirmation.
3. With **Monitor effects** enabled, each new dose has its own **Effects started** and **Effects ended** buttons. Several medicines and doses can be tracked simultaneously. **No effect** records a separate no-effect closure. With monitoring disabled, a dose is simply recorded as **Taken**, without effect controls. Editing this option affects new doses; old records retain their original mode. Finishing a monitored dose offers an optional **0–10 effectiveness rating**. Skip it or edit/clear it later in the dose details. Ratings are shown in history, the calendar and exports. Intake-only medicines have no rating controls.
4. Open **Journal** to search names and notes, filter active/completed doses, or open a dose to edit times and notes. Invalid time sequences and future timestamps are rejected. Deleting a dose requires confirmation.
5. **Home shortcut** asks your launcher to pin a medicine. Tapping it opens a confirmation to log the current time. Starred medicines are also offered as launcher app shortcuts where supported.
6. **Export** lets you choose inclusive start/end dates, one or several medicines (or all), JSON/CSV/Excel/all formats, and optional ZIP wrapping. Save to a local folder with Android’s file picker. Choosing a format or medicine selection preserves the page position.

Archiving a medicine keeps its existing doses and active tracking. Restore it using **Show archived**. Renaming a medicine does not rewrite historical names or dose labels.

## Stock and reminders

- An optional pack quantity enables stock tracking. Logging a dose consumes one unit. An empty pack must be refilled before another intake can be recorded.
- **Refill** starts a new pack with the entered quantity; it does not add to the previous pack. Deleting a dose refunds its unit only if it belongs to the current pack. Older doses keep their original pack identity even when their timestamp is edited, so deleting a dose from an earlier pack never changes the new pack.
- Open **Meds → Reminders** for a medicine. Add several times, choose every day, weekdays, weekends, selected weekdays, selected days each month, or annual dates. Multiple rules may be combined. Monthly dates absent from a month are skipped; a February 29 annual date runs in leap years.
- **ON/OFF cycle** repeats X consecutive calendar days of reminders followed by Y days without reminders. Set **Days ON**, **Days OFF**, **First ON day**, and any daily times. For example, a user-configured 21/7 cycle restarts every 28 calendar days, independent of month length. Existing daily/weekly/monthly/yearly rules remain available. The reminder card shows the current ON/OFF day and next alert. OFF days suppress delayed cycle alerts.
- Android notification permission is required. On Android 12+, **Allow precise reminders** opens the system permission screen. Without that optional access, Android may delay reminders. Rules are rescheduled after a reboot, app update, clock change or timezone change. Force-stopping the app suspends Android alarms until it is opened again.
- In **Settings**, **Remind me to take medication** enables or pauses intake reminders independently of **Show medication status**. Pausing preserves each medicine’s schedule. Choose System default, Sound, Vibration, Sound and vibration, or Silent. Sound modes offer a local ringtone picker, and Android’s channel settings remain available.
- Reminders use a separate sound/vibration channel from the silent ongoing status. Tap a reminder to view its schedule, or **Log dose** to confirm an intake. Recording an intake removes that medicine’s visible reminders. Simultaneous reminders are grouped.

## Calendar, colors and settings

- **Calendar** marks days containing dose or effect events. Select a day to see dose/event counts, records grouped by medicine, the number of doses, time since the last dose, and the first recorded date. Monthly totals count doses taken within the displayed month.
- The **+** at the right end of the color palette opens a picker with RGB sliders, a live preview and a six-digit hex field. Custom colors are saved locally and are reusable. Changing a medicine’s color also updates its displayed cards and notification without rewriting historical names or timestamps.
- The header gear opens **Settings**. Language changes apply immediately and persist; all four languages are bundled for offline use.
- The bottom of **Settings** shows the current version (**1.4.2** on both platforms).
- Enable **Show medication status** to see an ongoing notification for doses that monitor effects. Android 13+ asks for notification permission. **Active** records onset; **Over** closes the displayed dose and opens the optional effectiveness rating. When several doses are active, the previous/next arrows are visible even in the collapsed notification. Each arrow uses the color of its destination medicine; expanded notifications also expose the dose actions. With a single dose, Active/Over are directly visible. Use Show notification again in Settings after dismissing it. Native notification swipes are system gestures, so horizontal swipe navigation is replaced by these controls. Actions work while the app is in the background; no media playback or foreground service is simulated.
- The expanded status separates medicine, phase and dose/timing details; its body opens the selected dose. Medication details are hidden by default on the lock screen. Settings links to the system controls for reminder sound and vibration.
- **Offer me a coffee** opens [ko-fi.com/zerrino](https://ko-fi.com/zerrino) in the phone’s browser. Ko-fi and the connected payment provider handle payment. The app sends no medication data and contains no payment credentials; only this optional external page needs Internet access.
- Completed doses remain in the app’s history and calendar. When no active doses remain, the status notification disappears. Android’s own notification settings and force-stop behavior still apply.

## Data and privacy

- Records, inventory, refill identities and reminder schedules live in a private **SQLCipher encrypted database**. Its random 256-bit key is wrapped with an AES-256-GCM key in Android Keystore. No password or key is embedded in the APK.
- Settings, custom colors, pending export state, and the app’s saved screen state use authenticated AES-256-GCM encryption. Private export preparation files are encrypted too. An existing plaintext installation migrates before any screen or receiver reads the records, keeping the original database in place until the encrypted replacement is complete. Encryption errors never trigger a destructive reset or plaintext fallback.
- The app has no network or shared-storage permissions. It requests `POST_NOTIFICATIONS`, normal `RECEIVE_BOOT_COMPLETED`, and optional `SCHEDULE_EXACT_ALARM` access, plus the normal `VIBRATE` permission for reminder alert modes. Keys remain usable while the phone is locked so reminders can run after the first unlock following a reboot.
- Android automatic backup and device-transfer rules exclude the app’s data. No automatic upload or synchronization occurs.
- Files are exported only on request through Android’s document picker, with the local-only option. **Exported JSON, CSV, XLSX and ZIP files are not encrypted.** Notifications and launcher shortcuts intentionally display selected medication information through Android; the encrypted storage does not change those requested system features.
- Uninstalling the app, or clearing its storage, deletes its private records. Exported files remain wherever you saved them. Exports are for reading/analysis; this version does not import or restore them.
- Medication names, dose labels, notes, and observed effects are user-entered. The app supplies no treatment or dose recommendations.

## Export schema

Date selection uses the phone’s current timezone and each **event’s own timestamp**, from local midnight on the start date up to (but not including) local midnight after the end date. This includes an effect that occurs in the selected period even when its dose was taken earlier, and correctly handles daylight-saving changes.

- **JSON**: schema version, generation time, period/timezone, selected `events`, and complete context in `sessions`. Session timestamps may be outside the event selection period; this is intentional. Timestamps include UTC ISO 8601, local offset time, and Unix milliseconds for events. Missing observations are `null`.
- **CSV**: comma-delimited, quoted RFC 4180 rows, CRLF, and UTF-8 BOM for Excel. One row per selected event. Potential spreadsheet formulas in user text are prefixed with an apostrophe.
- **Excel**: an actual `.xlsx` workbook, with **Events**, **Doses**, and **About this export** sheets, frozen header rows, filters, and numeric duration fields in seconds. User text is written as text, never a formula. A workbook is a ZIP container internally, as required by the XLSX format.
- **ZIP**: wraps a single selected format, or all three files when **All formats** is selected.

Event types: `dose_taken`, `effects_started`, `effects_ended`, `closed_without_effect`.
Schema version 3 records `monitor_effects`, `stock_batch_id`, `stock_units`, and nullable numeric `effectiveness_rating` (0–10). Legacy records stay unrated until the user chooses a rating. These exports contain the selected dose/event history, not a complete restorable inventory or reminder backup.

Dose statuses: `taken`, `waiting_for_effect`, `effects_active`, `completed`, `closed_without_effect`.

## Build

Requires JDK 21 (JDK 17 also meets the plugin requirement), Android SDK Platform 36, and Build Tools 35.0.0. Gradle 8.11.1 and Android Gradle Plugin 8.9.2 are pinned. The first build needs Internet access to download build tools; the installed app does not.

Configure `ANDROID_HOME`, or put `sdk.dir=/absolute/path/to/android-sdk` in `local.properties`. This workspace uses `.tools/android-sdk`.

```sh
# Once per signing identity; passwords are generated and kept locally.
python3 scripts/create-signing-key.py

./gradlew :app:assembleRelease
cp app/build/outputs/apk/release/app-release.apk artifacts/Medic-Logger.apk
```

The private key and its properties are in the ignored `.signing/` folder. Keep a safe copy of both to sign updates. If no local key exists, Gradle falls back to the standard Android development key.

## Verification

```sh
scripts/test-core.sh
./gradlew :app:lintRelease :app:assembleRelease :app:assembleReleaseAndroidTest \
  -PdeviceTestVariant=release \
  -PdeviceTestRunner=fr.mediclogger.app.RatingTestRunner
adb install -r app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb shell am instrument -w fr.mediclogger.app.test/fr.mediclogger.app.RatingTestRunner
adb uninstall fr.mediclogger.app.test
```

The signed rating/migration runner uses only its own temporary databases; it does not modify user records or preferences. The broader development suite uses the separate QA build and refuses to run against the user app. The QA application is not kept on the phone.

The November 5 review passed 117 device checks for the pre-rating features, 35 migration/rating checks on the release application, 40 JVM checks, and independent parsers for all export formats. Actual phone UI checks saved a 7/10 rating and exported it through Android's document picker. See `docs/VERIFICATION.md` for the scope and limits.

Translations live in `app/translations.json`; run `python3 scripts/generate-i18n.py` after editing them. The generator checks format placeholders and creates Android string/plural resources.

## Icon

The custom red/white icon was generated using the built-in imagegen tool. Its capsule and clock symbolize medication timing. Source: `assets/medic-logger-icon-red-source.png`; Android asset: `app/src/main/res/drawable-nodpi/ic_launcher_art.png`; small standalone icon: `artifacts/medic-logger-icon.png`. Both generation prompts are recorded in `assets/icon-prompt.txt`. The mobile design references and decisions are in `docs/UI-DESIGN.md`.


## Android App Bundle

`artifacts/Medic-Logger.aab` is the signed 1.4.2 release bundle (versionCode 8, target API 36). It includes all four supported ABIs and bundled interface languages. R8/resource shrinking are enabled, with the de-obfuscation map in `artifacts/Medic-Logger.mapping.txt`. Signing uses the existing private local key; no credentials are included in artifacts.

Bundle validation, 16 KB native-library page alignment and a bundle-generated universal APK were checked with the official bundletool. Both the generated APK and original APK install as updates with the same certificate. The bundle is a build artifact for Play Console; this work does not publish it to any store.

## Local simulators on the Mac

The dedicated iOS Simulator and project-local Android AVD allow testing without a physical phone. Start Android by opening `scripts/Open-Android-Simulator.command` or running `zsh scripts/run-android-simulator.sh`. See [Android simulator setup](docs/ANDROID-SIMULATOR.md). Android tests always target `emulator-5556` explicitly.

## Legal information and store preparation

Settings includes an offline Privacy policy and Medical notice in all four languages. Android also displays its complete open-source licenses. Publisher/contact: Zerrino / afds999@gmail.com. The shared legal source is `assets/legal/content.json`; run `python3 scripts/generate-legal.py`, `python3 scripts/generate-i18n.py` and `python3 scripts/generate-ios-resources.py` after editing it or its translations.

The four ready-to-host policy pages are in `docs/legal` and `artifacts/Medic-Logger-privacy-pages.zip`. Website publication was postponed by the publisher. A working public privacy-policy URL must still be entered in both store consoles before submission; no placeholder link appears in the apps. See [store information and official references](docs/STORE-SUBMISSION.md).
