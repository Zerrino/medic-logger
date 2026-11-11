# Design review: Medic Logger iOS

## Summary

Native SwiftUI, iOS 17+, for a private medication journal. Its main job is to record an intake and, when explicitly enabled, the beginning and end of observed effects. The red capsule identity, medicine colors and quiet system surfaces serve that task. Five persistent tabs separate Today, Journal, Calendar, Medicines and Export; the gear opens general settings.

Reviewed source, real Simulator screens, native notification requests and ActivityKit lifecycle on the Mac M3. This is not a physical-iPhone, VoiceOver-user, color-profile or App Store approval certification.

Skills read and applied:

- [SwiftUI Expert](https://github.com/AvdLee/SwiftUI-Agent-Skill/blob/main/skills/swiftui-expert-skill/SKILL.md): Observation ownership, stable identity, native controls and semantic typography.
- [Apple Design](https://github.com/dickwu/apple-design-skill/blob/main/SKILL.md): 11 relevant HIG references and its code audit, checked against the current official Apple color/layout/Live Activity pages.
- [SwiftUI UI Patterns](https://github.com/Dimillian/Skills/blob/main/swiftui-ui-patterns/SKILL.md): one navigation stack per tab, item-driven sheets, focus, form structure and isolated previews.

## Critical findings corrected

- **Dark-mode contrast.** The original brand red `#B63840` had **2.94:1** contrast against `#1C1C1E`. Its text/icon variant is now `#F07B83` (**6.37:1**) in dark appearance. The light variant remains `#B63840` on white (**5.79:1**). Increased-contrast variants are `#84252D` (light) and `#FFA8AE` (dark). Custom medicine fills remain the chosen color, while foreground ink is adjusted for legibility and filled-button labels choose black or white. `Shared/AccessibleColor.swift` tests include white, black, blue, gold and yellow at 4.5:1 and 7:1 targets. Citation: `color.md › Best practices`, “Make sure all your app’s colors work well in light, dark, and increased contrast contexts.” [Apple Color](https://developer.apple.com/design/human-interface-guidelines/color)
- **Largest text layout.** Calendar cells, inline date labels and a narrow medicine filter could crowd or wrap at the largest accessibility size. The calendar switches to a native date selector with a separate full-width label; month/year, event times and medicine summaries reflow. Quick-log grids, effect controls, rating choices and weekday controls adapt rather than imposing a small fixed column width. Citation: `typography.md › Supporting Dynamic Type`, “Make sure your app’s layout adapts to all font sizes.” [Apple Typography](https://developer.apple.com/design/human-interface-guidelines/typography)
- **Live Activity surface.** A fixed white background could conflict with dark-mode labels. It now uses a semantic system surface and adaptive medicine ink. The real Lock Screen and compact Dynamic Island presentation were inspected in the dedicated simulator. Citation: `live-activities.md › Best practices` requires readable colors in Dark Mode and on Always-On displays. [Apple Live Activities](https://developer.apple.com/design/human-interface-guidelines/live-activities)

## Improvements applied

- **Medium — touch and feedback:** refill/log controls have larger hit regions; custom card/color controls show a press state. Calendar/rating/day controls use at least 44 pt targets or the system control. Citation: `buttons.md › Best practices`, “Always include a press state for a custom button.” [Apple Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons)
- **Medium — structured input:** medicine fields use native focus chaining, and numeric stock/cycle keyboards have Done. Export date-order errors appear in context. Rating and refill sheets support medium/large detents and a grabber, with explicit dismissal actions. These are applications of the form/sheet patterns; exact composition is design judgment.
- **Medium — useful context:** calendar end events show the optional effectiveness score, rather than requiring opening the dose. Intake-only records never gain effect or rating actions.
- **Medium — privacy and notification expectations:** status is opt-in, sensitive activity names are marked private, notification previews use the native system controls, and settings explain the finite iOS reminder horizon. No fake media player or background audio service is used. Citation: `live-activities.md › Best practices`, “Avoid displaying sensitive information.” [Apple Live Activities](https://developer.apple.com/design/human-interface-guidelines/live-activities)

## What works

- Native TabView, NavigationStack, Form, pickers, menus, sheets and Files export. The tab bar performs navigation, with labels and no disabled destinations. Citation: `tab-bars.md › Best practices`, “Use a tab bar to support navigation, not to provide actions.” [Apple Tab Bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars)
- System fonts and semantic foreground/background colors; persistent version in Settings; medicine/schedule controls live beside the medicine. No redundant app appearance switch or On this phone card. Citation: `settings.md › Best practices` says to respect systemwide settings. [Apple Settings](https://developer.apple.com/design/human-interface-guidelines/settings)
- Localized text, explicit state labels in addition to color, optional rating, reversible edit drafts, confirmation for deletion and external logging requests. Export preserves format/date/medicine state.

## Craft and platform notes

Keep the white/red capsule identity and reserve colored fills for actual medication identity and primary actions. No extra decorative animation was added. This restraint is design judgment appropriate to a frequent logging utility.

The Simulator build and unsigned device archive do not ask end users for certificates or developer keys. Publication signing belongs to the publisher; normal users install through TestFlight/App Store. No store submission or payment was performed. iOS controls notification/haptic delivery, Live Activity lifespan and background refresh; Settings exposes those practical limits instead of promising an indefinite local scheduler.
