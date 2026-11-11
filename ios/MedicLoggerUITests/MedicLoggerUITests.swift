import XCTest
import UIKit

final class MedicLoggerUITests: XCTestCase {
    var app: XCUIApplication!
    override func setUpWithError() throws {
        continueAfterFailure=false
        app=XCUIApplication();app.launchArguments=["--ui-testing","--reset-test-data"];app.launch()
        XCTAssertTrue(app.buttons["add-medicine"].waitForExistence(timeout:10))
    }
    func enter(_ element: XCUIElement,_ value: String) { XCTAssertTrue(element.waitForExistence(timeout:5));element.tap();element.typeText(value) }
    func setSwitch(_ element: XCUIElement,on: Bool) {
        XCTAssertTrue(element.waitForExistence(timeout:5));let desired = on ? "1" : "0"
        if element.value as? String != desired { element.tap() }
        if element.value as? String != desired { element.coordinate(withNormalizedOffset:CGVector(dx:0.93,dy:0.5)).tap() }
        XCTAssertEqual(element.value as? String,desired)
    }
    func create(_ name: String,monitor: Bool = false,quantity: String? = nil) {
        app.buttons["add-medicine"].tap();enter(app.textFields["medicine-name"],name)
        if app.buttons["Done"].exists { app.buttons["Done"].tap() }
        XCTAssertEqual(app.switches["monitor-effects"].value as? String,"0")
        if monitor { setSwitch(app.switches["monitor-effects"],on:true) }
        if let quantity { setSwitch(app.switches["Track stock"],on:true);enter(app.textFields["medicine-quantity"],quantity) }
        app.buttons["save-medicine"].tap()
        XCTAssertTrue(app.buttons["Log dose: \(name)"].waitForExistence(timeout:5))
    }
    func take(_ name: String) { app.buttons["Log dose: \(name)"].tap();XCTAssertTrue(app.alerts.firstMatch.waitForExistence(timeout:4));app.alerts.buttons["Log dose"].tap() }
    func attach(_ name: String) { let a=XCTAttachment(screenshot:app.screenshot());a.name=name;a.lifetime = .keepAlways;add(a) }
    func testIntakeOnlyStockAndRefill() {
        create("Review intake",quantity:"2");take("Review intake")
        XCTAssertTrue(app.descendants(matching:.any).matching(NSPredicate(format:"identifier BEGINSWITH %@ AND identifier ENDSWITH %@","timeline-","-taken")).firstMatch.waitForExistence(timeout:5))
        XCTAssertEqual(app.buttons.matching(NSPredicate(format:"identifier BEGINSWITH %@","dose-active-")).count,0)
        app.tabBars.buttons["Meds"].tap();XCTAssertTrue(app.staticTexts["Stock: 1"].waitForExistence(timeout:5))
        app.buttons["Refill"].tap();enter(app.textFields["refill-quantity"],"5");app.navigationBars.buttons["Save"].tap()
        XCTAssertTrue(app.staticTexts["Stock: 5"].waitForExistence(timeout:5));attach("Inventory and refill")
    }
    func testNoEffectIsClosedTodayAndAfterRelaunch() {
        create("Review no effect",monitor:true);take("Review no effect")
        let over=app.buttons.matching(NSPredicate(format:"identifier BEGINSWITH %@","dose-over-")).firstMatch
        XCTAssertTrue(over.waitForExistence(timeout:5));over.tap()
        XCTAssertTrue(app.buttons["Skip"].waitForExistence(timeout:5));app.buttons["Skip"].tap()
        XCTAssertTrue(app.staticTexts["No effect"].waitForExistence(timeout:5));attach("No effect before relaunch");XCTAssertFalse(app.staticTexts["Waiting"].exists)
        XCTAssertEqual(app.buttons.matching(NSPredicate(format:"identifier BEGINSWITH %@","dose-active-")).count,0)
        XCTAssertEqual(app.buttons.matching(NSPredicate(format:"identifier BEGINSWITH %@","dose-details-")).count,1)
        app.terminate();app.launchArguments=["--ui-testing"];app.launch()
        XCTAssertTrue(app.staticTexts["No effect"].waitForExistence(timeout:5));XCTAssertFalse(app.staticTexts["Waiting"].exists);attach("No effect is closed after relaunch")
        app.tabBars.buttons["Calendar"].tap();app.swipeUp()
        XCTAssertEqual(app.buttons.matching(NSPredicate(format:"identifier BEGINSWITH %@","dose-details-")).count,1)
        XCTAssertEqual(app.descendants(matching:.any).matching(identifier:"timeline-2-taken").count,1)
        XCTAssertTrue(app.descendants(matching:.any).matching(identifier:"timeline-2-end").firstMatch.exists);attach("One compact no effect timeline")
    }
    func testEffectsRatingJournalAndCalendar() {
        create("Review effects",monitor:true);take("Review effects")
        let active=app.buttons.matching(NSPredicate(format:"identifier BEGINSWITH %@","dose-active-")).firstMatch
        XCTAssertTrue(active.waitForExistence(timeout:5));active.tap()
        XCTAssertTrue(app.staticTexts["Effects active"].waitForExistence(timeout:5))
        app.buttons.matching(NSPredicate(format:"identifier BEGINSWITH %@","dose-over-")).firstMatch.tap()
        XCTAssertTrue(app.buttons["rating-7"].waitForExistence(timeout:5));app.buttons["rating-7"].tap();app.buttons["Save rating"].tap()
        app.tabBars.buttons["Journal"].tap();XCTAssertTrue(app.staticTexts["Effectiveness: 7/10"].waitForExistence(timeout:5));attach("Rated dose in Journal")
        app.tabBars.buttons["Calendar"].tap();app.swipeUp();XCTAssertTrue(app.descendants(matching:.any).matching(identifier:"timeline-2-onset").firstMatch.waitForExistence(timeout:5));XCTAssertTrue(app.descendants(matching:.any).matching(identifier:"timeline-2-end").firstMatch.exists);XCTAssertEqual(app.buttons.matching(NSPredicate(format:"identifier BEGINSWITH %@","dose-details-")).count,1);attach("Calendar with linked dose timeline")
    }
    func testCycleAndVersionInSettings() {
        create("Review cycle");app.tabBars.buttons["Meds"].tap();app.buttons["Reminders"].tap();app.buttons["add-reminder"].tap()
        app.buttons["reminder-frequency"].tap();app.buttons["ON/OFF cycle"].tap()
        XCTAssertTrue(app.textFields["cycle-on"].waitForExistence(timeout:4));XCTAssertEqual(app.textFields["cycle-on"].value as? String,"21");XCTAssertEqual(app.textFields["cycle-off"].value as? String,"7");attach("ON OFF cycle editor")
        app.navigationBars.buttons["Cancel"].tap();app.navigationBars.buttons.element(boundBy:0).tap()
        app.buttons["open-settings"].tap();app.swipeUp()
        XCTAssertTrue(app.otherElements["app-version"].exists || app.staticTexts["Version 1.4.2"].waitForExistence(timeout:5));attach("Settings version")
    }
    func testCustomColorAndMultiMedicineExport() {
        app.buttons["add-medicine"].tap();enter(app.textFields["medicine-name"],"Review custom")
        app.swipeLeft() // The color row scrolls horizontally only when needed.
        if !app.buttons["custom-color"].isHittable { app.scrollViews.firstMatch.swipeLeft() }
        app.buttons["custom-color"].tap();let hex=app.textFields["color-hex"];hex.tap();hex.press(forDuration:1)
        if app.menuItems["Select All"].waitForExistence(timeout:1) { app.menuItems["Select All"].tap() };hex.typeText(String(repeating:XCUIKeyboardKey.delete.rawValue,count:8)+"236AC8")
        app.buttons["use-color"].tap();app.buttons["save-medicine"].tap();take("Review custom")
        create("Review second");take("Review second");app.tabBars.buttons["Export"].tap()
        setSwitch(app.switches["All medicines"],on:false);app.buttons["export-choose-medicines"].tap()
        app.buttons.containing(.staticText,identifier:"Review custom").firstMatch.tap();app.buttons.containing(.staticText,identifier:"Review second").firstMatch.tap();app.buttons["Apply"].tap()
        XCTAssertTrue(app.staticTexts["2 events selected"].waitForExistence(timeout:5));app.buttons["export-format"].tap();app.buttons["Excel (.xlsx)"].tap();XCTAssertTrue(app.staticTexts["2 events selected"].exists);attach("Multi medicine Excel export")
    }
    func testNotificationPermissionAndSavedCycle() {
        create("Review reminder");app.tabBars.buttons["Meds"].tap();app.buttons["Reminders"].tap();app.buttons["add-reminder"].tap()
        app.buttons["reminder-frequency"].tap();app.buttons["ON/OFF cycle"].tap();app.buttons["save-reminder"].tap()
        let springboard=XCUIApplication(bundleIdentifier:"com.apple.springboard")
        let alert=springboard.alerts.firstMatch
        if alert.waitForExistence(timeout:5) {
            let allow=alert.buttons.matching(NSPredicate(format:"label IN %@",["Allow","Autoriser","Permitir"])).firstMatch
            XCTAssertTrue(allow.exists);allow.tap()
        }
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format:"label CONTAINS %@","21 days ON · 7 days OFF")).firstMatch.waitForExistence(timeout:5));attach("Saved cycle reminder")
    }
    func testLanguageSelection() {
        app.buttons["open-settings"].tap()
        for (language,title) in [("Français","Réglages"),("Português","Definições"),("中文","设置"),("English","Settings")] {
            app.buttons["language-picker"].tap();app.buttons[language].tap();XCTAssertTrue(app.navigationBars[title].waitForExistence(timeout:4))
        }
    }
    func testLargestAccessibleText() {
        create("Review large text",monitor:true);take("Review large text");app.terminate()
        app.launchArguments=["--ui-testing","-UIPreferredContentSizeCategoryName",UIContentSizeCategory.accessibilityExtraExtraExtraLarge.rawValue];app.launch()
        app.tabBars.buttons["Journal"].tap();XCTAssertTrue(app.staticTexts["Waiting"].waitForExistence(timeout:5));attach("Largest text Journal")
        app.tabBars.buttons["Calendar"].tap();XCTAssertTrue(app.staticTexts["Choose dates"].waitForExistence(timeout:5));attach("Largest text Calendar");app.swipeUp();attach("Largest text Calendar details")
    }
    func testStatusLiveActivity() {
        create("Review live status",monitor:true);take("Review live status")
        app.buttons["open-settings"].tap();setSwitch(app.switches["status-toggle"],on:true);app.navigationBars.buttons["Done"].tap()
        XCTAssertTrue(app.staticTexts["Waiting"].waitForExistence(timeout:5));attach("Live status enabled")
    }
}
