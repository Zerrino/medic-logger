import SwiftUI

struct CalendarCell: Identifiable { let date: Date;var id: String { DayMath.dayString(date) } }
struct CalendarView: View {
    let store: MedicStore, show: (AppSheet)->Void
    @State private var month = Date()
    @State private var selected = Date()
    @Environment(\.dynamicTypeSize) private var typeSize
    private var cal: Calendar { DayMath.calendar() }
    private var days: [CalendarCell] {
        let start = cal.date(from:cal.dateComponents([.year,.month],from:month))!,offset = ((cal.component(.weekday,from:start) + 5) % 7)
        let count = cal.range(of:.day,in:.month,for:start)!.count,cells = ((offset + count + 6)/7)*7
        return (0..<cells).map { CalendarCell(date:cal.date(byAdding:.day,value:$0-offset,to:start)!) }
    }
    private var allEvents: [LogEvent] { store.data.doses.flatMap { d in var e = [LogEvent(dose:d,type:"Taken",date:d.takenAt)];if let t = d.onsetAt { e.append(LogEvent(dose:d,type:"Effects started",date:t)) };if let t = d.endedAt { e.append(LogEvent(dose:d,type:d.onsetAt == nil ? "Closed" : "Effects ended",date:t)) };return e } }
    private var eventsByDay: [String:[LogEvent]] { Dictionary(grouping:allEvents,by:{ DayMath.dayString($0.date) }) }
    private var dayEvents: [LogEvent] { (eventsByDay[DayMath.dayString(selected)] ?? []).sorted { $0.date < $1.date } }
    private var dayDoses: [Dose] { CalendarDoses.on(selected,from:store.data.doses) }
    private var monthCount: Int { store.data.doses.filter { cal.isDate($0.takenAt,equalTo:month,toGranularity:.month) }.count }
    private var dayHeadingLayout: AnyLayout { typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment:.leading,spacing:8)) : AnyLayout(HStackLayout(spacing:8)) }
    var body: some View {
        ScrollView {
            LazyVStack(alignment:.leading,spacing:12) {
                Card(padding:14,spacing:8,radius:18) {
                    HStack {
                        Button(store.t("Previous month"),systemImage:"chevron.left") { shift(-1) }.labelStyle(.iconOnly).frame(minWidth:44,minHeight:44)
                        Spacer();VStack(spacing:4) { Text(month,format:.dateTime.month(.wide)).font(.headline).lineLimit(1).minimumScaleFactor(0.5);Text(month,format:.dateTime.year()).font(.subheadline).monospacedDigit() };Spacer()
                        Button(store.t("Next month"),systemImage:"chevron.right") { shift(1) }.labelStyle(.iconOnly).frame(minWidth:44,minHeight:44)
                    }
                    Text(store.t("%d doses this month",monthCount)).font(.subheadline).foregroundStyle(.secondary)
                    if typeSize.isAccessibilitySize { VStack(alignment:.leading,spacing:12) { Text(store.t("Choose dates")).font(.headline).fixedSize(horizontal:false,vertical:true);DatePicker(store.t("Choose dates"),selection:$selected,displayedComponents:.date).labelsHidden().accessibilityIdentifier("calendar-date-select") } }
                    else { CalendarGrid(store:store,days:days,month:month,events:eventsByDay,selected:$selected) }
                }
                dayHeadingLayout { Text(selected,format:.dateTime.weekday(.wide).day().month(.wide)).font(.headline);if !typeSize.isAccessibilitySize { Spacer() };Button(store.t("Today")) { selected = Date();month = Date() }.frame(minHeight:44) }
                if dayEvents.isEmpty { EmptyState(title:store.t("No records on this day"),symbol:"calendar") }
                ForEach(dayDoses) { dose in
                    DoseCard(store:store,dose:dose,day:selected,actions:false,show:show).accessibilityElement(children:.contain)
                }
                if !store.data.doses.isEmpty {
                    Text(store.t("Medicines")).font(.title3.bold())
                    ForEach(store.data.medicines.filter { m in store.data.doses.contains { $0.medicineID == m.id } }) { medicine in MedicineStatistics(store:store,medicine:medicine) }
                }
            }.padding(16).frame(maxWidth:760).frame(maxWidth:.infinity)
        }.background(Theme.background).onChange(of:selected) { month = selected }
    }
    private func shift(_ value: Int) { month = cal.date(byAdding:.month,value:value,to:month)! }
}
struct CalendarGrid: View {
    let store: MedicStore,days: [CalendarCell],month: Date,events: [String:[LogEvent]]
    @Binding var selected: Date
    private var symbols: [String] { let f = DateFormatter();f.locale = store.locale;let days = f.shortWeekdaySymbols!;return Array(days.dropFirst()) + [days[0]] }
    var body: some View {
        VStack(spacing:4) {
            HStack { ForEach(0..<7,id:\.self) { i in Text(symbols[i]).font(.caption.weight(.semibold)).foregroundStyle(.secondary).frame(maxWidth:.infinity) } }
            LazyVGrid(columns:Array(repeating:GridItem(.flexible(),spacing:2),count:7),spacing:2) {
                ForEach(days) { cell in CalendarDayButton(store:store,date:cell.date,selected:DayMath.dayString(cell.date) == DayMath.dayString(selected),currentMonth:Calendar.current.isDate(cell.date,equalTo:month,toGranularity:.month),events:events[cell.id] ?? []) { selected = cell.date } }
            }
        }
    }
}
struct CalendarDayButton: View {
    let store: MedicStore,date: Date,selected: Bool,currentMonth: Bool,events: [LogEvent],action: ()->Void
    private var colors: [String] { Array(Set(events.map { $0.dose.color })).sorted().prefix(3).map { $0 } }
    var body: some View {
        Button(action:action) {
            VStack(spacing:4) {
                Text(String(Calendar.current.component(.day,from:date))).font(.subheadline.weight(selected ? .bold : .regular)).foregroundStyle(selected ? .white : currentMonth ? Color.primary : Color.secondary.opacity(0.5))
                HStack(spacing:2) { ForEach(colors,id:\.self) { color in Circle().fill(selected ? .white : AccessibleColor.ink(color)).frame(width:4,height:4) } }.frame(height:4)
            }.frame(maxWidth:.infinity,minHeight:44).background(selected ? Theme.fill : .clear,in:.rect(cornerRadius:12))
        }.buttonStyle(.plain).accessibilityLabel(date.formatted(.dateTime.day().month().year().locale(store.locale)) + ", " + store.t("%d events",events.count)).accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}
struct MedicineStatistics: View {
    let store: MedicStore,medicine: Medication
    @Environment(\.dynamicTypeSize) private var typeSize
    private var layout: AnyLayout { typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment:.leading,spacing:10)) : AnyLayout(HStackLayout(spacing:12)) }
    private var doses: [Dose] { store.doses.filter { $0.medicineID == medicine.id } }
    var body: some View {
        VStack(alignment:.leading,spacing:4) {
            layout { Text(medicine.label).font(.subheadline.weight(.semibold));if !typeSize.isAccessibilitySize { Spacer() };Text(store.t("%d doses",doses.count)).font(.caption).foregroundStyle(.secondary) }
            if let last = doses.first { Text(store.t("Last dose %s ago",store.elapsed(last.takenAt))).font(.subheadline).foregroundStyle(.secondary) }
            if let first = doses.last { Text(store.t("First recorded on %s",first.takenAt.formatted(.dateTime.day().month().year().locale(store.locale)))).font(.caption).foregroundStyle(.secondary) }
            Divider().padding(.top,4)
        }.padding(.horizontal,4).padding(.vertical,3)
    }
}
