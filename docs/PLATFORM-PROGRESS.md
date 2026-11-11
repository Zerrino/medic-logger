# Platform delivery

## Completed 1.4.2 (8) legal screens

- Both apps: Settings → Legal with offline Privacy policy and Medical notice, localized in four languages, publisher Zerrino and public contact afds999@gmail.com. Android includes full open-source notices. Shared content generates both native sources and static policy pages.
- Android APK/AAB compilation, release lint, signature and bundletool validation pass. iOS Release Simulator build, ad-hoc signature and unsigned device archive pass; the privacy screen was inspected in Simulator. Both simulator apps updated with data retained.
- At the user's request, Android 1.4.2 was installed as an update on the connected A059 and the APK copied to Downloads; installed version/code and matching file checksum confirmed. No app data clear/uninstall was performed.
- Public policy pages are ready locally; the user postponed publication. A public URL and final store console declarations remain for submission. See docs/STORE-SUBMISSION.md. No store submission was performed.

## Completed iOS 1.4.1 and Android emulator setup

- Fixed stale Waiting state after No effect: Today now keeps one stable identity per dose and cards read its current stored state. Existing records need no migration.
- Today, Journal and Calendar share one compact, connected Taken/Onset/End or Closed timeline per dose, without duplicate Taken. Intake-only rows have one step. Cross-day context and distinct intakes are preserved; large accessibility text switches to a vertical layout.
- 35 native tests pass, plus focused UI checks for closure/relaunch/calendar, effects/rating, intake-only stock/refill and largest accessibility text. Release iOS **1.4.1 (7)** and its status extension compile, install and run in the dedicated simulator; the unsigned device archive also compiles. Main encrypted data were kept and only owned test fixtures/runner removed.
- Added dedicated **Medic Logger Android** AOSP Android 16 ARM64 emulator on the Mac M3, serial `emulator-5556`, accelerated by Hypervisor.Framework. Android release **1.4.0 (6)** is installed/running. 35 checks passed on an isolated QA build; release startup/screen and crash log checked separately. QA packages removed; no physical phone used. Double-click `scripts/Open-Android-Simulator.command`; see `docs/ANDROID-SIMULATOR.md`.
- Updated Simulator ZIP and source archive; Android APK/AAB remain the existing signed release.

## Completed Android 1.4.0

- Native Java app, package fr.mediclogger.app, versionCode 6, compile/target API 36, min API 26.
- Encrypted SQLCipher storage schema 4 and encrypted preferences; export schema 3 (plaintext exports).
- Inventory/refills, concurrent dose logging, optional effect monitoring (off for new medicines), optional 0–10 effectiveness score, calendar, four languages, custom colors, Ko-fi.
- Reminder schedules: daily/weekly/monthly/yearly and new 21/7-style configurable ON/OFF cycles with a date anchor and multiple hours. Cycle math uses calendar days across DST. Global reminders, sound profiles and status notification controls remain independent.
- 57 core checks plus parsers; 34 new device migration/cycle checks use owned temporary databases. Prior review has other reports in artifacts/review.
- APK uploaded to phone Downloads and installed; only fr.mediclogger.app remains. User was explicitly notified before AAB creation.
- Signed AAB built, bundletool validated, all native ABIs aligned to 16 KB; bundle-generated APK installed/launched successfully, then original APK restored. Release mapping included. Artifacts: Medic-Logger.apk, Medic-Logger.aab, mapping and checksums.

## Completed iOS 1.4.0 on the Mac M3

- Native SwiftUI for iPhone/iPad iOS 17+, matching medicines/aliases/colors, intake-only default, independent monitored doses, 0–10 effectiveness, notes/editing, batch-bound stock/refills, calendar/history, recurrence including ON/OFF cycles, four languages, Ko-fi and plaintext JSON/CSV/XLSX/ZIP exports.
- AES-256-GCM local data/settings, device-only Keychain key, backup exclusion and protected files. No app account, backend, analytics or automatic sync. No encryption/signing keys are requested from users.
- Independent local reminder and Live Activity settings, native sounds/silent/system controls, bounded rolling queue with a visible horizon and best-effort background refresh. The iOS limitations are documented honestly in Settings and ios/README.md.
- Dedicated Medic Logger iOS simulator, iPhone17Pro iOS26.5, UUID DC991293-A36A-4EF7-9B83-966001C851A9. Other simulators/user data were not used.
- 30 native tests and 8 UI journeys passed across review/targeted reruns. Actual Files JSON saved/parsed, independent ZIP/CSV/XLSX parsers passed, real ActivityKit lifecycle and native notification requests tested. See docs/VERIFICATION.md for scope, reruns and limits.
- SwiftUI Expert, Apple Design and SwiftUI UI Patterns skills applied; contrast, large-text layout, keyboard focus, sheet sizing and Live Activity surface improved. docs/IOS-DESIGN-REVIEW.md records references and changes.
- Release Simulator .app zipped to artifacts/Medic-Logger-iOS-Simulator.zip. Unsigned physical-device archive at ios/build/MedicLogger.xcarchive. Version 1.4.0 (6) visible in Settings, verified on the release app. Owned simulator fixture files and runner removed; main encrypted data kept; release app left running.
- User clarified they want no developer-key/certificate configuration for end users. Normal TestFlight/App Store installation meets that; publisher signing remains Apple's requirement. No store submission was requested or performed. ios/INSTALL-IPHONE.md explains the distinction.

No QA copy or instrumentation helper remains on the Android phone. Exactly fr.mediclogger.app remains; the Downloads APK checksum matches the delivered artifact. Never clear or uninstall the user app during future reviews.
