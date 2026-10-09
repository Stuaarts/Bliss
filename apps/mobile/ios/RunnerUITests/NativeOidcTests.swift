import XCTest

/// Drives the unmodified Flutter app and Apple's authentication browser from a
/// separate process. Credentials belong only to the runner's disposable realm.
final class NativeOidcTests: XCTestCase {
    private let app = XCUIApplication(bundleIdentifier: "com.bliss.blissMobile")
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    override func setUpWithError() throws {
        continueAfterFailure = false
        #if !targetEnvironment(simulator)
        XCTFail("This test requires a disposable iOS Simulator.")
        #endif
        XCTAssertEqual(ProcessInfo.processInfo.environment["BLISS_IOS_AUTH_OPT_IN"], "isolated-local")
        XCTAssertFalse(password("CUSTOMER").isEmpty)
        XCTAssertFalse(password("PROVIDER").isEmpty)
    }

    override func tearDownWithError() throws {
        if testRun?.hasSucceeded == false {
            print("Bliss failure UI: \(app.debugDescription)")
            print("SpringBoard failure UI: \(springboard.debugDescription)")
        }
    }

    func testRealBrowserOidcLifecycle() {
        app.launch()
        tap("Your account")
        XCTAssertTrue(element("Sign in").waitForExistence(timeout: 30))

        signIn("customer", displayName: "customer")
        XCTAssertFalse(element("Manage your business").exists)
        checkpoint("customer browser PKCE callback and authenticated profile", "customer-signed-in")

        restart()
        account("customer")
        checkpoint("customer refresh-token recovery after process termination", "customer-restored")

        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 15))
        app.activate()
        account("customer")
        checkpoint("customer background and resume", "customer-resumed")

        signOut()
        restart()
        XCTAssertTrue(element("Sign in").waitForExistence(timeout: 30))
        XCTAssertFalse(element("Hello, customer").exists)
        checkpoint("signed-out state survives process termination", "signed-out")

        // A fresh credential form is mandatory. Silent reuse of customer SSO
        // cannot satisfy this step, even if the app cleared its local tokens.
        signIn("provider", displayName: "Alex Demo")
        reveal("Manage your business")
        XCTAssertFalse(element("Hello, customer").exists)
        checkpoint("provider fresh browser login and provider capability", "provider-signed-in")

        restart()
        account("Alex Demo")
        reveal("Manage your business")
        checkpoint("provider refresh-token recovery", "provider-restored")
        signOut()

        signIn("customer", displayName: "customer")
        app.swipeUp()
        XCTAssertFalse(element("Manage your business").exists)
        XCTAssertFalse(element("Hello, Alex Demo").exists)
        checkpoint("switch back to customer without stale provider controls", "customer-switched")
        signOut()
        restart()
        XCTAssertTrue(element("Sign in").waitForExistence(timeout: 30))
        checkpoint("final logout remains signed out", "final-signed-out")
    }

    private func password(_ role: String) -> String {
        ProcessInfo.processInfo.environment["BLISS_\(role)_PASSWORD"] ?? ""
    }

    private func element(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func reveal(_ label: String) {
        for _ in 0..<6 {
            let control = element(label)
            if control.exists && control.isHittable { return }
            app.swipeUp()
        }
        XCTFail("Missing visible app control: \(label)")
    }

    private func tap(_ label: String) {
        XCTAssertTrue(element(label).waitForExistence(timeout: 30), "Missing app control: \(label)")
        if !element(label).isHittable { reveal(label) }
        element(label).tap()
    }

    private func account(_ displayName: String) {
        XCTAssertTrue(element("Hello, \(displayName)").waitForExistence(timeout: 30),
                      "Expected authenticated profile after browser callback or refresh")
        XCTAssertTrue(element("customer").exists)
        reveal("Sign out")
    }

    private func restart() {
        app.terminate()
        XCTAssertTrue(app.wait(for: .notRunning, timeout: 15))
        app.launch()
        tap("Your account")
    }

    private func consent() {
        // ASWebAuthenticationSession's consent prompt is owned by SpringBoard.
        for surface in [springboard, app] {
            let button = surface.alerts.buttons["Continue"].firstMatch
            if button.exists && button.isHittable { button.tap() }
        }
    }

    private func browserField() -> (XCUIApplication, XCUIElement)? {
        let surfaces = [app, XCUIApplication(bundleIdentifier: "com.apple.SafariViewService"),
                        XCUIApplication(bundleIdentifier: "com.apple.AuthenticationServicesUI.AuthenticationServicesAgent")]
        for surface in surfaces {
            let field = surface.webViews.textFields.firstMatch
            if field.exists && field.isHittable { return (surface, field) }
        }
        return nil
    }

    private func signIn(_ username: String, displayName: String) {
        tap("Sign in")
        let deadline = Date().addingTimeInterval(30)
        var field: (XCUIApplication, XCUIElement)?
        repeat {
            consent()
            field = browserField()
            if field != nil { break }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        guard let (browser, usernameField) = field else {
            XCTFail("Expected fresh local Keycloak credential form, not silent SSO")
            return
        }
        // The build pins the issuer to loopback; verify the rendered realm too
        // before entering the runner-generated credentials.
        XCTAssertTrue(browser.staticTexts["BLISS"].exists, "Expected Bliss realm heading")
        usernameField.tap()
        usernameField.typeText(username)
        let secret = browser.webViews.secureTextFields.firstMatch
        XCTAssertTrue(secret.exists)
        secret.tap()
        secret.typeText(password(username.uppercased()))
        let submit = browser.webViews.buttons["Sign In"].firstMatch
        XCTAssertTrue(submit.exists, "Expected local Keycloak submit control")
        submit.tap()
        account(displayName)
    }

    private func signOut() {
        tap("Sign out")
        let deadline = Date().addingTimeInterval(30)
        repeat {
            consent()
            let signIn = element("Sign in")
            if signIn.exists && signIn.isEnabled && signIn.isHittable { return }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        XCTFail("Identity-provider logout did not return to enabled sign in")
    }

    private func checkpoint(_ description: String, _ name: String) {
        print("Verified iOS: \(description)")
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "bliss-ios-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
