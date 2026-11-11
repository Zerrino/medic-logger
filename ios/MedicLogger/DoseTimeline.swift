import Foundation

struct DoseTimelineStep: Identifiable {
    enum Kind: String { case taken, onset, end }
    enum State { case recorded, pending, skipped }
    let kind: Kind, label: String, date: Date?, state: State
    var id: Kind { kind }
}
extension Dose {
    var timeline: [DoseTimelineStep] {
        var steps = [DoseTimelineStep(kind:.taken,label:"Taken",date:takenAt,state:.recorded)]
        guard monitorEffects else { return steps }
        steps.append(DoseTimelineStep(kind:.onset,label:"Onset",date:onsetAt,state:onsetAt != nil ? .recorded : endedAt == nil ? .pending : .skipped))
        steps.append(DoseTimelineStep(kind:.end,label:endedAt == nil ? "End" : onsetAt == nil ? "Closed" : "Ended",date:endedAt,state:endedAt == nil ? .pending : .recorded))
        return steps
    }
    func hasEvent(on day: Date, zone: TimeZone = .current) -> Bool {
        let calendar = DayMath.calendar(zone)
        guard let interval = calendar.dateInterval(of:.day,for:day) else { return false }
        return [takenAt,onsetAt,endedAt].compactMap { $0 }.contains { $0 >= interval.start && $0 < interval.end }
    }
    func firstEvent(on day: Date, zone: TimeZone = .current) -> Date? {
        let calendar = DayMath.calendar(zone)
        return [takenAt,onsetAt,endedAt].compactMap { $0 }.filter { calendar.isDate($0,inSameDayAs:day) }.min()
    }
}
enum CalendarDoses {
    static func on(_ day: Date, from doses: [Dose], zone: TimeZone = .current) -> [Dose] {
        doses.filter { $0.hasEvent(on:day,zone:zone) }.sorted {
            let a = $0.firstEvent(on:day,zone:zone)!, b = $1.firstEvent(on:day,zone:zone)!
            return a != b ? a < b : $0.id < $1.id
        }
    }
}
