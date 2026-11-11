import Foundation
import UserNotifications
import UIKit
import ActivityKit
import BackgroundTasks

struct PlannedReminder {
    let identifier: String, medicine: Medication, rule: Reminder
    let date: Date, components: DateComponents
    let repeats: Bool
}
enum ReminderPlanner {
    static func plan(_ data: AppData, now: Date = Date(), zone: TimeZone = .current, limit: Int = 60) -> [PlannedReminder] {
        guard data.settings.remindersEnabled else { return [] }
        let rules = data.reminders.filter { r in r.enabled && data.medicines.contains { $0.id == r.medicineID && !$0.archived } && (try? r.validate()) != nil }
        var repeating: [PlannedReminder] = []
        for rule in rules where rule.frequency != "cycle" {
            guard let medicine = data.medicines.first(where: { $0.id == rule.medicineID }) else { continue }
            var patterns: [DateComponents] = []
            if rule.frequency == "daily" { patterns = [DateComponents()] }
            if rule.frequency == "weekly" { patterns = (0..<7).filter { rule.weekDays & (1 << $0) != 0 }.map { var d = DateComponents(); d.weekday = ($0 + 1) % 7 + 1; return d } }
            if rule.frequency == "monthly" { patterns = rule.monthDays.map { var d = DateComponents(); d.day = $0; return d } }
            if rule.frequency == "yearly" { patterns = rule.yearDays.compactMap { value in guard let (m,d) = DayMath.monthDay(value) else { return nil }; var p = DateComponents(); p.month = m; p.day = d; return p } }
            for (index,pattern) in patterns.enumerated() {
                for time in Set(rule.times).sorted() {
                    guard let (h,m,s) = DayMath.timeParts(time) else { continue }
                    var components = pattern; components.calendar = DayMath.calendar(zone); components.hour = h; components.minute = m; components.second = s
                    let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
                    guard let date = trigger.nextTriggerDate() else { continue }
                    repeating.append(PlannedReminder(identifier:"repeat-\(rule.id)-\(index)-\(time)",medicine:medicine,rule:rule,date:date,components:components,repeats:true))
                }
            }
        }
        let useRepeats = repeating.count <= limit / 2 || !rules.contains { $0.frequency == "cycle" }
        var output = useRepeats && repeating.count <= limit ? repeating : []
        let rollingRules = output.isEmpty && !repeating.isEmpty ? rules : rules.filter { $0.frequency == "cycle" }
        var nextDates: [Int64:Date] = [:]
        for r in rollingRules { nextDates[r.id] = try? r.next(after:now,zone:zone) }
        while output.count < limit, let first = nextDates.min(by: { $0.value < $1.value }),
              let rule = rollingRules.first(where: { $0.id == first.key }), let medicine = data.medicines.first(where: { $0.id == rule.medicineID }) {
            let date = first.value
            let components = DayMath.calendar(zone).dateComponents([.year,.month,.day,.hour,.minute,.second], from:date)
            output.append(PlannedReminder(identifier:"dose-\(rule.id)-\(Int64(date.timeIntervalSince1970 * 1000))",medicine:medicine,rule:rule,date:date,components:components,repeats:false))
            nextDates[rule.id] = try? rule.next(after:date,zone:zone)
        }
        return output.sorted { $0.date < $1.date }
    }
}
enum NotificationClient {
    static func requestPermission() async throws -> Bool { try await UNUserNotificationCenter.current().requestAuthorization(options:[.alert,.sound,.badge]) }
    static func register(language: String) {
        let log = UNNotificationAction(identifier:"LOG_DOSE",title:Translations.text("Log dose",language:language),options:[.foreground])
        let category = UNNotificationCategory(identifier:"MEDICATION_REMINDER",actions:[log],intentIdentifiers:[],hiddenPreviewsBodyPlaceholder:Translations.text("Medication reminders",language:language),options:[])
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }
    static func schedule(_ data: AppData) async -> (allowed: Bool,until: Date?) {
        await NotificationQueue.shared.schedule(data)
    }
    fileprivate static func apply(_ data: AppData) async -> (allowed: Bool,until: Date?) {
        let center = UNUserNotificationCenter.current(), settings = await UNUserNotificationCenter.current().notificationSettings()
        let allowed = [.authorized,.provisional,.ephemeral].contains(settings.authorizationStatus)
        register(language:data.settings.language); center.removeAllPendingNotificationRequests()
        guard allowed, data.settings.remindersEnabled, !Task.isCancelled else { return (allowed,nil) }
        let plan = ReminderPlanner.plan(data)
        var scheduled: [PlannedReminder] = []
        for reminder in plan {
            guard !Task.isCancelled else { break }
            let content = UNMutableNotificationContent();content.title = reminder.medicine.name
            let dateText = reminder.date.formatted(.dateTime.day().month(.abbreviated).hour().minute().locale(Locale(identifier:data.settings.language)))
            content.body = (reminder.medicine.dose.isEmpty ? "" : reminder.medicine.dose + " · ") + (reminder.repeats ? Translations.text("Time to take %s",language:data.settings.language,arguments:[reminder.medicine.label]) : Translations.text("Scheduled for %s",language:data.settings.language,arguments:[dateText]))
            content.categoryIdentifier = "MEDICATION_REMINDER"; content.threadIdentifier = "medication-reminders"
            content.userInfo = ["medicineID":reminder.medicine.id,"ruleID":reminder.rule.id,"dueAt":reminder.date.timeIntervalSince1970]
            if data.settings.alertMode != "silent" { content.sound = data.settings.alertMode == "default" || data.settings.sound == "default" ? .default : UNNotificationSound(named:UNNotificationSoundName(rawValue:data.settings.sound + ".wav")) }
            let trigger = UNCalendarNotificationTrigger(dateMatching:reminder.components,repeats:reminder.repeats)
            do { try await center.add(UNNotificationRequest(identifier:reminder.identifier,content:content,trigger:trigger));scheduled.append(reminder) } catch { /* The app shows permission/horizon state; no dose is logged by an alert. */ }
        }
        let rolling = Dictionary(grouping:scheduled.filter { !$0.repeats },by:{ $0.rule.id })
        let horizon = rolling.values.compactMap { $0.map(\.date).max() }.min()
        return (allowed,horizon)
    }
    static func dismissMedicine(_ id: Int64) {
        UNUserNotificationCenter.current().getDeliveredNotifications { posted in
            let ids = posted.filter { ($0.request.content.userInfo["medicineID"] as? NSNumber)?.int64Value == id }.map { $0.request.identifier }
            UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers:ids)
        }
    }
}
private actor NotificationQueue {
    static let shared = NotificationQueue()
    private var tail: Task<(allowed: Bool,until: Date?),Never>?
    func schedule(_ data: AppData) async -> (allowed: Bool,until: Date?) {
        let previous = tail
        let task = Task { _ = await previous?.value;return await NotificationClient.apply(data) }
        tail = task
        return await withTaskCancellationHandler(operation:{ await task.value },onCancel:{ task.cancel() })
    }
}
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completion: @escaping (UNNotificationPresentationOptions)->Void) { completion([.banner,.list,.sound]) }
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completion: @escaping ()->Void) {
        Task { @MainActor in
            if let id = response.notification.request.content.userInfo["medicineID"] as? NSNumber { MedicStore.shared.pendingIntakeID = id.int64Value }
            MedicStore.shared.refreshNotifications();completion()
        }
    }
}
enum LiveStatus {
    @MainActor static func update(_ data: AppData) async {
        let wanted = data.settings.showStatus ? Array(data.doses.filter(\.active).sorted { $0.takenAt > $1.takenAt }.prefix(4)) : []
        for activity in Activity<DoseActivity>.activities where !wanted.contains(where: { $0.id == activity.attributes.sessionID }) { await activity.end(nil,dismissalPolicy:.immediate) }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        for dose in wanted {
            let state = DoseActivity.ContentState(name:dose.label,phase:Translations.text(dose.onsetAt == nil ? "Waiting for effects" : "Effects active",language:data.settings.language),color:dose.color,reference:dose.onsetAt ?? dose.takenAt,waiting:dose.onsetAt == nil,activeLabel:Translations.text("Active",language:data.settings.language),overLabel:Translations.text("Over",language:data.settings.language))
            let content = ActivityContent(state:state,staleDate:nil)
            if let existing = Activity<DoseActivity>.activities.first(where: { $0.attributes.sessionID == dose.id }) { await existing.update(content) }
            else { do { _ = try Activity.request(attributes:DoseActivity(sessionID:dose.id),content:content,pushType:nil) } catch { /* Optional status is restricted by iOS and its user settings. */ } }
        }
    }
}
enum BackgroundRefresh {
    static let identifier = "fr.mediclogger.app.refresh"
    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier:identifier,using:nil) { task in
            submit()
            let work = Task {
                let snapshot = await MainActor.run { MedicStore.shared.data }
                let outcome = await NotificationClient.schedule(snapshot)
                task.setTaskCompleted(success:outcome.allowed && !Task.isCancelled)
            }
            task.expirationHandler = { work.cancel() }
        }
    }
    static func submit() { let request = BGAppRefreshTaskRequest(identifier:identifier);request.earliestBeginDate = Date().addingTimeInterval(6*3600);try? BGTaskScheduler.shared.submit(request) }
}
