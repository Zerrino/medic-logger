import SwiftUI

struct JournalView: View {
    let store: MedicStore, show: (AppSheet)->Void
    @State private var search = ""
    @State private var medicineID: Int64?
    @State private var visible = 50
    private var matches: [Dose] { store.doses.filter { dose in (medicineID == nil || dose.medicineID == medicineID) && (search.isEmpty || [dose.medicineName,dose.alias,dose.notes].contains { $0.localizedCaseInsensitiveContains(search) }) } }
    var body: some View {
        ScrollView {
            LazyVStack(alignment:.leading,spacing:14) {
                MedicineFilter(store:store,selection:$medicineID)
                if matches.isEmpty { EmptyState(title:store.t("No doses found"),symbol:"list.bullet.rectangle") }
                ForEach(Array(matches.prefix(visible))) { DoseCard(store:store,dose:$0,show:show) }
                if matches.count > visible { Button(store.t("Show more (%d)",matches.count-visible)) { visible += 50 }.frame(maxWidth:.infinity,minHeight:44) }
            }.padding(20).frame(maxWidth:760).frame(maxWidth:.infinity)
        }.background(Theme.background).searchable(text:$search,prompt:store.t("Search medicines or notes"))
            .onChange(of:search) { visible = 50 }.onChange(of:medicineID) { visible = 50 }
    }
}
struct MedicineFilter: View {
    let store: MedicStore
    @Binding var selection: Int64?
    private var label: String { store.data.medicines.first { $0.id == selection }?.label ?? store.t("All medicines") }
    var body: some View {
        Menu {
            Button(store.t("All medicines")) { selection = nil }
            ForEach(store.data.medicines) { medicine in Button(medicine.label) { selection = medicine.id } }
        } label: {
            HStack { Text(label).font(.body).fixedSize(horizontal:false,vertical:true);Spacer(minLength:12);Image(systemName:"line.3.horizontal.decrease").font(.body) }.padding(.vertical,8).frame(minHeight:44)
        }.accessibilityLabel(store.t("Filter medicines") + ": " + label)
    }
}

struct DoseEditor: View {
    let store: MedicStore
    @State private var draft: Dose
    @State private var error: String?
    @State private var deleting = false
    @Environment(\.dismiss) private var dismiss
    init(store: MedicStore,dose: Dose) { self.store = store;_draft = State(initialValue:dose) /* A one-time, cancellable edit draft. */ }
    var body: some View {
        Form {
            Section { HStack { MedicineIcon(color:draft.color);VStack(alignment:.leading) { Text(draft.medicineName).font(.headline);Text(draft.dose).font(.subheadline).foregroundStyle(.secondary) } };StatusBadge(dose:draft,store:store) }
            Section {
                DatePicker(store.t("Dose taken"),selection:$draft.takenAt,in:...Date())
                if draft.monitorEffects {
                    Toggle(store.t("Effects started"),isOn:Binding(get:{ draft.onsetAt != nil },set:{ draft.onsetAt = $0 ? max(draft.takenAt,min(draft.endedAt ?? Date(),Date())) : nil }))
                    if draft.onsetAt != nil { DatePicker(store.t("Set onset time"),selection:Binding(get:{ draft.onsetAt ?? draft.takenAt },set:{ draft.onsetAt = $0 }),in:draft.takenAt...Date()) }
                    Toggle(store.t("Dose closed / effects ended"),isOn:Binding(get:{ draft.endedAt != nil },set:{ draft.endedAt = $0 ? Date() : nil;if !$0 { draft.effectiveness = nil } }))
                    if draft.endedAt != nil { DatePicker(store.t("Set end time"),selection:Binding(get:{ draft.endedAt ?? Date() },set:{ draft.endedAt = $0 }),in:(draft.onsetAt ?? draft.takenAt)...Date()) }
                }
            }
            if draft.monitorEffects && draft.endedAt != nil {
                Section(store.t("Effectiveness (optional)")) { RatingPicker(store:store,selection:$draft.effectiveness);if draft.effectiveness != nil { Button(store.t("Clear rating")) { draft.effectiveness = nil } } }
            }
            Section(store.t("Notes")) { TextField(store.t("Notes (optional)"),text:$draft.notes,axis:.vertical).lineLimit(3...10).accessibilityIdentifier("dose-notes") }
            if let error { Section { Text(error).foregroundStyle(.red) } }
            Section { Button(store.t("Delete dose"),role:.destructive) { deleting = true } }
        }.navigationTitle(store.t("Dose details")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement:.cancellationAction) { Button(store.t("Cancel")) { dismiss() } };ToolbarItem(placement:.confirmationAction) { Button(store.t("Save"),action:save).bold() } }
            .confirmationDialog(store.t("Delete this dose?"),isPresented:$deleting,titleVisibility:.visible) { Button(store.t("Delete dose"),role:.destructive) { do { try store.mutate { $0.deleteDose(draft.id) };dismiss() } catch { self.error = store.t((error as? AppFailure)?.key ?? "Could not save app data.") } } } message: { Text(store.t("This removes the dose and its events.")) }
    }
    private func save() { do { try store.mutate { try $0.saveDose(draft) };dismiss() } catch { self.error = store.t((error as? AppFailure)?.key ?? "Could not save app data.") } }
}
struct RatingPicker: View {
    let store: MedicStore
    @Binding var selection: Int?
    @ScaledMetric(relativeTo:.headline) private var cellWidth = 44.0
    var body: some View {
        VStack(alignment:.leading,spacing:14) {
            LazyVGrid(columns:[GridItem(.adaptive(minimum:cellWidth),spacing:8)],spacing:8) {
                ForEach(0...10,id:\.self) { value in Button { selection = value } label: { Text(String(value)).font(.headline).frame(maxWidth:.infinity,minHeight:44).background(selection == value ? Theme.fill : Theme.red.opacity(0.08),in:.rect(cornerRadius:12)).foregroundStyle(selection == value ? .white : Theme.red) }.buttonStyle(.plain).accessibilityLabel("\(value)/10").accessibilityAddTraits(selection == value ? [.isSelected] : []).accessibilityIdentifier("rating-\(value)") }
            }
            Text(store.t("0: Not effective · 10: Very effective")).font(.caption).foregroundStyle(.secondary)
        }.padding(.vertical,8)
    }
}
struct RatingView: View {
    let store: MedicStore,dose: Dose
    @State private var score: Int?
    @State private var error: String?
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        Form {
            Section { Text(dose.medicineName).font(.headline);Text(store.dateText(dose.takenAt)).font(.subheadline).foregroundStyle(.secondary) }
            Section(store.t("Effectiveness (optional)")) { RatingPicker(store:store,selection:$score) }
            if let error { Text(error).foregroundStyle(.red) }
        }.navigationTitle(store.t("Effectiveness")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement:.cancellationAction) { Button(store.t("Skip")) { dismiss() } };ToolbarItem(placement:.confirmationAction) { Button(store.t("Save rating")) { do { try store.mutate { try $0.rate(dose.id,value:score) };dismiss() } catch { self.error = store.t((error as? AppFailure)?.key ?? "Could not save app data.") } }.disabled(score == nil) } }
    }
}
