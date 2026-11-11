import SwiftUI

struct DoseTimelineView: View {
    let store: MedicStore,dose: Dose,day: Date
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        if !dose.monitorEffects {
            HStack(spacing:6) { TimelineDot(color:dose.color,state:.recorded);Text(store.t("Taken")).font(.caption).foregroundStyle(.secondary);Spacer();Text(DayMath.calendar().isDate(dose.takenAt,inSameDayAs:day) ? store.timeText(dose.takenAt) : store.dateText(dose.takenAt)).font(.caption.monospacedDigit()) }.accessibilityElement(children:.combine).accessibilityIdentifier("timeline-\(dose.id)-taken")
        } else if typeSize.isAccessibilitySize {
            VStack(alignment:.leading,spacing:0) {
                ForEach(dose.timeline) { step in TimelineVerticalStep(store:store,dose:dose,step:step,day:day,last:step.kind == dose.timeline.last?.kind) }
            }
        } else {
            HStack(alignment:.top,spacing:0) {
                ForEach(dose.timeline) { step in
                    TimelineHorizontalStep(store:store,dose:dose,step:step,day:day,first:step.kind == .taken,last:step.kind == dose.timeline.last?.kind)
                }
            }
        }
    }
}
private struct TimelineHorizontalStep: View {
    let store: MedicStore,dose: Dose,step: DoseTimelineStep,day: Date,first: Bool,last: Bool
    var body: some View {
        VStack(spacing:4) {
            HStack(spacing:0) {
                Rectangle().fill(AccessibleColor.ink(dose.color).opacity(first ? 0 : 0.35)).frame(height:1)
                TimelineDot(color:dose.color,state:step.state)
                Rectangle().fill(AccessibleColor.ink(dose.color).opacity(last ? 0 : 0.35)).frame(height:1)
            }.frame(height:12)
            Text(store.t(step.label)).font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
            Text(stamp).font(.caption.weight(.medium)).monospacedDigit().foregroundStyle(step.date == nil ? .secondary : .primary).multilineTextAlignment(.center)
        }.frame(maxWidth:.infinity).accessibilityElement(children:.combine).accessibilityIdentifier("timeline-\(dose.id)-\(step.kind.rawValue)")
    }
    private var stamp: String { guard let date = step.date else { return "—" };return DayMath.calendar().isDate(date,inSameDayAs:day) ? store.timeText(date) : date.formatted(.dateTime.day().month(.abbreviated).hour().minute().locale(store.locale)) }
}
private struct TimelineVerticalStep: View {
    let store: MedicStore,dose: Dose,step: DoseTimelineStep,day: Date,last: Bool
    var body: some View {
        HStack(alignment:.top,spacing:12) {
            VStack(spacing:0) { TimelineDot(color:dose.color,state:step.state).padding(.top,6);if !last { Rectangle().fill(AccessibleColor.ink(dose.color).opacity(0.35)).frame(width:1).frame(maxHeight:.infinity) } }.frame(width:12)
            VStack(alignment:.leading,spacing:3) { Text(store.t(step.label)).font(.subheadline);Text(stamp).font(.subheadline.monospacedDigit()).foregroundStyle(.secondary) }.padding(.bottom,last ? 0 : 12)
        }.fixedSize(horizontal:false,vertical:true).accessibilityElement(children:.combine).accessibilityIdentifier("timeline-\(dose.id)-\(step.kind.rawValue)")
    }
    private var stamp: String { guard let date = step.date else { return "—" };return DayMath.calendar().isDate(date,inSameDayAs:day) ? store.timeText(date) : store.dateText(date) }
}
private struct TimelineDot: View {
    let color: String,state: DoseTimelineStep.State
    var body: some View {
        Circle().fill(state == .recorded ? AccessibleColor.ink(color) : Color(uiColor:.secondarySystemGroupedBackground))
            .overlay { Circle().strokeBorder(AccessibleColor.ink(color).opacity(state == .pending ? 0.35 : 1),lineWidth:1.5) }
            .overlay { if state == .skipped { Image(systemName:"minus").font(.system(size:6,weight:.bold)).foregroundStyle(AccessibleColor.ink(color)) } }
            .frame(width:9,height:9).padding(.horizontal,3).accessibilityHidden(true)
    }
}
