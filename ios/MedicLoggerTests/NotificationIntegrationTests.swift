import XCTest
import UserNotifications
import ActivityKit
@testable import MedicLogger

final class NotificationIntegrationTests: XCTestCase {
    @MainActor func testNativeScheduledNotifications() async throws {
        let center=UNUserNotificationCenter.current(),permission=await UNUserNotificationCenter.current().notificationSettings()
        try XCTSkipUnless(permission.authorizationStatus == .authorized,"Run the notification permission UI test first on the dedicated simulator.")
        var data=AppData();var m=Medication();m.name="Review notification";let id=try data.saveMedicine(m)
        var r=Reminder();r.medicineID=id;r.frequency="cycle";_ = try data.saveReminder(r);data.settings.alertMode="sound";data.settings.sound="chime"
        let result=await NotificationClient.schedule(data);XCTAssertTrue(result.allowed);XCTAssertNotNil(result.until)
        var pending=await center.pendingNotificationRequests();XCTAssertEqual(pending.count,60)
        XCTAssertTrue(pending.allSatisfy { $0.content.categoryIdentifier == "MEDICATION_REMINDER" && $0.content.sound != nil })
        XCTAssertEqual(Set(pending.compactMap { ($0.content.userInfo["medicineID"] as? NSNumber)?.int64Value }),[id])
        let categories=await center.notificationCategories();XCTAssertEqual(categories.first?.actions.first?.identifier,"LOG_DOSE")
        data.settings.alertMode="silent";_ = await NotificationClient.schedule(data);pending=await center.pendingNotificationRequests();XCTAssertEqual(pending.count,60);XCTAssertTrue(pending.allSatisfy { $0.content.sound == nil })
        data.settings.remindersEnabled=false;_ = await NotificationClient.schedule(data);pending=await center.pendingNotificationRequests();XCTAssertTrue(pending.isEmpty);XCTAssertTrue(data.doses.isEmpty)
    }
    @MainActor func testNativeLiveActivityLifecycle() async throws {
        try XCTSkipUnless(ActivityAuthorizationInfo().areActivitiesEnabled,"Live Activities are disabled by this simulator's iOS settings.")
        var data=AppData();data.settings.showStatus=true
        var dose=Dose();dose.id=900002;dose.medicineID=900001;dose.medicineName="Review live activity";dose.takenAt=Date().addingTimeInterval(-60);data.doses=[dose]
        await LiveStatus.update(data)
        let activity=try XCTUnwrap(Activity<DoseActivity>.activities.first { $0.attributes.sessionID == dose.id })
        XCTAssertTrue(activity.content.state.waiting);XCTAssertEqual(activity.content.state.name,dose.medicineName)
        let changed=expectation(description:"Native Live Activity receives its active state")
        let listener=Task { for await content in activity.contentUpdates { if !content.state.waiting { changed.fulfill();break } } }
        data.doses[0].onsetAt=Date();await LiveStatus.update(data);await fulfillment(of:[changed],timeout:5);listener.cancel()
        data.settings.showStatus=false;await LiveStatus.update(data);XCTAssertFalse(Activity<DoseActivity>.activities.contains { $0.attributes.sessionID == dose.id })
    }
}
