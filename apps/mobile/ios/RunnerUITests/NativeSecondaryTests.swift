import XCTest

/// Cancellation and a manual, independent admin decision through normal UI.
final class NativeSecondaryTests: NativeDemoTest {
    func testDirectCancellationAndManualDispute() {
        XCTAssertFalse(password("ADMIN").isEmpty)
        let runID = ProcessInfo.processInfo.environment["BLISS_IOS_MARKETPLACE_RUN_ID"] ?? ""
        XCTAssertNotNil(runID.range(of: "^[a-z0-9-]{1,40}$", options: .regularExpression))
        let cancelledCity = "Demo \(runID) cancel"
        let disputeCity = "Demo \(runID) dispute"
        let reason = "Local demo: unfinished garden cleanup for \(runID)"
        let evidence = "Provider demo response: cleanup details for \(runID)"
        let rationale = "Local demo decision: customer favored after reviewing both accounts for \(runID)"

        switchAccount("customer", "customer")
        directBooking(cancelledCity)
        expect("Waiting for provider")
        confirm("Cancel booking")
        expect("Cancelled")
        reveal("Voided · CAD 65.50")
        restart()
        account("customer")
        booking(cancelledCity)
        expect("Cancelled")
        reveal("Voided · CAD 65.50")
        checkpoint("secondary cancellation and simulated void survive process restart", "secondary-cancelled")

        switchAccount("customer", "customer")
        directBooking(disputeCity)
        expect("Waiting for provider")
        checkpoint("secondary direct booking created", "secondary-created")

        switchAccount("provider", "Alex Demo")
        booking(disputeCity)
        button("Accept and schedule")
        expect("Schedule service")
        button("Accept and schedule")
        expect("Scheduled")
        let start = app.buttons["Start service"].firstMatch
        XCTAssertFalse(start.exists && start.isEnabled, "Respect the actual scheduled time")
        XCTAssertTrue(start.waitForExistence(timeout: 150))
        confirm("Start service")
        confirm("Mark work complete")
        expect("Waiting for customer")
        checkpoint("secondary provider completed scheduled work", "secondary-provider-complete")

        switchAccount("customer", "customer")
        booking(disputeCity)
        button("Dispute / support")
        field("What happened?", reason)
        button("Open dispute")
        expect("Collecting evidence")
        expectContaining(reason)
        XCTAssertFalse(element("Resolve dispute").exists)
        checkpoint("secondary customer opened dispute without decision controls", "secondary-opened")

        switchAccount("provider", "Alex Demo")
        booking(disputeCity)
        button("Dispute / support")
        expectContaining(reason)
        button("Add evidence")
        field("Evidence", evidence)
        button("Add evidence")
        expect("Waiting for admin review")
        expectContaining(evidence)
        XCTAssertFalse(element("Resolve dispute").exists)
        checkpoint("secondary provider evidence awaits independent admin", "secondary-evidence")

        switchAccount("admin", "admin")
        expect("admin")
        button("Your activity")
        tap("Disputes")
        item("Garden refresh")
        // Confirm the exact run's reason and evidence before deciding anything.
        expectContaining(reason)
        expectContaining(evidence)
        button("Resolve dispute")
        tap("Outcome")
        tap("Customer favored")
        field("Decision rationale", rationale)
        button("Resolve dispute")
        expect("Decision applied.")
        expectContaining("Decision: customer favored")
        checkpoint("secondary independent admin applied customer favored decision", "secondary-resolved")

        switchAccount("customer", "customer")
        booking(disputeCity)
        expect("Cancelled")
        reveal("Voided · CAD 65.50")
        checkpoint("secondary dispute settlement voided simulated authorization", "secondary-voided")
        top()
        button("Dispute / support")
        expectContaining(reason)
        expect("Decision applied.")
        expectContaining(rationale)
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 15))
        app.activate()
        expect("Decision applied.")
        restart()
        account("customer")
        booking(disputeCity)
        button("Dispute / support")
        expect("Decision applied.")
        expectContaining(rationale)
        checkpoint("secondary decision survives background and process restart", "secondary-restored")
        restart()
        account("customer")
        signOut()
        expect("Sign in")
        checkpoint("secondary lifecycle finished signed out", "secondary-signed-out")
    }

    private func directBooking(_ city: String) {
        button("Back") // Account -> public catalog.
        expect("All services")
        tap("Gardening")
        item("Garden refresh")
        button("Request booking")
        replaceField("Service city", city)
        button("Request booking")
        expect("Booking")
        expect("Garden refresh")
        expectContaining(city)
    }

    private func booking(_ city: String) {
        button("Your activity")
        item(city) // Both bookings have the same offering title.
        expect("Booking")
        expect("Garden refresh")
        expectContaining(city)
    }
}
