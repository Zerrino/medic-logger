# Google Play release automation

No official Google MCP server exists for Play Console. Community MCP servers wrap the
[Android Publisher API](https://developers.google.com/android-publisher); evaluate the
repository and maintainer of any of them before giving it credentials.

## One-time manual steps (the API cannot do these)
1. Create the app in Play Console and publish a first release by hand (drafts only until then).
2. Declare `SCHEDULE_EXACT_ALARM` (medication reminders) and complete Data safety, health-apps and content-rating forms.
3. Enrol in Play App Signing; register `.signing/medic-logger.jks` as the **upload key** (`release` signing config).
4. Google Cloud: create a project, enable *Google Play Android Developer API*, create a service account,
   download its JSON key **outside the repo** (`*service-account*.json` is git-ignored), then invite the
   service account in Play Console → Users and permissions with release permissions on this app.

## Per release
```
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
# bump versionCode / versionName in app/build.gradle
./gradlew :app:lintRelease :app:bundleRelease
# upload app/build/outputs/bundle/release/app-release.aab to the internal track
```
Start on the `internal` track, then promote to closed/production. Upload `mapping.txt` with the bundle
(done automatically for AABs) so crash traces deobfuscate.
