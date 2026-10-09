import XCTest

/// Shared UI controls; contains no XCTest entry points.
extension NativeDemoTest {
    func switchAccount(_ username: String, _ displayName: String) {
        restart()
        if !element("Sign in").waitForExistence(timeout: 3) {
            reveal("Sign out")
            signOut()
        }
        signIn(username, displayName: displayName)
    }

    func expect(_ label: String) {
        XCTAssertTrue(element(label).waitForExistence(timeout: 30), "Missing content: \(label)")
    }

    func expectContaining(_ label: String) {
        // Flutter SelectableText exposes message bodies as accessibility values
        // on iOS. Only displayed text/rows count; never an unsent composer value.
        let predicate = NSPredicate(format: "label CONTAINS %@ OR value CONTAINS %@", label, label)
        let deadline = Date().addingTimeInterval(30)
        repeat {
            if app.staticTexts.matching(predicate).firstMatch.exists ||
                app.otherElements.matching(predicate).firstMatch.exists { return }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        XCTFail("Missing displayed content: \(label)")
    }

    func button(_ label: String) {
        let control = app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
        XCTAssertTrue(control.waitForExistence(timeout: 30), "Missing button: \(label)")
        for _ in 0..<8 {
            if control.isHittable && control.isEnabled { break }
            app.swipeUp()
        }
        XCTAssertTrue(control.isEnabled && control.isHittable, "Button unavailable: \(label)")
        control.tap()
    }

    func item(_ title: String) {
        let control = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", title)).firstMatch
        XCTAssertTrue(control.waitForExistence(timeout: 30))
        XCTAssertTrue(control.isHittable)
        control.tap()
    }

    func confirm(_ label: String) {
        button(label)
        expect("Go back")
        button(label)
    }

    func openBooking(_ title: String) {
        button("Your activity")
        item(title)
        expect("Booking")
        expect(title)
    }

    func top() {
        for _ in 0..<6 { app.swipeDown() }
    }

    func field(_ label: String, _ value: String) {
        let predicate = NSPredicate(format: "label CONTAINS %@", label)
        func input() -> XCUIElement {
            let single = app.textFields.matching(predicate).firstMatch
            return single.exists ? single : app.textViews.matching(predicate).firstMatch
        }
        for _ in 0..<8 {
            if input().exists && input().isHittable { break }
            app.swipeUp()
        }
        let control = input()
        XCTAssertTrue(control.exists && control.isHittable, "Missing field: \(label)")
        control.tap()
        control.typeText(value)
        XCTAssertEqual(control.value as? String, value, "Field value mismatch: \(label)")
    }

    func replaceField(_ label: String, _ value: String) {
        let predicate = NSPredicate(format: "label CONTAINS %@", label)
        let control = app.textFields.matching(predicate).firstMatch
        XCTAssertTrue(control.waitForExistence(timeout: 30))
        for _ in 0..<8 {
            if control.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(control.isHittable)
        control.tap()
        // The direct-booking city is prefilled. Delete its actual value before
        // entering the unique run marker, then verify the complete replacement.
        let previous = control.value as? String ?? ""
        control.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: previous.count))
        control.typeText(value)
        XCTAssertEqual(control.value as? String, value)
    }
}
