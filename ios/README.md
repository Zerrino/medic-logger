# Medic Logger for iOS

Native SwiftUI application for iPhone and iPad, iOS 17+. Version **1.4.2 (8)**. Xcode project: `MedicLogger.xcodeproj`; shared scheme: **MedicLogger**. No runtime dependencies, backend, analytics, account or remote notification service.

The Mac M3 test environment uses the dedicated **Medic Logger iOS** simulator (iPhone 17 Pro, iOS 26.5), UUID `DC991293-A36A-4EF7-9B83-966001C851A9`. Other simulators and the Android phone's data are not used by iOS tests.

## Build and run on this Mac

From the repository root:

```sh
zsh scripts/build-ios.sh
xcrun simctl boot DC991293-A36A-4EF7-9B83-966001C851A9 # only if shut down
xcrun simctl install DC991293-A36A-4EF7-9B83-966001C851A9 ios/DerivedData/Build/Products/Release-iphonesimulator/MedicLogger.app
xcrun simctl launch DC991293-A36A-4EF7-9B83-966001C851A9 fr.mediclogger.app
open -a Simulator --args -CurrentDeviceUDID DC991293-A36A-4EF7-9B83-966001C851A9
```

Or open `ios/MedicLogger.xcodeproj` in Xcode, select **MedicLogger → Medic Logger iOS**, and Run. The ZIP in `artifacts/Medic-Logger-iOS-Simulator.zip` contains the arm64 Simulator `.app`; it is not an iPhone IPA.

`zsh scripts/build-ios.sh --device-archive` additionally creates an unsigned iPhone/iPad archive in `ios/build/MedicLogger.xcarchive`. To install on a physical iPhone or distribute through TestFlight/App Store, select your Apple development team and signing identity in Xcode, then archive and distribute using that account. No Apple account, private certificate or provisioning profile is embedded in this project.

## Features and platform behavior

End users do not configure signing keys or developer certificates. See the [French iPhone installation guide](INSTALL-IPHONE.md) for the intended TestFlight/App Store flow and the publisher's signing responsibility.

- Today, quick aliases, simultaneous doses, intake-only by default, optional effect monitoring, optional 0–10 end-of-dose effectiveness, notes and editable timestamps.
- Optional stock, refills with distinct pack identities, and refunds only for the current pack. Archive preserves history.
- Journal and monthly calendar: each intake has one compact card linking Taken → Onset → End/Closed with its times. Intake-only medicines show Taken once. Events spanning midnight retain their full timeline, with dates on steps outside the selected day. Totals and time since the last intake remain available.
- Daily, chosen weekdays/weekends, monthly, annual and **X days ON / Y days OFF** cycles, with multiple times and a first ON date. These are user-defined schedules, without treatment advice.
- English by default, French, Portuguese and Chinese, bundled offline. Settings show the version. Custom colors use the native picker and hex entry.
- Settings → Legal includes an offline Privacy policy and Medical notice, with publisher/contact Zerrino / afds999@gmail.com, in all four languages. Public policy HTML is prepared in `docs/legal`; publication is postponed. See `docs/STORE-SUBMISSION.md` for the public URL and console information still needed for submission.
- Independent status and intake-reminder settings. Local notifications have Log dose confirmation; optional ActivityKit status shows up to four monitored doses using the iOS Lock Screen/Dynamic Island. Its links open the app and confirm Active/Over before changing data. iOS determines Live Activity availability and lifetime; this is not an indefinite Android-style status service.
- Simple calendar schedules use native repeating notifications when they fit. Cycles and large schedule sets use a chronological rolling queue capped at 60 pending requests. Opening the app, notification actions and opportunistic background refresh extend it. Settings show the conservative scheduling horizon. **Open the app regularly; iOS does not guarantee background refresh or indefinite cycle reminders while the app remains unopened.** System Focus, notification permission, sound, vibration and preview settings apply. Custom notification vibration cannot be programmed by this public iOS API. Default, sound and silent modes and two bundled short PCM sounds are available.
- JSON schema 3, CSV, actual XLSX and optional/all-format ZIP. Inclusive dates select events by their own timestamps, with complete session context; multiple explicit medicine IDs are supported. Date, medicine and format selection remain in place when exporting. Use Files to save locally. Exports are unencrypted and are for analysis, not restorable backups.
- Offer me a coffee opens `https://ko-fi.com/zerrino` in the browser. No medication data accompanies that URL; payments remain on Ko-fi.

## Storage

App data and settings are Codable records sealed with **AES-256-GCM**. The random key is stored in the local **AfterFirstUnlockThisDeviceOnly** Keychain, without synchronization. The private encrypted file is in Application Support, excluded from backups, with file protection until first unlock. Authentication or decoding failures leave the existing file untouched and block writes. No plaintext database, preference copy, analytics or network upload is created. UI test data use a separate encrypted scope, only in Debug builds. The app switcher gets a privacy cover in the background. System notifications and launcher shortcuts display the information explicitly selected for those features.

## Tests

```sh
xcodebuild -project ios/MedicLogger.xcodeproj -scheme MedicLogger \
  -configuration Debug -destination id=DC991293-A36A-4EF7-9B83-966001C851A9 \
  -derivedDataPath ios/DerivedData -parallel-testing-enabled NO test
```

Unit tests use in-memory data or owned temporary encrypted files. UI tests reset only the separate `ui-testing.enc` Debug scope. The native notification integration test needs the notification-permission UI test to have run once on this simulator; rerun that integration test after granting the prompt. Review exports contain only synthetic names and can be removed after verification. Tests and their launcher runner are excluded from the release artifact.

Translations: edit `app/translations.json` or `ios/Translations-extra.json`, then run `python3 scripts/generate-ios-resources.py`. That generator also shares the workbook metadata with Android. `scripts/generate-ios-project.py` regenerates the stable Xcode project when adding files. No additional build generator needs installing.

UI implementation follows [SwiftUI Expert](https://github.com/AvdLee/SwiftUI-Agent-Skill/blob/main/skills/swiftui-expert-skill/SKILL.md) and Apple's [accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility) and [notification](https://developer.apple.com/design/human-interface-guidelines/notifications) guidance: native controls, semantic type, labeled actions, stable IDs, explicit permission and concise notifications.

## iOS 1.4.1 fix

Closing a dose without effects now updates the existing row immediately. Today uses one identity space for active/finished rows and cards read the current dose by ID, so a finished record cannot retain the old Waiting display when moved between sections. A regression reproduced the old issue and verifies no stale controls or duplicate card, including after relaunch. The calendar groups by dose identity, with one compact connected timeline instead of one card per event.
