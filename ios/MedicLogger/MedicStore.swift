import Foundation
import Observation
import UIKit

extension AppData {
    func remaining(_ medicine: Medication) -> Int? { batches.first { $0.id == medicine.currentBatchID && $0.medicineID == medicine.id }?.remaining }
    mutating func saveMedicine(_ input: Medication, quantity: Int? = nil) throws -> Int64 {
        var medicine = input
        medicine.name = medicine.name.trimmingCharacters(in: .whitespacesAndNewlines)
        medicine.alias = medicine.alias.trimmingCharacters(in: .whitespacesAndNewlines)
        medicine.dose = medicine.dose.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !medicine.name.isEmpty, medicine.name.count <= 80 else { throw AppFailure("Enter a medicine name (up to 80 characters).") }
        guard medicine.alias.count <= 20, medicine.dose.count <= 80 else { throw AppFailure("Use up to 20 characters for the short name and 80 for the dose.") }
        if !medicine.archived && medicines.contains(where: { $0.id != medicine.id && !$0.archived && $0.name.caseInsensitiveCompare(medicine.name) == .orderedSame && $0.dose.caseInsensitiveCompare(medicine.dose) == .orderedSame }) { throw AppFailure("This medicine and dose already exist. Use the saved shortcut.") }
        if medicine.id == 0 {
            if let quantity { try Self.validateQuantity(quantity) }
            medicine.id = allocateID(); medicines.append(medicine)
            if let quantity { try refill(medicine.id, quantity: quantity) }
        } else {
            guard let index = medicines.firstIndex(where: { $0.id == medicine.id }) else { throw AppFailure("This medicine is archived or no longer exists.") }
            medicine.currentBatchID = medicines[index].currentBatchID
            medicines[index] = medicine
        }
        return medicine.id
    }
    static func validateQuantity(_ value: Int) throws { guard (0...1_000_000_000).contains(value) else { throw AppFailure("Enter a quantity from 0 to 1,000,000,000.") } }
    mutating func refill(_ medicineID: Int64, quantity: Int) throws {
        try Self.validateQuantity(quantity)
        guard let index = medicines.firstIndex(where: { $0.id == medicineID && !$0.archived }) else { throw AppFailure("This medicine is archived or no longer exists.") }
        let id = allocateID(); batches.append(StockBatch(id: id, medicineID: medicineID, initial: quantity, remaining: quantity)); medicines[index].currentBatchID = id
    }
    mutating func take(_ medicineID: Int64, now: Date = Date()) throws -> Int64 {
        guard let medicine = medicines.first(where: { $0.id == medicineID && !$0.archived }) else { throw AppFailure("This medicine is archived or no longer exists.") }
        var dose = Dose(); dose.medicineID = medicineID; dose.medicineName = medicine.name; dose.alias = medicine.alias; dose.dose = medicine.dose; dose.color = medicine.color; dose.monitorEffects = medicine.monitorEffects; dose.takenAt = now
        try dose.validate(now: now)
        if let batch = medicine.currentBatchID {
            guard let index = batches.firstIndex(where: { $0.id == batch && $0.medicineID == medicineID }), batches[index].remaining > 0 else { throw AppFailure("This pack is empty. Refill it before logging a dose.") }
            batches[index].remaining -= 1; dose.stockBatchID = batch; dose.stockUnits = 1
        }
        dose.id = allocateID(); doses.append(dose); return dose.id
    }
    mutating func mark(_ id: Int64, onset: Bool, now: Date = Date()) throws {
        guard let index = doses.firstIndex(where: { $0.id == id }), doses[index].active else { throw AppFailure("This dose is already closed.") }
        var dose = doses[index]
        if onset { guard dose.onsetAt == nil else { throw AppFailure("The effects have already been recorded.") }; dose.onsetAt = now } else { dose.endedAt = now }
        try dose.validate(now: now); doses[index] = dose
    }
    mutating func saveDose(_ value: Dose) throws {
        guard let index = doses.firstIndex(where: { $0.id == value.id }) else { throw AppFailure("This dose no longer exists.") }
        let old = doses[index]; var value = value
        guard old.medicineID == value.medicineID else { throw AppFailure("A dose cannot be moved to a different medicine.") }
        value.stockBatchID = old.stockBatchID; value.stockUnits = old.stockUnits; value.monitorEffects = old.monitorEffects
        try value.validate(); doses[index] = value
    }
    mutating func rate(_ id: Int64, value: Int?) throws {
        guard var dose = doses.first(where: { $0.id == id }) else { throw AppFailure("This dose no longer exists.") }
        dose.effectiveness = value; try saveDose(dose)
    }
    mutating func deleteDose(_ id: Int64) {
        guard let dose = doses.first(where: { $0.id == id }) else { return }
        doses.removeAll { $0.id == id }
        if let batchID = dose.stockBatchID, dose.stockUnits > 0,
           medicines.first(where: { $0.id == dose.medicineID })?.currentBatchID == batchID,
           let index = batches.firstIndex(where: { $0.id == batchID && $0.medicineID == dose.medicineID }), batches[index].remaining + dose.stockUnits <= batches[index].initial { batches[index].remaining += dose.stockUnits }
    }
    mutating func deleteMedicine(_ id: Int64) throws {
        guard !doses.contains(where: { $0.medicineID == id }) else { throw AppFailure("This medicine has recorded doses. Keep it archived, or delete its doses from Journal first.") }
        medicines.removeAll { $0.id == id }; reminders.removeAll { $0.medicineID == id }; batches.removeAll { $0.medicineID == id }
    }
    mutating func saveReminder(_ value: Reminder) throws -> Int64 {
        try value.validate()
        guard medicines.contains(where: { $0.id == value.medicineID && !$0.archived }) else { throw AppFailure("This medicine is archived or no longer exists.") }
        var value = value; value.times = Array(Set(value.times)).sorted()
        if value.id == 0 { value.id = allocateID(); reminders.append(value) }
        else if let index = reminders.firstIndex(where: { $0.id == value.id }) { reminders[index] = value }
        else { throw AppFailure("This reminder no longer exists.") }
        return value.id
    }
}

@MainActor @Observable final class MedicStore {
    static let shared = MedicStore()
    private(set) var data = AppData()
    private(set) var loadError: String?
    var message: String?
    var pendingIntakeID: Int64?
    var pendingRatingID: Int64?
    var pendingDetailsID: Int64?
    var notificationPermission = false
    var scheduledUntil: Date?
    @ObservationIgnored private var storage: SecureStorage?
    @ObservationIgnored private var notificationTask: Task<Void,Never>?
    #if DEBUG
    init(preview data: AppData) { self.data = data }
    #endif
    init(storage supplied: SecureStorage? = nil) {
        do {
            #if DEBUG
            let testing = ProcessInfo.processInfo.arguments.contains("--ui-testing")
            #else
            let testing = false
            #endif
            let storage = try supplied ?? SecureStorage(scope: testing ? "ui-testing" : "main")
            self.storage = storage
            #if DEBUG
            if testing && ProcessInfo.processInfo.arguments.contains("--reset-test-data") { try storage.write(AppData()) }
            #endif
            if FileManager.default.fileExists(atPath: storage.url.path) { data = try storage.read() }
            else { try storage.write(data) }
            guard data.schema == 1 else { throw AppFailure("This data version is not supported.") }
        } catch { loadError = "Encrypted storage could not be opened. Existing data was kept."; self.storage = nil }
    }
    var language: String { data.settings.language }
    var locale: Locale { Locale(identifier: ["en":"en_GB","fr":"fr_FR","pt":"pt_PT","zh":"zh_CN"][language] ?? "en_GB") }
    var medicines: [Medication] { data.medicines.filter { !$0.archived }.sorted { $0.favorite != $1.favorite ? $0.favorite : $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending } }
    var doses: [Dose] { data.doses.sorted { $0.takenAt != $1.takenAt ? $0.takenAt > $1.takenAt : $0.id > $1.id } }
    func t(_ key: String, _ args: CVarArg...) -> String { Translations.text(key, language: language, arguments: args) }
    func displayError(_ error: Error) { message = t((error as? AppFailure)?.key ?? "Could not save app data.") }
    func perform(_ operation: () throws -> Void) { do { try operation() } catch { displayError(error) } }
    func mutate(_ operation: (inout AppData) throws -> Void) throws {
        guard let storage else { throw AppFailure("Encrypted storage could not be opened. Existing data was kept.") }
        var next = data; try operation(&next); try storage.write(next); data = next; updateShortcuts(); refreshNotifications()
    }
    @discardableResult func take(_ id: Int64) throws -> Int64 {
        var result: Int64 = 0; try mutate { result = try $0.take(id) }; NotificationClient.dismissMedicine(id); return result
    }
    func mark(_ id: Int64, onset: Bool) throws { try mutate { try $0.mark(id,onset:onset) }; if !onset { pendingRatingID = id } }
    func settings(_ update: (inout AppSettings) -> Void) throws { try mutate { update(&$0.settings) } }
    func refreshNotifications() {
        let previous = notificationTask; previous?.cancel(); let snapshot = data
        notificationTask = Task {
            await previous?.value
            guard !Task.isCancelled else { return }
            let result = await NotificationClient.schedule(snapshot)
            if !Task.isCancelled { notificationPermission = result.allowed; scheduledUntil = result.until }
            if !Task.isCancelled { await LiveStatus.update(snapshot) }
        }
    }
    func allowNotifications() async {
        do { notificationPermission = try await NotificationClient.requestPermission(); refreshNotifications() }
        catch { displayError(error) }
    }
    func updateShortcuts() {
        UIApplication.shared.shortcutItems = medicines.filter(\.favorite).prefix(4).map { m in
            UIApplicationShortcutItem(type: "medicine-\(m.id)", localizedTitle: m.label, localizedSubtitle: t("Log dose: %s",m.name), icon: UIApplicationShortcutIcon(type: .time), userInfo:["medicineID":NSNumber(value:m.id)])
        }
    }
}
