import SwiftUI

extension MedicStore {
    func reminderSummary(_ r: Reminder) -> String {
        let name = ["daily":"Every day","weekly":"Weekly","monthly":"Monthly","yearly":"Yearly","cycle":"ON/OFF cycle"][r.frequency] ?? "Reminders"
        return (r.frequency == "cycle" ? t("%d days ON · %d days OFF",r.cycleOnDays,r.cycleOffDays) : t(name)) + " · " + r.times.joined(separator:" · ")
    }
}
struct RemindersView: View {
    let store: MedicStore,medicine: Medication
    @State private var editing: Reminder?
    @State private var deleting: Reminder?
    private var rules: [Reminder] { store.data.reminders.filter { $0.medicineID == medicine.id } }
    var body: some View {
        List {
            Section { Text(medicine.name).font(.headline);Button(store.t("Add reminder"),systemImage:"plus") { var r = Reminder();r.medicineID = medicine.id;editing = r }.accessibilityIdentifier("add-reminder") }
            if !store.data.settings.remindersEnabled { Text(store.t("Medication reminders are off in Settings")).font(.footnote).foregroundStyle(.secondary) }
            if rules.isEmpty { EmptyState(title:store.t("No reminders yet"),symbol:"bell") }
            ForEach(rules) { rule in
                VStack(alignment:.leading,spacing:10) {
                    Toggle(isOn:Binding(get:{ store.data.reminders.first(where:{ $0.id == rule.id })?.enabled ?? false },set:{ value in store.perform { try store.mutate { data in if let i = data.reminders.firstIndex(where:{ $0.id == rule.id }) { data.reminders[i].enabled = value } } } })) { Text(store.reminderSummary(rule)).font(.subheadline.weight(.medium)) }
                    if let next = try? rule.next(after:Date()) { Text(store.t("Next: %s",store.dateText(next))).font(.caption).foregroundStyle(.secondary) }
                    if rule.frequency == "cycle" { CyclePhase(store:store,reminder:rule) }
                    Button(store.t("Edit")) { editing = rule }
                }.padding(.vertical,6).swipeActions { Button(store.t("Delete"),role:.destructive) { deleting = rule } }
            }
        }.navigationTitle(store.t("Reminders")).navigationBarTitleDisplayMode(.inline)
            .sheet(item:$editing) { reminder in NavigationStack { ReminderEditor(store:store,reminder:reminder) } }
            .confirmationDialog(store.t("Delete reminder?"),isPresented:Binding(get:{ deleting != nil },set:{ if !$0 { deleting = nil } }),titleVisibility:.visible) { Button(store.t("Delete"),role:.destructive) { if let rule = deleting { store.perform { try store.mutate { $0.reminders.removeAll { $0.id == rule.id } } } };deleting = nil } }
    }
}
struct CyclePhase: View {
    let store: MedicStore,reminder: Reminder
    private var text: String {
        let pos = (try? reminder.cyclePosition(on:Date())) ?? -1
        if pos < 0 { return store.t("Starts %s",reminder.cycleStart) }
        return pos < reminder.cycleOnDays ? store.t("ON · Day %d of %d",pos+1,reminder.cycleOnDays) : store.t("OFF · Day %d of %d",pos-reminder.cycleOnDays+1,reminder.cycleOffDays)
    }
    var body: some View { Text(text).font(.caption.weight(.semibold)).foregroundStyle(Theme.red) }
}
struct TimeDraft: Identifiable { let id = UUID();var date: Date }
struct AnnualDraft: Identifiable { let id = UUID();var date: Date }
struct ReminderEditor: View {
    let store: MedicStore
    @State private var draft: Reminder
    @State private var times: [TimeDraft]
    @State private var annual: [AnnualDraft]
    @State private var onDays: String
    @State private var offDays: String
    @State private var anchor: Date
    @State private var error: String?
    @Environment(\.dynamicTypeSize) private var typeSize
    private enum CycleField: Hashable { case on,off }
    @FocusState private var field: CycleField?
    @Environment(\.dismiss) private var dismiss
    init(store: MedicStore,reminder: Reminder) {
        self.store = store;_draft = State(initialValue:reminder)
        _times = State(initialValue:reminder.times.map { TimeDraft(date:DayMath.time($0,on:Date(),calendar:DayMath.calendar()) ?? Date()) })
        _annual = State(initialValue:reminder.yearDays.compactMap { DayMath.date("2000" + $0.dropFirst()).map { AnnualDraft(date:$0) } })
        _onDays = State(initialValue:String(reminder.cycleOnDays));_offDays = State(initialValue:String(reminder.cycleOffDays));_anchor = State(initialValue:DayMath.date(reminder.cycleStart) ?? Date())
    }
    var body: some View {
        Form {
            Section {
                Toggle(store.t("Enabled"),isOn:$draft.enabled)
                Picker(store.t("Frequency"),selection:$draft.frequency) { Text(store.t("Every day")).tag("daily");Text(store.t("Weekly")).tag("weekly");Text(store.t("Monthly")).tag("monthly");Text(store.t("Yearly")).tag("yearly");Text(store.t("ON/OFF cycle")).tag("cycle") }.accessibilityIdentifier("reminder-frequency")
            }
            if draft.frequency == "weekly" { weekdaySection }
            if draft.frequency == "monthly" { monthSection }
            if draft.frequency == "yearly" { annualSection }
            if draft.frequency == "cycle" { cycleSection }
            Section(store.t("Times")) {
                ForEach($times) { $time in DatePicker(store.t("Time"),selection:$time.date,displayedComponents:.hourAndMinute) }.onDelete { times.remove(atOffsets:$0) }
                Button(store.t("+ Add time")) { times.append(TimeDraft(date:Date())) }
            }
            if let error { Section { Text(error).foregroundStyle(.red) } }
        }.navigationTitle(store.t(draft.id == 0 ? "Add reminder" : "Edit reminder")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement:.cancellationAction) { Button(store.t("Cancel")) { dismiss() } };ToolbarItem(placement:.confirmationAction) { Button(store.t("Save"),action:save).bold().accessibilityIdentifier("save-reminder") };ToolbarItemGroup(placement:.keyboard) { Spacer();Button(store.t("Done")) { field = nil } } }
    }
    private var weekdaySection: some View {
        Section(store.t("Days of the week")) {
            HStack { Button(store.t("Weekdays")) { draft.weekDays = 31 };Spacer();Button(store.t("Weekends")) { draft.weekDays = 96 } }
            WeekdayPicker(store:store,mask:$draft.weekDays)
        }
    }
    private var monthSection: some View {
        Section(store.t("Days of the month")) {
            LazyVGrid(columns:[GridItem(.adaptive(minimum:typeSize.isAccessibilitySize ? 90 : 44))]) {
                ForEach(1...31,id:\.self) { day in SelectionChip(title:String(day),selected:draft.monthDays.contains(day)) { if draft.monthDays.contains(day) { draft.monthDays.removeAll { $0 == day } } else { draft.monthDays.append(day) } } }
            }
        }
    }
    private var annualSection: some View {
        Section(store.t("Annual dates")) {
            ForEach($annual) { $value in DatePicker(store.t("Day and month"),selection:$value.date,displayedComponents:.date) }.onDelete { annual.remove(atOffsets:$0) }
            Button(store.t("+ Add date")) { annual.append(AnnualDraft(date:Date())) }
        }
    }
    private var cycleSection: some View {
        Section(store.t("ON/OFF cycle")) {
            HStack { Text(store.t("Days ON"));Spacer();TextField("21",text:$onDays).keyboardType(.numberPad).focused($field,equals:.on).multilineTextAlignment(.trailing).frame(minWidth:70).accessibilityIdentifier("cycle-on") }
            HStack { Text(store.t("Days OFF"));Spacer();TextField("7",text:$offDays).keyboardType(.numberPad).focused($field,equals:.off).multilineTextAlignment(.trailing).frame(minWidth:70).accessibilityIdentifier("cycle-off") }
            DatePicker(store.t("First ON day"),selection:$anchor,displayedComponents:.date)
        }
    }
    private func save() {
        do {
            var value = draft;value.times = times.map { let p = DayMath.calendar().dateComponents([.hour,.minute],from:$0.date);return String(format:"%02d:%02d",p.hour!,p.minute!) }
            value.yearDays = Array(Set(annual.map { let p = DayMath.calendar().dateComponents([.month,.day],from:$0.date);return String(format:"--%02d-%02d",p.month!,p.day!) })).sorted()
            if value.frequency == "cycle" { guard let on = Int(onDays),let off = Int(offDays) else { throw AppFailure("Use 1–3650 ON days and 0–3650 OFF days.") };value.cycleOnDays = on;value.cycleOffDays = off;value.cycleStart = DayMath.dayString(anchor) }
            try store.mutate { _ = try $0.saveReminder(value) }
            if value.enabled && store.data.settings.remindersEnabled { Task { await store.allowNotifications() } };dismiss()
        } catch { self.error = store.t((error as? AppFailure)?.key ?? "Could not save app data.") }
    }
}
struct WeekdayPicker: View {
    let store: MedicStore
    @Binding var mask: Int
    @ScaledMetric(relativeTo:.subheadline) private var cellWidth = 62.0
    private var symbols: [String] { let f = DateFormatter();f.locale = store.locale;let values = f.shortWeekdaySymbols!;return Array(values.dropFirst()) + [values[0]] }
    var body: some View { LazyVGrid(columns:[GridItem(.adaptive(minimum:cellWidth))]) { ForEach(0..<7,id:\.self) { day in SelectionChip(title:symbols[day],selected:mask & (1 << day) != 0) { mask ^= 1 << day } } } }
}
struct SelectionChip: View {
    let title: String,selected: Bool,action: ()->Void
    var body: some View { Button(action:action) { Text(title).font(.subheadline.weight(.medium)).frame(maxWidth:.infinity,minHeight:44).background(selected ? Theme.fill : Theme.red.opacity(0.08),in:.rect(cornerRadius:12)).foregroundStyle(selected ? .white : Theme.red) }.buttonStyle(.plain).accessibilityAddTraits(selected ? [.isSelected] : []) }
}
