# Medic Logger — verification on November 5, 2026

## 1.4.2 (8): offline legal information on both platforms

- Settings → Legal adds Privacy policy and Medical notice in English/French/Portuguese/Chinese, with the publisher's supplied Zerrino / afds999@gmail.com contact. Android also includes full license notices for SQLCipher, its default LibTomCrypt provider, SQLite/Android bindings, AndroidX, Kotlin and annotations. The exact notices are present in the optimized release APK resource.
- Reviewed current official Apple/Google privacy and health-app requirements; sources and remaining console information are in `docs/STORE-SUBMISSION.md`. The policy matches local encryption, no analytics/backend, user-controlled exports and external links. No custom EULA or consent wall was added.
- Android release APK/AAB and release lint pass; APK signature and official bundletool validation pass. iOS Release Simulator build, signature verification and unsigned device archive pass. The version is **1.4.2 (8)** on both platforms. Logs: `.tools/legal-android-build.log`, `.tools/legal-ios-build.log`.
- Both dedicated Mac simulators received the release update. The real iOS Settings → Privacy policy navigation and readable policy layout were inspected. This low-impact content update did not rerun the earlier full functional suites; those historical results below are not new test counts.
- At the publisher's subsequent request, the connected A059 received `adb install -r` successfully; installed version 1.4.2/code 8 was confirmed. `/sdcard/Download/Medic-Logger.apk` was copied/refreshed and its SHA-256 matches the delivered APK. No app storage was cleared or app uninstalled during this update.
- Four static policy pages and a hosting ZIP are prepared locally. **No website was published**, at the publisher's request. A public URL remains required in both store consoles before submission. No store submission or payment was performed.

## iOS 1.4.1 (7): closure and compact timelines

- Reproduced the reported bug in the old UI: closing without effects moved the dose under Taken today while the cached row still showed Waiting and effect actions. The record itself was already closed. A single Today list identity space and live dose lookup by ID now update that row immediately, without changing stored records.
- Today, Journal and Calendar use one compact card per dose. Taken → Onset → End/Closed are linked with their actual times; Taken is not repeated in the header. An intake-only dose has one Taken step. Cross-midnight doses retain their full context, with dates on steps outside the selected day; separate intakes remain separate records.
- **35 native tests pass** in `ios-141-final-tests.xcresult`, including five timeline/grouping tests. Three focused UI journeys passed in `ios-timeline-regression-2.xcresult`: no-effect closure/relaunch/calendar, observed effects/rating/calendar, and intake-only stock/refill. The final UI rerun passed no-effect closure and maximum accessibility text after the last layout changes. Logs and xcresult bundles remain in `.tools/`.
- Release Simulator build and unsigned physical-device archive both succeeded. Version **1.4.1 (7)** is installed in the dedicated iOS simulator. The Simulator ZIP has a valid ad-hoc signature, includes the status extension, and excludes tests. Owned Debug fixtures/export files and the test runner were removed; the main encrypted store was retained. No App Store/TestFlight submission or physical-iPhone installation was performed.

## Dedicated Android test emulator on the Mac M3

- Created **Medic Logger Android**, AVD `Medic_Logger_Android`: Android 16/API 36 AOSP ARM64, Pixel 8 profile, 2 GB RAM, native Hypervisor.Framework acceleration. SDK/image came from Google's official repository; no Google account is needed. The image and AVD are project-local under `.tools/`.
- Reserved serial **emulator-5556** is explicitly specified for every device command. No physical phone was targeted during this review. `scripts/Open-Android-Simulator.command` launches it without wiping data and safely handles an already-running emulator.
- Delivered Android **1.4.0 (6)** release APK installed and launched successfully; its actual Today screen was inspected and the native crash log was empty. **35 migration/effectiveness checks passed** on an isolated, unminified QA build using temporary databases; result: `artifacts/review/android-simulator-results.txt`. Direct helper execution against the optimized production APK cannot use constructors removed by R8, so that attempt is recorded separately in `.tools/android-emulator-release-instrumentation-attempt.txt` and is not counted as a pass.
- Both QA packages were removed afterwards. Only `fr.mediclogger.app` remains on the emulator; the release app is left running. The Android APK/AAB are unchanged by this iOS fix. Setup/relaunch instructions are in `docs/ANDROID-SIMULATOR.md`.

## Previous Android 1.4.0 feature review

- Package `fr.mediclogger.app`, versionCode **6**, target/compile **36**, minimum **26**. Exactly one Medic Logger application remains on the connected Android phone.
- Settings displays **Version 1.4.0**. The signed release APK was installed and copied to `/sdcard/Download/Medic-Logger.apk` before the user was notified and the AAB was built.
- Configurable ON/OFF cycles use calendar days and an anchor, multiple daily times, OFF-day suppression and expiry. `artifacts/review/cycle-core-results.txt`: **57** core checks plus independent export parsers. `cycle-device-results.txt`: **34** native cycle persistence and migration checks, using owned temporary databases.
- Signed **AAB** passed bundletool validation; a bundle-generated universal APK installed/launched, then the delivered APK was restored. All four native ABIs have **16 KB** ELF alignment. R8 and resource shrinking are enabled, with the mapping supplied. Certificates match; no private signing material is delivered in artifacts.

## Previous iOS 1.4.0 feature review

- Native SwiftUI iPhone/iPad app, minimum iOS **17**, version **1.4.0 (6)**. Xcode 26.6 on Mac M3; dedicated iPhone 17 Pro simulator running iOS 26.5.
- **30 native tests** passed: domain logic, batch-bound stock refunds, chronology, monitoring snapshots, ratings, recurrence/DST/leap dates, encryption/random nonces/tamper rejection, export contracts, custom-color contrast, notification requests/categories/sound/silent/off, and ActivityKit create/update/end.
- **8 UI journeys** passed across the main review and targeted reruns: stock/refill, effects/7-of-10 score/Journal/Calendar, cycle/version, custom hex color/multiple-medicine Excel selection, four languages, maximum accessibility text, saved cycle/notification permission, and enabled Live Activity status. Native switch tests target the actual trailing switch, and numeric-field tests dismiss the name keyboard before switching fields.
- The final dark/increased-contrast run has 30 passing native tests and 7 passing UI journeys; the stock journey passed in `ios-stock-final.xcresult` after its keyboard-focus automation fix. Calendar/effects and maximum-text journeys were rerun successfully after the final layout corrections in `ios-accessibility-final.xcresult`.
- Native Files saved a real JSON export, pulled and parsed in `artifacts/review/ios/ios-ui-export.json`. Synthetic JSON/CSV/XLSX/all-format ZIP fixtures pass `scripts/verify-ios-exports.py`. The event name is `dose_taken` on both platforms. XLSX ratings remain numeric and user text never becomes a formula.
- Actual compact Dynamic Island and Lock Screen status were inspected; `artifacts/review/ios/live-activity-dark.png` records the latter. Screenshots from successful native UI tests are beside the fixtures. Physical vibration/audio and physical-iPhone installation were not claimed.
- Release Simulator build and unsigned device archive both compile. The Simulator ZIP contains the arm64 `.app`, Widget extension and bundled icon/languages/sounds, with no test runner or testing flags. Owned UI fixtures/export files and the test runner were removed from the simulator; main encrypted storage was kept. The release app is left available in the dedicated Simulator.
- No App Store/TestFlight upload, publication or payment was performed. An iPhone install/distribution needs publisher signing; end users do not need keys or developer certificates. Rolling iOS reminder limits are documented in Settings and `ios/README.md`.

Design skills and corrections are recorded in `docs/IOS-DESIGN-REVIEW.md`. Build/test logs and xcresult bundles remain in `.tools/`; review fixtures contain synthetic data only.

---

# Historical Medic Logger 1.3.0 — review on November 5, 2026

Connected A059, Android 16. Final installed package: `fr.mediclogger.app`, versionCode 5. Only this user application remains installed; QA and instrumentation packages were removed. Current APK: `artifacts/Medic-Logger.apk`, also copied to the phone's Downloads folder.

## Fixed issues

- Startup on an existing plaintext database crashed during SQLCipher migration. The source now allows creation of the attached encrypted destination; ATTACH goes through the tracked SQLite API, and sqlcipher_export runs on the write connection. The original remains until the copy is readable and passes its integrity check. Synthetic legacy databases were exercised with and without WAL, including Unicode, snapshots and archive state.
- Removing a reminder group summary could remove another medicine's remaining reminder. A populated summary is retained and updated; notification service updates are reconciled after its asynchronous queue is applied.

## Requested additions

- Removed the On this phone Settings card.
- Separate intake-reminder switch, independent of ongoing medication status, with System default/Sound/Vibration/Sound and vibration/Silent profiles, ringtone selection and Android channel controls.
- Optional 0–10 effectiveness rating after a monitored dose finishes, including the notification Over route. Ratings can be edited or cleared in dose details, appear in cards/calendar, and export as numeric effectiveness_rating in schema 3. Intake-only doses have no rating.
- Explicit medicine selections remain an ID set, so adding a new medicine does not silently include it; All medicines remains a separate option.

## Evidence

- `artifacts/review/android-results.txt`: 117 Android checks before the rating addition. Covered encrypted migration, wrong-key rejection, AES-GCM integrity and large streams, encrypted preference migration, stock/refill identities, concurrent depletion, monitoring snapshots, four languages, destination-color arrows, notification identities/actions, reminder channels, alert profiles, the global switch and actual AlarmManager receiver delivery.
- `artifacts/review/rating-results.txt`: 35 checks on the release application after the rating addition. Used only temporary owned databases: legacy migrations to schema 3, encrypted schema-2 upgrade, null/0/10 scores, rejected out-of-range and active/intake-only ratings, clearing and unchanged inventory.
- `artifacts/review/core-results.txt`: 40 JVM checks and independent format parsers. Covers date/DST boundaries, multiple-medicine selection, recurrence patterns and rating validation. JSON/CSV/XLSX/ZIP fixtures include numeric ratings.
- `artifacts/review/ui-results.txt`: phone UI created medicines with monitoring off/on, decremented stock, recorded onset/end and a 7/10 score, showed it in Journal, checked calendar event/dose counts and preserved format-selection scroll. Android's real document picker saved plaintext JSON and an all-formats ZIP; independent parsers validated the JSON/CSV/nested XLSX and 7/10 value.
- Two owned review doses and two owned review medicines were removed, along with their dynamic launcher shortcuts and phone export fixtures. User records were not cleared by tests.
- Release compilation and lint completed without errors; warnings are documented by the generated lint report. Signature and package metadata are alongside the APK.

The user uninstalled the application during the review; reinstalling started with empty private storage. Legacy retention was verified using synthetic databases. Sound/vibration configuration and native alarm delivery were verified; the ringtone picker was resolved on the device, but selecting a particular audible ringtone was not exercised. No payment was made through the donation link.

---

# Medic Logger 1.2.0

Current scope: stock/refill tracking, multi-medicine export selection with preserved scroll, recurring local reminders, intake-only mode by default, encrypted storage, notification refinements, and the Ko-fi button.

The user requested no tests or visual checks for this update. Only release compilation/signing and installation are performed. The following results belong to the previous version and are not evidence for the new features.

Release 1.2.0 (versionCode 3) compiled successfully with `:app:assembleRelease`, including its normal release build checks. `adb install -r` returned `Success` on the connected A059. The current APK was copied to `/sdcard/Download/Medic-Logger.apk` and its media entry refreshed. No application launch, UI automation, screenshots, functional tests or instrumentation were run for this update.

The current deliverable and checksum are `artifacts/Medic-Logger.apk` and `artifacts/Medic-Logger.apk.sha256`. Other test reports/screenshots in artifacts belong to the historical version below.

# Historical verification — Medic Logger 1.1.0

Validated on the connected A059 phone running Android 16.

- Release APK built, signed and installed as an update; its app data was preserved.
- 36 device checks passed: database persistence, independent overlapping doses, ordered timestamps, notes, snapshots, archiving/deletion, export generation, all four compiled languages and plurals, custom-color persistence, notification Active/Over actions, stale-action identity, idempotency, previous/next navigation, and both destination-color arrow bitmaps.
- 12 JVM core checks passed, including inclusive dates, DST boundaries, filtering and invalid timestamps.
- Independent JSON/CSV/XML/ZIP parsers validated raw and zipped formats, nested XLSX, Unicode, quoted notes and spreadsheet formula handling.
- Android’s actual file picker saved an XLSX and an all-formats ZIP to local Downloads; files were pulled and parsed successfully.
- Phone UI checks covered simultaneous doses, the calendar’s seven events/three doses and empty-day selection, all four language settings, a custom hex color, and a visible notification Next control.
- Android lint: no issues found.
- Packaged release manifest: not debuggable, no Internet permission, no test instrumentation, backups disabled. Only notification-posting and boot-receive permissions are declared.
- APK signature verification passed with the dedicated local RSA signing key.
- Exactly the two owned TEST Alpha/TEST Beta UI fixtures and notification-test fixtures were removed. Other records were preserved. The test package was uninstalled and the phone’s original stay-awake setting restored.

The ready-to-install artifact is `artifacts/Medic-Logger.apk`; its checksum and signature report are alongside it.
