import XCTest

/// All business mutations use the normal app UI, including real browser login.
final class NativeMarketplaceTests: NativeDemoTest {
    func testRequestToReviewLifecycle() {
        let runID = ProcessInfo.processInfo.environment["BLISS_IOS_MARKETPLACE_RUN_ID"] ?? ""
        XCTAssertNotNil(runID.range(of: "^[a-z0-9-]{1,40}$", options: .regularExpression))
        let title = "iOS garden \(runID)"
        let customerMessage = "Customer ready for the garden visit \(runID)"
        let providerMessage = "Provider confirmed the garden visit \(runID)"
        let review = "iOS demo: careful garden work and clear communication."

        switchAccount("customer", "customer")
        button("Your activity")
        tap("My requests")
        button("Create request")
        field("Request title", title)
        field("Describe the work", "Local iOS demo: tidy the garden and remove leaves.")
        field("Service city", "Toronto")
        tap("Category")
        tap("Gardening")
        field("Budget (optional)", "95.50")
        button("Create request")
        expect("Your request")
        expect(title)
        checkpoint("marketplace customer request created", "marketplace-request")

        switchAccount("provider", "Alex Demo")
        button("Your activity")
        tap("Open opportunities")
        item(title)
        button("Send quote")
        field("Scope of work", "Garden tidy and leaf removal for \(runID)")
        field("Price (CAD)", "85.50")
        button("Send quote")
        expect("Quote sent. You can follow it in My quotes.")
        checkpoint("marketplace provider quote submitted", "marketplace-quote")

        switchAccount("customer", "customer")
        button("Your activity")
        tap("My requests")
        item(title)
        confirm("Accept quote")
        expect("Booking")
        expect(title)
        button("Messages")
        field("Message", customerMessage)
        button("Send")
        expectContaining(customerMessage)
        checkpoint("marketplace quote accepted and customer message sent", "marketplace-customer-chat")

        switchAccount("provider", "Alex Demo")
        openBooking(title)
        button("Messages")
        expectContaining(customerMessage)
        field("Message", providerMessage)
        button("Send")
        expectContaining(providerMessage)
        checkpoint("marketplace two-way chat delivered", "marketplace-chat")
        button("Back")
        button("Accept and schedule")
        expect("Schedule service")
        button("Accept and schedule")
        expect("Scheduled")
        // Respect the normal UI's two-minute schedule. No clock or DB shortcut.
        let start = app.buttons["Start service"].firstMatch
        XCTAssertFalse(start.exists && start.isEnabled, "Service must not start before its scheduled time")
        XCTAssertTrue(start.waitForExistence(timeout: 150), "Start service did not appear at scheduled time")
        confirm("Start service")
        confirm("Mark work complete")
        expect("Waiting for customer")
        checkpoint("marketplace scheduled work completed by provider", "marketplace-provider-complete")

        switchAccount("customer", "customer")
        openBooking(title)
        confirm("Confirm completion")
        expect("Completed")
        reveal("Captured · CAD 85.50")
        checkpoint("marketplace customer confirmation and simulated capture", "marketplace-completed")

        restart()
        account("customer")
        openBooking(title)
        reveal("Captured · CAD 85.50")
        // Return to the top of the long booking page before checking messages.
        top()
        button("Messages")
        expectContaining(customerMessage)
        expectContaining(providerMessage)
        button("Back")
        button("Booking review")
        tap("Rating")
        tap("5 stars")
        field("Comment (optional)", review)
        button("Publish review")
        expect("Review published")
        button("Back")
        button("Booking review")
        expect("Review published")
        expectContaining("5 / 5 · Verified customer")
        expectContaining(review)
        checkpoint("marketplace restored booking chat and saved review", "marketplace-review")

        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 15))
        app.activate()
        expect("Review published")
        restart()
        account("customer")
        openBooking(title)
        button("Booking review")
        expect("Review published")
        expectContaining(review)
        checkpoint("marketplace review survives background and process restart", "marketplace-restored")
        restart()
        account("customer")
        signOut()
        expect("Sign in")
        checkpoint("marketplace lifecycle finished signed out", "marketplace-signed-out")
    }

    private func switchAccount(_ username: String, _ displayName: String) {
        restart()
        if !element("Sign in").waitForExistence(timeout: 3) {
            reveal("Sign out")
            signOut()
        }
        signIn(username, displayName: displayName)
    }

    private func expect(_ label: String) {
        XCTAssertTrue(element(label).waitForExistence(timeout: 30), "Missing content: \(label)")
    }

    private func expectContaining(_ label: String) {
        XCTAssertTrue(app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", label)).firstMatch
            .waitForExistence(timeout: 30), "Missing content: \(label)")
    }

    private func button(_ label: String) {
        let control = app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
        XCTAssertTrue(control.waitForExistence(timeout: 30), "Missing button: \(label)")
        for _ in 0..<8 {
            if control.isHittable && control.isEnabled { break }
            app.swipeUp()
        }
        XCTAssertTrue(control.isEnabled && control.isHittable, "Button unavailable: \(label)")
        control.tap()
    }

    private func item(_ title: String) {
        let control = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", title)).firstMatch
        XCTAssertTrue(control.waitForExistence(timeout: 30))
        XCTAssertTrue(control.isHittable)
        control.tap()
    }

    private func confirm(_ label: String) {
        button(label)
        expect("Go back")
        button(label)
    }

    private func openBooking(_ title: String) {
        button("Your activity")
        item(title)
        expect("Booking")
        expect(title)
    }

    private func top() {
        for _ in 0..<6 { app.swipeDown() }
    }

    private func field(_ label: String, _ value: String) {
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
}
