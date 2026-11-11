import Foundation

struct Medication: Codable, Identifiable, Equatable {
    var id: Int64 = 0
    var name = "", alias = "", dose = "", color = "B63840"
    var favorite = true, archived = false, monitorEffects = false
    var currentBatchID: Int64?
    var label: String { alias.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? name : alias }
}
struct StockBatch: Codable, Identifiable, Equatable {
    var id: Int64, medicineID: Int64
    var initial: Int, remaining: Int
    var createdAt = Date()
}
struct Dose: Codable, Identifiable, Equatable {
    var id: Int64 = 0, medicineID: Int64 = 0
    var medicineName = "", alias = "", dose = "", color = "B63840", notes = ""
    var zoneID = TimeZone.current.identifier
    var takenAt = Date()
    var onsetAt: Date?, endedAt: Date?
    var monitorEffects = true
    var stockBatchID: Int64?, stockUnits = 0, effectiveness: Int?
    var label: String { alias.isEmpty ? medicineName : alias }
    var active: Bool { monitorEffects && endedAt == nil }
    var status: String {
        if !monitorEffects { return "taken" }
        if endedAt != nil { return onsetAt == nil ? "closed_without_effect" : "completed" }
        return onsetAt == nil ? "waiting_for_effect" : "effects_active"
    }
    func validate(now: Date = Date()) throws {
        if !monitorEffects && (onsetAt != nil || endedAt != nil) { throw AppFailure("Effect monitoring is disabled for this dose.") }
        if let rating = effectiveness, !(0...10).contains(rating) || !monitorEffects || endedAt == nil { throw AppFailure("Rate a finished monitored dose from 0 to 10.") }
        guard takenAt.timeIntervalSince1970 > 0 && takenAt <= now else { throw AppFailure("The dose time must be in the past or now.") }
        if let onsetAt, onsetAt < takenAt || onsetAt > now { throw AppFailure("Effects must start after the dose, and cannot start in the future.") }
        if let endedAt, endedAt < (onsetAt ?? takenAt) || endedAt > now { throw AppFailure("The end time must follow the previous event, and cannot be in the future.") }
        if notes.count > 2000 { throw AppFailure("Use up to 2,000 characters for notes.") }
    }
}
struct Reminder: Codable, Identifiable, Equatable {
    var id: Int64 = 0, medicineID: Int64 = 0
    var frequency = "daily", times = ["08:00"], weekDays = 127
    var monthDays = [1], yearDays = ["--01-01"]
    var cycleOnDays = 21, cycleOffDays = 7
    var cycleStart = DayMath.dayString(Date())
    var enabled = true
    func validate() throws {
        guard !times.isEmpty, times.allSatisfy({ DayMath.timeParts($0) != nil }) else { throw AppFailure("Add at least one reminder time.") }
        guard ["daily","weekly","monthly","yearly","cycle"].contains(frequency) else { throw AppFailure("Choose a reminder frequency.") }
        if frequency == "weekly" && weekDays & 127 == 0 { throw AppFailure("Choose at least one weekday.") }
        if frequency == "monthly" && (monthDays.isEmpty || monthDays.contains(where: { !(1...31).contains($0) })) { throw AppFailure("Choose valid days of the month.") }
        if frequency == "yearly" && (yearDays.isEmpty || yearDays.contains(where: { DayMath.monthDay($0) == nil })) { throw AppFailure("Choose at least one annual date.") }
        if frequency == "cycle" {
            guard (1...3650).contains(cycleOnDays), (0...3650).contains(cycleOffDays) else { throw AppFailure("Use 1–3650 ON days and 0–3650 OFF days.") }
            guard DayMath.date(cycleStart) != nil else { throw AppFailure("Choose a valid first ON day.") }
        }
    }
    func cyclePosition(on date: Date, zone: TimeZone = .current) throws -> Int {
        guard cycleOnDays > 0, cycleOffDays >= 0, let anchor = DayMath.date(cycleStart, zone: zone) else { throw AppFailure("Choose a valid first ON day.") }
        let cal = DayMath.calendar(zone)
        let days = cal.dateComponents([.day], from: anchor, to: cal.startOfDay(for: date)).day ?? -1
        return days < 0 ? -1 : days % (cycleOnDays + cycleOffDays)
    }
    func isOnDay(_ date: Date, zone: TimeZone = .current) -> Bool {
        guard let position = try? cyclePosition(on: date, zone: zone) else { return false }
        return position >= 0 && position < cycleOnDays
    }
    func next(after now: Date, zone: TimeZone = .current) throws -> Date {
        try validate()
        let cal = DayMath.calendar(zone)
        var day = cal.startOfDay(for: now)
        if frequency == "cycle", let anchor = DayMath.date(cycleStart, zone: zone), day < anchor { day = anchor }
        let limit = frequency == "yearly" ? 366 * 9 : frequency == "monthly" ? 370 : frequency == "cycle" ? 3 : 8
        for _ in 0..<limit {
            if frequency == "cycle" {
                let position = try cyclePosition(on: day, zone: zone)
                if position >= cycleOnDays { day = cal.date(byAdding: .day, value: cycleOnDays + cycleOffDays - position, to: day)! }
            }
            let parts = cal.dateComponents([.weekday,.day,.month], from: day)
            let weekdayBit = ((parts.weekday ?? 1) + 5) % 7
            let annual = String(format: "--%02d-%02d", parts.month ?? 1, parts.day ?? 1)
            let matches = frequency == "daily" || frequency == "cycle" || (frequency == "weekly" && weekDays & (1 << weekdayBit) != 0) || (frequency == "monthly" && monthDays.contains(parts.day ?? 0)) || (frequency == "yearly" && yearDays.contains(annual))
            if matches {
                let dates = Set(times).compactMap { DayMath.time($0, on: day, calendar: cal) }.filter { $0 > now }
                if let first = dates.min() { return first }
            }
            day = cal.date(byAdding: .day, value: 1, to: day)!
        }
        throw AppFailure("No upcoming reminder date was found.")
    }
}
struct AppSettings: Codable, Equatable {
    var language = "en", showStatus = false, remindersEnabled = true
    var alertMode = "default", sound = "default"
    var customColors: [String] = []
}
struct AppData: Codable, Equatable {
    var schema = 1, nextID: Int64 = 1
    var medicines: [Medication] = [], batches: [StockBatch] = [], doses: [Dose] = [], reminders: [Reminder] = []
    var settings = AppSettings()
    mutating func allocateID() -> Int64 { defer { nextID += 1 }; return nextID }
}
struct AppFailure: LocalizedError {
    var key: String
    init(_ key: String) { self.key = key }
    var errorDescription: String? { key }
}
enum DayMath {
    static func calendar(_ zone: TimeZone = .current) -> Calendar { var c = Calendar(identifier: .gregorian); c.timeZone = zone; c.firstWeekday = 2; return c }
    static func dayString(_ date: Date, zone: TimeZone = .current) -> String { let p = calendar(zone).dateComponents([.year,.month,.day], from: date); return String(format: "%04d-%02d-%02d", p.year!, p.month!, p.day!) }
    static func date(_ text: String, zone: TimeZone = .current) -> Date? {
        let parts = text.split(separator: "-").compactMap { Int($0) }; guard parts.count == 3 else { return nil }
        let cal = calendar(zone)
        guard let date = cal.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2])), dayString(date, zone: zone) == text else { return nil }; return date
    }
    static func timeParts(_ text: String) -> (Int,Int,Int)? {
        let parts = text.split(separator: ":", omittingEmptySubsequences: false)
        let p = parts.compactMap { Int($0) }; guard p.count == parts.count, (2...3).contains(p.count), (0...23).contains(p[0]), (0...59).contains(p[1]), p.count == 2 || (0...59).contains(p[2]) else { return nil }; return (p[0],p[1],p.count == 3 ? p[2] : 0)
    }
    static func time(_ text: String, on day: Date, calendar: Calendar) -> Date? {
        guard let (h,m,s) = timeParts(text) else { return nil }
        return calendar.date(bySettingHour: h, minute: m, second: s, of: day, matchingPolicy: .nextTimePreservingSmallerComponents, repeatedTimePolicy: .first, direction: .forward)
    }
    static func monthDay(_ text: String) -> (Int,Int)? { guard text.hasPrefix("--"), let d = date("2000" + text.dropFirst()) else { return nil }; let p = calendar().dateComponents([.month,.day], from: d); return (p.month!,p.day!) }
}
