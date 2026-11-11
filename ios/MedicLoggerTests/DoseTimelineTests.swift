import XCTest
@testable import MedicLogger

final class DoseTimelineTests: XCTestCase {
    private let utc = TimeZone(secondsFromGMT:0)!
    private func date(_ text: String) -> Date { ISO8601DateFormatter().date(from:text)! }
    func testTakenOnsetEndStayInOneDose() {
        var dose = Dose();dose.id = 7;dose.takenAt = date("2026-11-05T08:00:00Z");dose.onsetAt = date("2026-11-05T08:30:00Z");dose.endedAt = date("2026-11-05T10:00:00Z")
        let rows = CalendarDoses.on(dose.takenAt,from:[dose],zone:utc)
        XCTAssertEqual(rows.count,1);XCTAssertEqual(rows[0].id,7)
        XCTAssertEqual(rows[0].timeline.map(\.kind),[.taken,.onset,.end]);XCTAssertEqual(rows[0].timeline.filter { $0.kind == .taken }.count,1)
    }
    func testClosedWithoutEffectNeverHasPendingStages() {
        var data = AppData(),medicine = Medication();medicine.name = "No effect test";medicine.monitorEffects = true
        let id = try! data.saveMedicine(medicine);let doseID = try! data.take(id);try! data.mark(doseID,onset:false)
        let dose = data.doses[0];XCTAssertFalse(dose.active);XCTAssertEqual(dose.status,"closed_without_effect")
        XCTAssertEqual(dose.timeline[1].label,"Onset");XCTAssertEqual(dose.timeline[1].state,.skipped)
        XCTAssertFalse(dose.timeline.contains { $0.state == .pending });XCTAssertEqual(dose.timeline.last?.date,dose.endedAt)
    }
    func testOvernightDoseKeepsFullTimelineOnBothDays() {
        var dose = Dose();dose.id = 1;dose.takenAt = date("2026-11-04T23:50:00Z");dose.onsetAt = date("2026-11-05T00:20:00Z");dose.endedAt = date("2026-11-05T01:00:00Z")
        for day in ["2026-11-04T00:00:00Z","2026-11-05T00:00:00Z"] {
            let result = CalendarDoses.on(date(day),from:[dose],zone:utc);XCTAssertEqual(result.count,1);XCTAssertEqual(result[0].timeline.count,3);XCTAssertEqual(result[0].timeline.first?.date,dose.takenAt)
        }
        XCTAssertTrue(CalendarDoses.on(date("2026-11-06T00:00:00Z"),from:[dose],zone:utc).isEmpty)
    }
    func testSeparateIntakesOfSameMedicineRemainSeparate() {
        var a = Dose();a.id = 1;a.medicineID = 4;a.takenAt = date("2026-11-05T08:00:00Z");a.endedAt = a.takenAt
        var b = a;b.id = 2;b.takenAt = date("2026-11-05T09:00:00Z");b.onsetAt = b.takenAt;b.endedAt = b.takenAt
        XCTAssertEqual(CalendarDoses.on(a.takenAt,from:[b,a],zone:utc).map(\.id),[1,2])
    }
    func testIntakeOnlyHasNoEffectStages() {
        var dose = Dose();dose.monitorEffects = false
        XCTAssertEqual(dose.timeline.count,1);XCTAssertEqual(dose.timeline[0].kind,.taken)
    }
}
