# Medic Logger 1.4.2 — legal information for store submission

Reviewed against official Apple and Google documentation on November 5, 2026. This records the implemented behavior and remaining store configuration; it does not guarantee store approval.

## Included in both apps

**Settings → Legal** contains the offline **Privacy policy** and **Medical notice**, in English, French, Portuguese and Chinese. Publisher: **Zerrino**. Public privacy/support contact: **afds999@gmail.com**, supplied by the publisher. Android also includes **Open-source licenses**, with full applicable notices for its resolved runtime dependencies. iOS uses Apple's system frameworks and does not bundle the Android libraries.

The policy explains local records/settings, encrypted private storage, notifications/shortcuts, unencrypted exports including ZIP, deletion/retention, external links and support email. It does not promise recovery, sync or encrypted exports. The medical notice describes a personal journal, subjective observations and user-defined schedules, with consultation of a qualified healthcare professional and a statement that this is not a medical device.

Shared source: `assets/legal/content.json`. Run `python3 scripts/generate-legal.py` after changes; it generates both native content files and four static pages in `docs/legal`. Translations are in `app/translations.json`; regenerate Android/iOS resources as documented in the root README.

## Public policy URL — publication postponed

The publisher chose **not to publish a website yet**. `privacy_url` is `null`; both apps show the complete offline policy and no broken or placeholder web link. Ready-to-host pages are in `docs/legal` and `artifacts/Medic-Logger-privacy-pages.zip`. They contain no analytics, remote fonts or journal data.

Before submitting, host the pages at a stable public HTTPS address, accessible without sign-in, payment or geographic restriction, and enter that policy URL in both consoles. Google does not accept a PDF. Set `privacy_url` in the shared JSON to the actual address, regenerate and rebuild to add **View online** inside the apps. Check the anonymous URL before submitting; a local file or private preview is insufficient.

Sources: [Apple privacy requirements](https://developer.apple.com/app-store/review/guidelines/#privacy), [Google User Data](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en), [Google Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en).

## Store information

| Field | Current behavior / remaining action |
| --- | --- |
| Privacy policy URL | Add the public URL after hosting the prepared page. |
| Developer/support contact | Zerrino / afds999@gmail.com. Complete each store's verified legal identity and trader/account information separately; the display pseudonym does not replace verification. |
| Google Health apps declaration | Declare medication journal/reminder functionality accurately. No Health Connect, sensor measurement, clinical research or regulated-device certification is claimed. |
| Google Data safety / Apple App Privacy | The journal is processed locally; no account, advertising, analytics, tracking SDK or developer backend. On-device-only processing is not off-device collection under store definitions. User-chosen exports and external browser/email flows remain disclosed in the policy; review the current console questions when submitting. |
| Account deletion | No app account is created. Doses/medicines can be deleted; the policy explains removal of the local journal and separate export copies. No server-side record deletion is claimed. |
| Store description | Include the medical notice below. Avoid diagnosis, treatment, clinical-accuracy claims or promises that every reminder will arrive. |
| Apple EULA | Apple's standard EULA applies if no custom agreement is supplied. This update introduces no subscription or custom service agreement. |
| Donations | Android: optional consumable Google Play Billing products (`docs/PLAY-BILLING.md`) that grant no app content/features. iOS: Ko-fi opens externally. Check the final configuration against the current payment rules for each storefront. |

Sources: [Google Health declaration](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en), [Google on-device data definitions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), [Apple collection definitions](https://developer.apple.com/app-store/app-privacy-details/), [Apple standard EULA](https://developer.apple.com/help/app-store-connect/manage-app-information/provide-a-custom-license-agreement), [Apple medical/payment guidelines](https://developer.apple.com/app-store/review/guidelines/).

## Suggested English store notice

Medic Logger is a personal medication journal, not a medical device. It does not diagnose, treat, cure or prevent any medical condition. Consult a qualified healthcare professional for medical advice and before changing treatment. Schedules are set by you and are not prescriptions or dosing recommendations. Reminders may be delayed or missed because of device or system restrictions.

## Build scope

Both apps are **1.4.2 (8)**. Android release APK/AAB compilation and release lint passed; the APK signature and official bundletool validation passed. iOS Release Simulator build, ad-hoc signature and unsigned device archive compilation passed. Releases were updated in the dedicated Mac simulators while retaining app data. No store submission, website publication or Apple account signing was performed.

Reading bundled legal text makes no network request, changes no journal data and requires no consent dialog. Website publication was postponed at the publisher's request.
