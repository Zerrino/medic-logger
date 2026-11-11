import SwiftUI
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate {
    private let notifications = NotificationDelegate()
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey:Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = notifications
        BackgroundRefresh.register()
        if let shortcut = options?[.shortcutItem] as? UIApplicationShortcutItem { Task { @MainActor in Self.handle(shortcut) } }
        return true
    }
    func application(_ application: UIApplication, configurationForConnecting session: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        if let shortcut = options.shortcutItem { Task { @MainActor in Self.handle(shortcut) } }
        let configuration = UISceneConfiguration(name:nil,sessionRole:session.role);configuration.delegateClass = ShortcutSceneDelegate.self;return configuration
    }
    @MainActor static func handle(_ shortcut: UIApplicationShortcutItem) {
        if let number = shortcut.userInfo?["medicineID"] as? NSNumber { MedicStore.shared.pendingIntakeID = number.int64Value }
    }
}
final class ShortcutSceneDelegate: NSObject, UIWindowSceneDelegate {
    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem, completionHandler: @escaping (Bool)->Void) { Task { @MainActor in AppDelegate.handle(shortcutItem);completionHandler(true) } }
}
@main struct MedicLoggerApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var store = MedicStore.shared
    @Environment(\.scenePhase) private var scenePhase
    var body: some Scene {
        WindowGroup {
            RootView(store:store).tint(Theme.red).environment(\.locale,store.locale)
                .overlay { if scenePhase == .background { ZStack { Theme.background.ignoresSafeArea();Image(systemName:"lock.shield").font(.largeTitle).foregroundStyle(Theme.red) }.accessibilityHidden(true) } }
                .task { store.updateShortcuts();store.refreshNotifications() }
                .onChange(of:scenePhase) { _,phase in if phase == .active { store.refreshNotifications() };if phase == .background { BackgroundRefresh.submit() } }
        }
    }
}

enum AppSheet: Identifiable {
    case settings, medicine(Medication), dose(Dose), rating(Dose), reminder(Reminder), refill(Medication)
    var id: String { switch self { case .settings:"settings";case .medicine(let m):"medicine-\(m.id)";case .dose(let d):"dose-\(d.id)";case .rating(let d):"rating-\(d.id)";case .reminder(let r):"reminder-\(r.id)";case .refill(let m):"refill-\(m.id)" } }
    var compact: Bool { switch self { case .rating,.refill:true;default:false } }
}
struct RootNotice: Identifiable {
    enum Kind { case error(String), intake(Medication,Int), mark(Dose,Bool) }
    let id = UUID();let kind: Kind
}
struct RootView: View {
    let store: MedicStore
    @State private var selectedTab = 0
    @State private var sheet: AppSheet?
    @State private var notice: RootNotice?
    var body: some View {
        Group {
            if let error = store.loadError { VStack(spacing:16) { Image(systemName:"lock.shield").font(.largeTitle);Text(store.t(error)).multilineTextAlignment(.center) }.padding() }
            else { tabs }
        }
        .sheet(item:$sheet,onDismiss:flushPending) { destination in
            NavigationStack { sheetView(destination) }.tint(Theme.red).environment(\.locale,store.locale).presentationDetents(destination.compact ? [.medium,.large] : [.large]).presentationDragIndicator(.visible)
        }
        .onChange(of:store.pendingIntakeID) { flushPending() }
        .onChange(of:store.pendingRatingID) { flushPending() }
        .onChange(of:store.pendingDetailsID) { flushPending() }
        .onChange(of:store.message) { flushPending() }
        .task { flushPending() }
        .onOpenURL(perform:openURL)
        .alert(alertTitle,isPresented:Binding(get:{ notice != nil },set:{ if !$0 { notice = nil } }),presenting:notice) { value in
            switch value.kind {
            case .error: Button("OK",role:.cancel) { notice = nil }
            case .intake(let medicine,_): Button(store.t("Log dose")) { notice = nil;store.perform { try store.take(medicine.id) } };Button(store.t("Cancel"),role:.cancel) { notice = nil }
            case .mark(let dose,let onset): Button(store.t(onset ? "Active" : "Over")) { notice = nil;store.perform { try store.mark(dose.id,onset:onset) } };Button(store.t("Cancel"),role:.cancel) { notice = nil }
            }
        } message: { value in
            switch value.kind {
            case .error(let text): Text(text)
            case .intake(_,let count): if count > 0 { Text(store.t("%d doses are already active for this medicine. Log a separate dose?",count)) }
            case .mark(let dose,_): Text(dose.medicineName)
            }
        }
    }
    private var tabs: some View {
        TabView(selection:$selectedTab) {
            Screen(title:"Medic Logger",settingsLabel:store.t("Settings"),settings:{ sheet = .settings }) { TodayView(store:store,show:{ sheet = $0 }) }.tabItem { Label(store.t("Today"),systemImage:"pills") }.tag(0)
            Screen(title:store.t("Journal"),settingsLabel:store.t("Settings"),settings:{ sheet = .settings }) { JournalView(store:store,show:{ sheet = $0 }) }.tabItem { Label(store.t("Journal"),systemImage:"list.bullet.rectangle") }.tag(1)
            Screen(title:store.t("Calendar"),settingsLabel:store.t("Settings"),settings:{ sheet = .settings }) { CalendarView(store:store,show:{ sheet = $0 }) }.tabItem { Label(store.t("Calendar"),systemImage:"calendar") }.tag(2)
            Screen(title:store.t("Medicines"),settingsLabel:store.t("Settings"),settings:{ sheet = .settings }) { MedicinesView(store:store,show:{ sheet = $0 }) }.tabItem { Label(store.t("Meds"),systemImage:"cross.case") }.tag(3)
            Screen(title:store.t("Export"),settingsLabel:store.t("Settings"),settings:{ sheet = .settings }) { ExportView(store:store) }.tabItem { Label(store.t("Export"),systemImage:"square.and.arrow.up") }.tag(4)
        }
    }
    @ViewBuilder private func sheetView(_ destination: AppSheet) -> some View {
        switch destination {
        case .settings: SettingsView(store:store)
        case .medicine(let m): MedicineEditor(store:store,medicine:m)
        case .dose(let d): DoseEditor(store:store,dose:d)
        case .rating(let d): RatingView(store:store,dose:d)
        case .reminder(let r): ReminderEditor(store:store,reminder:r)
        case .refill(let m): RefillView(store:store,medicine:m)
        }
    }
    private var alertTitle: String {
        guard let notice else { return "" }
        switch notice.kind { case .error:return store.t("Please try again.");case .intake(let m,let count):return store.t(count > 0 ? "Another dose of %s?" : "Log a dose of %s?",m.label);case .mark(_,let onset):return store.t(onset ? "Effects started" : "Dose closed / effects ended") }
    }
    private func flushPending() {
        guard notice == nil else { return }
        if let message = store.message { store.message = nil;notice = RootNotice(kind:.error(message));return }
        guard sheet == nil else { return }
        if let id = store.pendingRatingID { store.pendingRatingID = nil;if let dose = store.data.doses.first(where:{ $0.id == id && $0.monitorEffects && $0.endedAt != nil }) { sheet = .rating(dose) };return }
        if let id = store.pendingDetailsID { store.pendingDetailsID = nil;if let dose = store.data.doses.first(where:{ $0.id == id }) { sheet = .dose(dose) };return }
        if let id = store.pendingIntakeID {
            store.pendingIntakeID = nil
            if let medicine = store.medicines.first(where:{ $0.id == id }) { notice = RootNotice(kind:.intake(medicine,store.data.doses.filter { $0.medicineID == id && $0.active }.count)) }
            else { notice = RootNotice(kind:.error(store.t("This medicine is archived or no longer exists."))) }
        }
    }
    private func openURL(_ url: URL) {
        guard url.scheme == "mediclogger",url.host == "dose",let first = url.pathComponents.dropFirst().first,let id = Int64(first),let dose = store.data.doses.first(where:{ $0.id == id }) else { return }
        let action = url.pathComponents.last
        if dose.active && (action == "over" || (action == "active" && dose.onsetAt == nil)) { notice = RootNotice(kind:.mark(dose,action == "active")) }
        else { store.pendingDetailsID = id;flushPending() }
    }
}
