# Mobile UI decisions

The visual direction follows the user's request: four offline interface languages, concise labels, white surfaces with medical red accents, and no “Local” header badge.

References consulted:

- [Android Mobile Design skill](https://github.com/wshobson/agents/blob/main/plugins/ui-design/skills/mobile-android-design/SKILL.md) and its [layout/component reference](https://github.com/wshobson/agents/blob/main/plugins/ui-design/skills/mobile-android-design/references/details.md).
- [Android accessibility foundations](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility): minimum 48 dp interaction targets, scalable text, body text at least 12 sp, contrast of at least 4.5:1 for text.
- [Material 3 spacing](https://m3.material.io/foundations/layout/grids-spacing/spacing), [navigation bars](https://m3.material.io/components/navigation-bar/overview), and [typography](https://m3.material.io/styles/typography/overview).

Applied in the native Android Views implementation:

- Five stable bottom destinations, plus a header Settings control, centered 12 sp labels, 24 dp line icons, and a small selected indicator rather than four outlined buttons.
- Functional 28 sp screen headings; 16–18 sp item/section titles; 14–16 sp primary text; no body text below 12 sp. Typography uses regular and medium weights.
- Repeated 8/12/16 dp spacing, 20 dp screen margins, consistent alignment and card padding. Content scrolls above the navigation and respects system/keyboard insets.
- One primary action per dose. Compact “Taken / Onset / End” time groups make simultaneous doses distinct. State includes words as well as color.
- Inputs and checkboxes have explicit minimum touch heights. Icon controls and color choices have 48 dp targets. Controls have descriptive accessibility labels.
- The export action remains fixed above the navigation. The format picker uses a two-column grid to reduce scrolling.
- Red `#B63840`, white `#FFFFFF`, dark text `#302326`, secondary text `#6D6063`, and pale rose `#FBE7E7`. The app keeps this requested palette rather than taking colors from the phone's wallpaper.
- Duplicate-dose confirmation, editable dose details, chronological validation, and explicit destructive confirmations.
- User-entered medicine colors distinguish medicines; they do not indicate medical severity or recommended dosing.
- A matching red/white generated adaptive icon, a monochrome themed variant, and a white launch background.

The skill's Compose-specific APIs are not used: this application uses native Java Views and the Android SDK. The visual and interaction rules are applied directly.

The calendar uses Monday-first weeks with marked event days, monthly dose totals, daily counts and medicine groups. Custom colors have RGB/hex input. Translated navigation labels stay short enough for the five-tab layout; Portuguese uses “Medicação”. The status notification uses standard Android actions and explicit previous/next controls rather than unsupported custom swipe handling.

Notification navigation buttons are 56 dp wide with large colored chevrons. Each button previews the destination medicine’s current custom color, and its chevron uses a contrasting light/dark stroke. Compact cards prioritize readable dose labels and navigation for multiple doses; a single-dose card exposes Active/Over directly.


## Notification review — 1.2.0

Reviewed with the same Android Mobile Design skill, its 48 dp interaction and accessibility guidance, and the official [Android notification design guide](https://developer.android.com/design/ui/mobile/guides/home-screen/notifications).

- Retain the user-requested large arrows in the destination medicine’s color. Android owns horizontal swipe gestures; app navigation uses explicit controls.
- Use a decorated custom layout only for the ongoing carousel. Its expanded content separates the medicine name, the current phase with its start time, and dose/intake details. Standard Android actions remain below it. Tapping the body opens that dose.
- Use a native BigText template for scheduled reminders, with distinct schedule-details and Log dose destinations. Simultaneous reminders use an InboxStyle group summary and child alerts, avoiding an extra summary sound.
- Keep ongoing status on a silent low-importance channel, and reminders on a separate normal-importance channel. Settings exposes reminder sound/vibration controls. No media session, foreground service or full-screen alarm is simulated.
- Give both notification types a private visibility default and an anonymous public version for the lock screen. Restore/cancel actions remain explicit, and an intake clears visible reminders for that medicine.
- Intake-only doses have no ongoing effect controls. Effect monitoring is disabled by default when creating a medicine; old dose snapshots retain their original mode.

No visual review was performed on this update, at the user's request.


## Settings and effectiveness — 1.3.0

The privacy information card was removed from Settings at the user's request. Ongoing status and intake reminders are separate controls. Reminder alert profiles use distinct native channels because Android fixes channel behavior when it is created; the original channel remains the System default choice. A local ringtone picker and the current channel's Android controls remain available.

Completed monitored doses offer an optional two-row 0–10 selection with 48 dp touch targets, an explicit Skip action and no preselected score. Intake-only doses omit it. History and calendar use the same dose cards to display the chosen score. Exported ratings remain raw numeric values.
