import SwiftUI

struct SettingsView: View {
    let store: MedicStore
    @Environment(\.dismiss) private var dismiss
    private var version: String { Bundle.main.object(forInfoDictionaryKey:"CFBundleShortVersionString") as? String ?? "1.4.2" }
    private func binding<Value>(_ key: WritableKeyPath<AppSettings,Value>) -> Binding<Value> { Binding(get:{ store.data.settings[keyPath:key] },set:{ value in store.perform { try store.settings { $0[keyPath:key] = value } } }) }
    var body: some View {
        Form {
            Section(store.t("Language")) {
                Picker(store.t("Language"),selection:binding(\.language)) { Text("English").tag("en");Text("Français").tag("fr");Text("Português").tag("pt");Text("中文").tag("zh") }.accessibilityIdentifier("language-picker")
            }
            Section {
                Toggle(store.t("Show medication status"),isOn:binding(\.showStatus)).accessibilityIdentifier("status-toggle")
            } header: { Text(store.t("Medication status")) } footer: { Text(store.t("Live Activities show monitored doses on the Lock Screen and Dynamic Island. iOS controls their duration and availability.")) }
            Section(store.t("Medication reminders")) {
                Toggle(store.t("Remind me to take medication"),isOn:Binding(get:{ store.data.settings.remindersEnabled },set:{ value in store.perform { try store.settings { $0.remindersEnabled = value } };if value { Task { await store.allowNotifications() } } })).accessibilityIdentifier("reminders-toggle")
                if store.data.settings.remindersEnabled {
                    Picker(store.t("Reminder alert"),selection:binding(\.alertMode)) { Text(store.t("System default")).tag("default");Text(store.t("Sound")).tag("sound");Text(store.t("Silent")).tag("silent") }
                    if store.data.settings.alertMode == "sound" {
                        Picker(store.t("Notification sound"),selection:binding(\.sound)) { Text(store.t("System default")).tag("default");Text(store.t("Gentle")).tag("gentle");Text(store.t("Chime")).tag("chime") }
                    }
                    Text(store.t("Vibration, volume, Focus and notification previews follow your iPhone settings.")).font(.footnote).foregroundStyle(.secondary)
                    if !store.notificationPermission { Button(store.t("Allow notifications")) { Task { await store.allowNotifications() } } }
                    if let date = store.scheduledUntil {
                        Text(store.t("Cycle reminders scheduled through %s",store.dateText(date))).font(.footnote).foregroundStyle(.secondary)
                        Text(store.t("Open Medic Logger regularly to extend cycle reminders. iOS limits pending notifications; background refresh is not guaranteed.")).font(.footnote).foregroundStyle(.secondary)
                    }
                }
                Button(store.t("iPhone notification settings"),systemImage:"arrow.up.right.square") { if let url = URL(string:UIApplication.openNotificationSettingsURLString) { UIApplication.shared.open(url) } }
            }
            Section(store.t("Support Medic Logger")) {
                Link(destination:URL(string:"https://ko-fi.com/zerrino")!) { Label(store.t("Offer me a coffee"),systemImage:"cup.and.saucer.fill") }
            }
            Section(store.t("Legal")) {
                NavigationLink { LegalView(store: store, medical: false) } label: {
                    Label(store.t("Privacy policy"), systemImage: "hand.raised")
                }.accessibilityIdentifier("privacy-policy")
                NavigationLink { LegalView(store: store, medical: true) } label: {
                    Label(store.t("Medical notice"), systemImage: "info.circle")
                }.accessibilityIdentifier("medical-notice")
            }
            Section { HStack { Text(store.t("Version %s",version));Spacer();Image(systemName:"pills.fill").foregroundStyle(Theme.red).accessibilityHidden(true) }.accessibilityIdentifier("app-version") } footer: { Text(store.t("Encrypted storage. Exports are not encrypted.")) }
        }.navigationTitle(store.t("Settings")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement:.confirmationAction) { Button(store.t("Done")) { dismiss() } } }
            .task { store.refreshNotifications() }
    }
}
