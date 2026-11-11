import WidgetKit
import SwiftUI
import ActivityKit

@main struct MedicLoggerWidgets: WidgetBundle { var body: some Widget { DoseStatusWidget() } }
struct DoseStatusWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for:DoseActivity.self) { context in
            VStack(alignment:.leading,spacing:10) {
                HStack { Image(systemName:"pills").foregroundStyle(tint(context.state.color));Text(context.state.name).bold().privacySensitive();Spacer();Text(context.state.reference,style:.timer).monospacedDigit() }
                Text(context.state.phase).font(.subheadline)
                HStack {
                    if context.state.waiting { Link(context.state.activeLabel,destination:URL(string:"mediclogger://dose/\(context.attributes.sessionID)/active")!) }
                    Link(context.state.overLabel,destination:URL(string:"mediclogger://dose/\(context.attributes.sessionID)/over")!)
                }.buttonStyle(.bordered).tint(tint(context.state.color))
            }.padding().activityBackgroundTint(Color(uiColor:.secondarySystemGroupedBackground)).activitySystemActionForegroundColor(tint(context.state.color))
                .widgetURL(URL(string:"mediclogger://dose/\(context.attributes.sessionID)"))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) { Label(context.state.name,systemImage:"pills").privacySensitive() }
                DynamicIslandExpandedRegion(.trailing) { Text(context.state.reference,style:.timer).monospacedDigit() }
                DynamicIslandExpandedRegion(.bottom) { Text(context.state.phase) }
            } compactLeading: { Image(systemName:"pills").foregroundStyle(tint(context.state.color)) }
              compactTrailing: { Text(context.state.reference,style:.timer).monospacedDigit().minimumScaleFactor(0.8).frame(maxWidth:48) }
              minimal: { Image(systemName:"pills") }
              .widgetURL(URL(string:"mediclogger://dose/\(context.attributes.sessionID)"))
        }
    }
    private func tint(_ hex: String) -> Color { AccessibleColor.ink(hex) }
}
