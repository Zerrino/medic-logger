import SwiftUI

struct MedicinesView: View {
    let store: MedicStore,show: (AppSheet)->Void
    @State private var archived = false
    @Environment(\.dynamicTypeSize) private var typeSize
    private var stockLayout: AnyLayout { typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment:.leading,spacing:8)) : AnyLayout(HStackLayout(spacing:10)) }
    private var medicines: [Medication] { archived ? store.data.medicines.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending } : store.medicines }
    var body: some View {
        List {
            Section { Button(store.t("Add a medicine"),systemImage:"plus") { show(.medicine(Medication())) }.accessibilityIdentifier("add-medicine-list") }
            Section {
                if medicines.isEmpty { EmptyState(title:store.t("No medicines saved"),symbol:"cross.case") }
                ForEach(medicines) { medicine in
                    VStack(alignment:.leading,spacing:12) {
                        Button { show(.medicine(medicine)) } label: {
                            HStack(spacing:12) { MedicineIcon(color:medicine.color);VStack(alignment:.leading,spacing:5) { Text(medicine.name).font(.headline).foregroundStyle(.primary);if !medicine.alias.isEmpty { Text(medicine.alias).font(.subheadline).foregroundStyle(.secondary) };if !medicine.dose.isEmpty { Text(medicine.dose).font(.caption).foregroundStyle(.secondary) } };Spacer();Image(systemName:"chevron.right").font(.caption).foregroundStyle(.secondary) }
                        }.buttonStyle(.plain)
                        if medicine.archived { Text(store.t("Archived")).font(.caption).foregroundStyle(.secondary) }
                        else {
                            stockLayout {
                                if let remaining = store.data.remaining(medicine) { Text(store.t("Stock: %d",remaining)).font(.subheadline).foregroundStyle(.secondary) }
                                if !typeSize.isAccessibilitySize { Spacer() }
                                Button { show(.refill(medicine)) } label: { Text(store.t("Refill")).frame(minHeight:32) }.buttonStyle(.bordered)
                                Button { store.pendingIntakeID = medicine.id } label: { Text(store.t("Log dose")).frame(minHeight:32) }.buttonStyle(.borderedProminent).tint(Color(hex:medicine.color)).foregroundStyle(AccessibleColor.onFill(medicine.color))
                            }
                            NavigationLink { RemindersView(store:store,medicine:medicine) } label: { Label(store.t("Reminders"),systemImage:"bell.badge").font(.subheadline) }
                        }
                    }.padding(.vertical,8)
                }
            }
            Section { Toggle(store.t("Show archived"),isOn:$archived) }
        }
    }
}
struct MedicineEditor: View {
    let store: MedicStore
    @State private var draft: Medication
    @State private var trackStock = false
    @State private var quantity = ""
    @State private var customColor = false
    @State private var error: String?
    @State private var deleteConfirm = false
    private enum Field: Hashable { case name,alias,dose,quantity }
    @FocusState private var field: Field?
    @Environment(\.dismiss) private var dismiss
    init(store: MedicStore,medicine: Medication) { self.store = store;_draft = State(initialValue:medicine) /* Intentionally seed a cancellable draft once. */ }
    private var colors: [String] { var values = Theme.palette + store.data.settings.customColors;if !values.contains(draft.color) { values.append(draft.color) };return values }
    var body: some View {
        Form {
            Section {
                TextField(store.t("Medicine name *"),text:$draft.name).textContentType(.none).focused($field,equals:.name).submitLabel(.next).onSubmit { field = .alias }.accessibilityIdentifier("medicine-name")
                TextField(store.t("Short name"),text:$draft.alias).focused($field,equals:.alias).submitLabel(.next).onSubmit { field = .dose }.accessibilityIdentifier("medicine-alias")
                TextField(store.t("Dose label (optional)"),text:$draft.dose).focused($field,equals:.dose).submitLabel(.done).onSubmit { field = nil }.accessibilityIdentifier("medicine-dose")
            }
            Section(store.t("Color")) {
                ScrollView(.horizontal) {
                    HStack(spacing:12) {
                        ForEach(colors,id:\.self) { hex in Button { draft.color = hex } label: { Circle().fill(Color(hex:hex)).frame(width:42,height:42).overlay { if draft.color == hex { Image(systemName:"checkmark").foregroundStyle(AccessibleColor.onFill(hex)).font(.headline) } }.padding(2) }.buttonStyle(PressFeedbackStyle()).accessibilityLabel("#" + hex).accessibilityAddTraits(draft.color == hex ? [.isSelected] : []) }
                        Button(store.t("Create color"),systemImage:"plus") { customColor = true }.labelStyle(.iconOnly).font(.title3).frame(width:44,height:44).background(Theme.red.opacity(0.08),in:.circle).accessibilityIdentifier("custom-color")
                    }.padding(.vertical,4)
                }.scrollIndicators(.hidden)
            }
            Section {
                Toggle(store.t("Show in Quick log"),isOn:$draft.favorite)
                Toggle(store.t("Monitor effects"),isOn:$draft.monitorEffects).accessibilityIdentifier("monitor-effects")
            } footer: { Text(store.t("Applies to new doses.")) }
            if draft.id == 0 {
                Section {
                    Toggle(store.t("Track stock"),isOn:$trackStock)
                    if trackStock { TextField(store.t("Pack quantity (optional)"),text:$quantity).keyboardType(.numberPad).focused($field,equals:.quantity).accessibilityIdentifier("medicine-quantity") }
                }
            } else {
                Section {
                    if let current = store.data.medicines.first(where:{ $0.id == draft.id }),let remaining = store.data.remaining(current) { Text(store.t("Current pack: %d",remaining)) }
                    NavigationLink { RemindersView(store:store,medicine:draft) } label: { Label(store.t("Reminders"),systemImage:"bell") }
                    Toggle(store.t("Archived"),isOn:$draft.archived)
                } footer: { if draft.archived { Text(store.t("Your dose history will be kept.")) } }
                Section { Button(store.t("Delete medicine"),role:.destructive) { deleteConfirm = true } }
            }
            if let error { Section { Text(error).foregroundStyle(.red) } }
        }.navigationTitle(store.t(draft.id == 0 ? "Add a medicine" : "Edit medicine")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement:.cancellationAction) { Button(store.t("Cancel")) { dismiss() } };ToolbarItem(placement:.confirmationAction) { Button(store.t("Save"),action:save).bold().accessibilityIdentifier("save-medicine") };ToolbarItemGroup(placement:.keyboard) { Spacer();Button(store.t("Done")) { field = nil } } }
            .sheet(isPresented:$customColor) { NavigationStack { CustomColorView(store:store,initial:draft.color) { draft.color = $0 } } }
            .confirmationDialog(store.t("Delete %s?",draft.name),isPresented:$deleteConfirm,titleVisibility:.visible) {
                Button(store.t("Delete medicine"),role:.destructive) { do { try store.mutate { try $0.deleteMedicine(draft.id) };dismiss() } catch { self.error = store.t((error as? AppFailure)?.key ?? "Could not save app data.") } }
            } message: { Text(store.t("Only medicines without recorded doses can be permanently deleted.")) }
    }
    private func save() {
        do {
            var stock: Int?
            if draft.id == 0 && trackStock { guard let parsed = Int(quantity) else { throw AppFailure("Enter a quantity from 0 to 1,000,000,000.") };stock = parsed }
            try store.mutate { data in _ = try data.saveMedicine(draft,quantity:stock);if !Theme.palette.contains(draft.color) && !data.settings.customColors.contains(draft.color) { data.settings.customColors.append(draft.color) } };dismiss()
        } catch { self.error = store.t((error as? AppFailure)?.key ?? "Could not save app data.") }
    }
}
struct CustomColorView: View {
    let store: MedicStore,apply: (String)->Void
    @State private var color: Color
    @State private var hex: String
    @State private var error = false
    @Environment(\.dismiss) private var dismiss
    init(store: MedicStore,initial: String,apply: @escaping (String)->Void) { self.store = store;self.apply = apply;_color = State(initialValue:Color(hex:initial));_hex = State(initialValue:initial) }
    var body: some View {
        Form {
            ColorPicker(store.t("Color"),selection:$color,supportsOpacity:false).onChange(of:color) { hex = color.hex }
            HStack { Text("#");TextField(store.t("Hex color"),text:$hex).textInputAutocapitalization(.characters).autocorrectionDisabled().accessibilityIdentifier("color-hex") }
            RoundedRectangle(cornerRadius:16).fill(Color(hex:hex)).frame(height:80).accessibilityHidden(true)
            if error { Text(store.t("Enter a color as #RRGGBB.")).foregroundStyle(.red) }
        }.navigationTitle(store.t("Create color")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement:.cancellationAction) { Button(store.t("Cancel")) { dismiss() } };ToolbarItem(placement:.confirmationAction) { Button(store.t("Use color")) { let value = hex.trimmingCharacters(in:CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn:"#"))).uppercased();guard value.count == 6,UInt32(value,radix:16) != nil else { error = true;return };apply(value);dismiss() }.accessibilityIdentifier("use-color") } }
    }
}
struct RefillView: View {
    let store: MedicStore,medicine: Medication
    @State private var quantity = ""
    @State private var error: String?
    @FocusState private var focused: Bool
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        Form {
            Section { Text(medicine.name).font(.headline);if let remaining = store.data.remaining(medicine) { Text(store.t("Current pack: %d",remaining)).foregroundStyle(.secondary) } }
            Section { TextField(store.t("New pack quantity"),text:$quantity).keyboardType(.numberPad).focused($focused).accessibilityIdentifier("refill-quantity") } footer: { Text(store.t("Starts a new pack. Older doses do not change this pack.")) }
            if let error { Text(error).foregroundStyle(.red) }
        }.navigationTitle(store.t("Refill")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement:.cancellationAction) { Button(store.t("Cancel")) { dismiss() } };ToolbarItem(placement:.confirmationAction) { Button(store.t("Save")) { do { guard let count = Int(quantity) else { throw AppFailure("Enter a quantity from 0 to 1,000,000,000.") };try store.mutate { try $0.refill(medicine.id,quantity:count) };dismiss() } catch { self.error = store.t((error as? AppFailure)?.key ?? "Could not save app data.") } } };ToolbarItemGroup(placement:.keyboard) { Spacer();Button(store.t("Done")) { focused = false } } }
    }
}
