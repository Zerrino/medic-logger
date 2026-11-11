import SwiftUI

enum Theme {
    static let red = Color("AccentColor"), fill = Color(hex:"B63840"), background = Color(uiColor:.systemGroupedBackground)
    static let palette = ["B63840","3276B5","A86612","7B55A2","C15C85","536878"]
}
extension Color {
    init(hex: String) { let n = UInt32(hex.trimmingCharacters(in:CharacterSet(charactersIn:"#")),radix:16) ?? 0xB63840;self.init(red:Double((n >> 16)&255)/255,green:Double((n >> 8)&255)/255,blue:Double(n&255)/255) }
    var hex: String { var r:CGFloat = 0,g:CGFloat = 0,b:CGFloat = 0,a:CGFloat = 0;UIColor(self).getRed(&r,green:&g,blue:&b,alpha:&a);return String(format:"%02X%02X%02X",Int(r*255),Int(g*255),Int(b*255)) }
}
struct MedicineIcon: View {
    let color: String
    @ScaledMetric(relativeTo:.body) private var size = 44.0
    var body: some View { Image(systemName:"pills.fill").font(.title3).foregroundStyle(AccessibleColor.ink(color)).frame(width:min(64,size),height:min(64,size)).background(Color(hex:color).opacity(0.10),in:.rect(cornerRadius:14)).accessibilityHidden(true) }
}
struct StatusBadge: View {
    let dose: Dose, store: MedicStore
    private var text: String { if !dose.monitorEffects { return store.t("Taken") };if dose.endedAt != nil { return store.t(dose.onsetAt == nil ? "No effect" : "Completed") };return store.t(dose.onsetAt == nil ? "Waiting" : "Effects active") }
    var body: some View { Text(text).font(.caption.weight(.semibold)).foregroundStyle(.primary).padding(.horizontal,8).padding(.vertical,3).background(Color(hex:dose.color).opacity(0.10),in:.capsule).accessibilityIdentifier("dose-status-\(dose.id)") }
}
struct Card<Content: View>: View {
    var padding: CGFloat = 18
    var spacing: CGFloat = 14
    var radius: CGFloat = 22
    @ViewBuilder let content: Content
    var body: some View { VStack(alignment:.leading,spacing:spacing) { content }.padding(padding).frame(maxWidth:.infinity,alignment:.leading).background(Color(uiColor:.secondarySystemGroupedBackground),in:.rect(cornerRadius:radius)) }
}
struct PressFeedbackStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label.opacity(configuration.isPressed ? 0.65 : 1) }
}
struct EmptyState: View {
    let title: String, symbol: String
    var body: some View { VStack(spacing:12) { Image(systemName:symbol).font(.largeTitle).foregroundStyle(Theme.red.opacity(0.65));Text(title).font(.headline).multilineTextAlignment(.center).foregroundStyle(.secondary) }.frame(maxWidth:.infinity).padding(.vertical,32) }
}
struct Screen<Content: View>: View {
    let title: String, settingsLabel: String, settings: ()->Void
    @ViewBuilder let content: Content
    var body: some View {
        NavigationStack { content.navigationTitle(title).toolbar { ToolbarItem(placement:.topBarTrailing) { Button(settingsLabel,systemImage:"gearshape",action:settings).labelStyle(.iconOnly).accessibilityIdentifier("open-settings") } } }
    }
}
extension MedicStore {
    func dateText(_ date: Date) -> String { date.formatted(.dateTime.day().month(.abbreviated).year().hour().minute().locale(locale)) }
    func timeText(_ date: Date) -> String { date.formatted(.dateTime.hour().minute().locale(locale)) }
    func elapsed(_ since: Date, now: Date = Date()) -> String {
        let minutes = max(0,Int(now.timeIntervalSince(since)/60));if minutes < 1 { return t("< 1 min") };if minutes < 60 { return t("%d min",minutes) };let h = minutes/60;if h < 24 { return minutes%60 == 0 ? t("%d h",h) : t("%d h %d min",h,minutes%60) };return t("%d d",h/24)
    }
}
