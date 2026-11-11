import SwiftUI
import UniformTypeIdentifiers

struct ExportView: View {
    let store: MedicStore
    @State private var from = Calendar.current.date(byAdding:.day,value:-6,to:Date())!
    @State private var to = Date()
    @State private var allMedicines = true
    @State private var selected: Set<Int64> = []
    @State private var choosing = false
    @State private var format: ExportFormat = .json
    @State private var zipped = false
    @State private var preparing = false
    @State private var showingExporter = false
    @State private var document: ExportDocument?
    @State private var filename = "medic-logger.json"
    @State private var contentType = UTType.json
    @State private var feedback: String?
    private var selection: ExportSelection? { try? ExportSelection(data:store.data,from:from,to:to,medicines:allMedicines ? nil : selected) }
    var body: some View {
        Form {
            Section {
                DatePicker(store.t("From"),selection:$from,displayedComponents:.date)
                DatePicker(store.t("To"),selection:$to,displayedComponents:.date)
                HStack { Button(store.t("7 days")) { range(7) };Spacer();Button(store.t("30 days")) { range(30) };Spacer();Button(store.t("All time")) { from = store.data.doses.map(\.takenAt).min() ?? Date();to = Date() } }.font(.subheadline)
            } header: { Text(store.t("Date range")) } footer: { if Calendar.current.startOfDay(for:to) < Calendar.current.startOfDay(for:from) { Text(store.t("End date must be on or after start date.")).foregroundStyle(.red) } }
            Section(store.t("Medicines")) {
                Toggle(store.t("All medicines"),isOn:$allMedicines)
                if !allMedicines { Button { choosing = true } label: { HStack { Text(store.t("Choose medicines"));Spacer();Text(String(selected.count)).foregroundStyle(.secondary);Image(systemName:"chevron.right").font(.caption) } }.accessibilityIdentifier("export-choose-medicines") }
            }
            Section(store.t("Format")) {
                Picker(store.t("Format"),selection:$format) { ForEach(ExportFormat.allCases) { item in Text(store.t(item.label)).tag(item) } }.accessibilityIdentifier("export-format")
                Toggle(store.t("ZIP archive"),isOn:format == .all ? .constant(true) : $zipped).disabled(format == .all)
            }
            Section {
                Label(store.t("%d events selected",selection?.events.count ?? 0),systemImage:"list.bullet.rectangle").font(.subheadline).foregroundStyle(.secondary)
                Button(action:prepare) { HStack { Spacer();if preparing { ProgressView().padding(.trailing,6) };Label(store.t(preparing ? "Preparing…" : "Save export"),systemImage:"square.and.arrow.up");Spacer() }.frame(minHeight:36) }.buttonStyle(.borderedProminent).tint(Theme.fill).foregroundStyle(.white).disabled(preparing || (selection?.events.isEmpty ?? true)).listRowBackground(Color.clear).accessibilityIdentifier("save-export")
            } footer: { Text(store.t("Encrypted storage. Exports are not encrypted.")) }
            if let feedback { Section { Text(feedback).font(.subheadline).foregroundStyle(.secondary).accessibilityIdentifier("export-feedback") } }
        }
        .sheet(isPresented:$choosing) { NavigationStack { MedicineSelectionView(store:store,selected:$selected) } }
        .fileExporter(isPresented:$showingExporter,document:document,contentType:contentType,defaultFilename:filename) { result in
            switch result { case .success:feedback = store.t("Export saved");case .failure:feedback = store.t("Could not save the file. Please try exporting again.") };document = nil
        }
    }
    private func range(_ days: Int) { to = Date();from = Calendar.current.date(byAdding:.day,value:1-days,to:to)! }
    private func prepare() {
        do {
            let data = try ExportSelection(data:store.data,from:from,to:to,medicines:allMedicines ? nil : selected)
            let chosen = format, archive = zipped || chosen == .all
            preparing = true;feedback = nil
            Task {
                do {
                    let bytes = try await Task.detached(priority:.userInitiated) { try ExportWriter.write(data,format:chosen,zipped:archive) }.value
                    let ext = archive ? "zip" : chosen.ext
                    document = ExportDocument(data:bytes);filename = "Medic-Logger-\(DayMath.dayString(from))-\(DayMath.dayString(to)).\(ext)";contentType = UTType(filenameExtension:ext) ?? .data;preparing = false;showingExporter = true
                } catch { preparing = false;feedback = store.t((error as? AppFailure)?.key ?? "Could not save the file. Please try exporting again.") }
            }
        } catch { feedback = store.t((error as? AppFailure)?.key ?? "Could not save the file. Please try exporting again.") }
    }
}
struct MedicineSelectionView: View {
    let store: MedicStore
    @Binding var selected: Set<Int64>
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        List {
            ForEach(store.data.medicines) { medicine in
                Button { if selected.contains(medicine.id) { selected.remove(medicine.id) } else { selected.insert(medicine.id) } } label: {
                    HStack { MedicineIcon(color:medicine.color);VStack(alignment:.leading) { Text(medicine.name).foregroundStyle(.primary);if medicine.archived { Text(store.t("Archived")).font(.caption).foregroundStyle(.secondary) } };Spacer();Image(systemName:selected.contains(medicine.id) ? "checkmark.circle.fill" : "circle").foregroundStyle(selected.contains(medicine.id) ? Theme.red : .secondary) }
                }.accessibilityAddTraits(selected.contains(medicine.id) ? [.isSelected] : [])
            }
        }.navigationTitle(store.t("Choose medicines")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement:.confirmationAction) { Button(store.t("Apply")) { dismiss() } } }
    }
}
