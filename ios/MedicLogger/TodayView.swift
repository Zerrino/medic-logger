import SwiftUI

private enum TodayEntry: Identifiable {
    case title(String), dose(Dose)
    var id: String { switch self { case .title(let title):"title-" + title;case .dose(let dose):"dose-\(dose.id)" } }
}
struct TodayView: View {
    let store: MedicStore, show: (AppSheet)->Void
    @Environment(\.dynamicTypeSize) private var typeSize
    private var today: [Dose] { store.doses.filter { Calendar.current.isDateInToday($0.takenAt) } }
    private var active: [Dose] { store.doses.filter(\.active) }
    private var shortcuts: [Medication] { store.medicines.filter(\.favorite) }
    private var entries: [TodayEntry] {
        let finished = today.filter { !$0.active }
        var rows: [TodayEntry] = []
        if !active.isEmpty { rows.append(.title("Active doses"));rows += active.map(TodayEntry.dose) }
        if !finished.isEmpty { rows.append(.title("Taken today"));rows += finished.map(TodayEntry.dose) }
        return rows
    }
    var body: some View {
        ScrollView {
            LazyVStack(alignment:.leading,spacing:14) {
                TodayHeader(store:store,count:today.count)
                VStack(alignment:.leading,spacing:12) {
                    HStack { Text(store.t("Quick log")).font(.title3.bold());Spacer();Button(store.t("Add medicine"),systemImage:"plus") { show(.medicine(Medication())) }.labelStyle(.iconOnly).frame(minWidth:44,minHeight:44).accessibilityIdentifier("add-medicine") }
                    if shortcuts.isEmpty { Card { EmptyState(title:store.t("No medicines saved"),symbol:"pills");Button(store.t("Add a medicine")) { show(.medicine(Medication())) }.buttonStyle(.borderedProminent).frame(maxWidth:.infinity) } }
                    else {
                        LazyVGrid(columns:[GridItem(typeSize.isAccessibilitySize ? .flexible() : .adaptive(minimum:150),spacing:12)],spacing:12) {
                            ForEach(shortcuts) { medicine in QuickLogButton(store:store,medicine:medicine) }
                        }
                    }
                }
                // One identity space: moving a dose from active to finished must not reuse a stale lazy row.
                ForEach(entries) { entry in
                    switch entry {
                    case .title(let key): Text(store.t(key)).font(.headline).padding(.top,4)
                    case .dose(let dose): DoseCard(store:store,dose:dose,show:show)
                    }
                }
            }.padding(20).frame(maxWidth:760).frame(maxWidth:.infinity)
        }.background(Theme.background).navigationBarTitleDisplayMode(.inline)
    }
}
struct QuickLogButton: View {
    let store: MedicStore, medicine: Medication
    var body: some View {
        Button { store.pendingIntakeID = medicine.id } label: {
            VStack(alignment:.leading,spacing:8) {
                    HStack { MedicineIcon(color:medicine.color);Spacer();Image(systemName:"plus.circle.fill").font(.title2).foregroundStyle(AccessibleColor.ink(medicine.color)) }
                Text(medicine.label).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading)
                if !medicine.dose.isEmpty { Text(medicine.dose).font(.caption).foregroundStyle(.secondary).lineLimit(2) }
                if let remaining = store.data.remaining(medicine) { Text(store.t("Stock: %d",remaining)).font(.caption).foregroundStyle(.secondary) }
            }.padding(12).frame(maxWidth:.infinity,minHeight:100,alignment:.topLeading).background(Color(uiColor:.secondarySystemGroupedBackground),in:.rect(cornerRadius:22))
        }.buttonStyle(PressFeedbackStyle()).accessibilityLabel(store.t("Log dose: %s",medicine.name)).accessibilityIdentifier("quick-log-\(medicine.id)")
    }
}
struct DoseCard: View {
    let store: MedicStore
    private let doseID: Int64
    let day: Date
    let actions: Bool
    let show: (AppSheet)->Void
    init(store: MedicStore,dose: Dose,day: Date = Date(),actions: Bool = true,show: @escaping (AppSheet)->Void) {
        self.store = store;self.doseID = dose.id;self.day = day;self.actions = actions;self.show = show
    }
    var body: some View {
        if let dose = store.data.doses.first(where:{ $0.id == doseID }) {
            DoseCardContent(store:store,dose:dose,day:day,actions:actions,show:show)
        }
    }
}
private struct DoseCardContent: View {
    let store: MedicStore,dose: Dose,day: Date,actions: Bool,show: (AppSheet)->Void
    @Environment(\.dynamicTypeSize) private var typeSize
    private var actionLayout: AnyLayout { typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(spacing:8)) : AnyLayout(HStackLayout(spacing:8)) }
    private var headingLayout: AnyLayout { typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment:.leading,spacing:6)) : AnyLayout(HStackLayout(alignment:.top,spacing:8)) }
    var body: some View {
        Card(padding:12,spacing:9,radius:16) {
            Button { show(.dose(dose)) } label: {
                headingLayout {
                    if !typeSize.isAccessibilitySize {
                        Image(systemName:"pills.fill").foregroundStyle(AccessibleColor.ink(dose.color)).frame(width:28,height:28).background(Color(hex:dose.color).opacity(0.08),in:.rect(cornerRadius:9)).accessibilityHidden(true)
                    }
                    VStack(alignment:.leading,spacing:2) {
                        Text(dose.label).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading)
                        if !dose.dose.isEmpty { Text(dose.dose).font(.caption).foregroundStyle(.secondary) }
                    }
                    if !typeSize.isAccessibilitySize { Spacer(minLength:4) }
                    VStack(alignment:.trailing,spacing:3) {
                        if dose.monitorEffects { StatusBadge(dose:dose,store:store) }
                        if let score = dose.effectiveness { Text("\(score)/10").font(.caption.weight(.semibold)).foregroundStyle(.primary).accessibilityLabel(store.t("Effectiveness: %d/10",score)) }
                    }
                }.frame(minHeight:44).contentShape(.rect)
            }.buttonStyle(PressFeedbackStyle()).accessibilityLabel(store.t("Dose details") + ": " + dose.medicineName).accessibilityIdentifier("dose-details-\(dose.id)")
            DoseTimelineView(store:store,dose:dose,day:day)
            if actions && dose.active {
                actionLayout {
                    if dose.onsetAt == nil {
                        Button { store.perform { try store.mark(dose.id,onset:true) } } label: { Text(store.t("Active")).frame(maxWidth:.infinity,minHeight:30) }
                            .buttonStyle(.borderedProminent).tint(Color(hex:dose.color)).foregroundStyle(AccessibleColor.onFill(dose.color)).accessibilityIdentifier("dose-active-\(dose.id)")
                    }
                    Button { store.perform { try store.mark(dose.id,onset:false) } } label: { Text(store.t(dose.onsetAt == nil ? "No effect" : "Over")).frame(maxWidth:.infinity,minHeight:30) }
                        .buttonStyle(.bordered).tint(AccessibleColor.ink(dose.color)).accessibilityIdentifier("dose-over-\(dose.id)")
                }.controlSize(.regular)
            }
            if !dose.notes.isEmpty { Text(dose.notes).font(.caption).foregroundStyle(.secondary).lineLimit(typeSize.isAccessibilitySize ? nil : 2) }
        }
    }
}
struct TodayHeader: View {
    let store: MedicStore,count: Int
    @Environment(\.dynamicTypeSize) private var typeSize
    private var layout: AnyLayout { typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment:.leading,spacing:16)) : AnyLayout(HStackLayout(spacing:16)) }
    var body: some View {
        layout {
            VStack(alignment:.leading,spacing:6) { Text(Date(),format:.dateTime.weekday(.wide).day().month(.wide)).font(.subheadline).foregroundStyle(.secondary);Text(store.t("Today")).font(.largeTitle.bold()) }
            if !typeSize.isAccessibilitySize { Spacer() }
            VStack(spacing:2) { Text(String(count)).font(.title.bold()).foregroundStyle(Theme.red);Text(store.t("Doses today")).font(.caption).foregroundStyle(.secondary) }.padding(14).background(Theme.red.opacity(0.07),in:.rect(cornerRadius:18))
        }
    }
}
