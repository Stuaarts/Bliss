import XCTest

/// Drives the unmodified Flutter app and Apple's authentication browser from a
/// separate process. Credentials belong only to the runner's disposable realm.
class NativeDemoTest: XCTestCase {
    let app = XCUIApplication(bundleIdentifier: "com.bliss.blissMobile")
    let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

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

    func password(_ role: String) -> String {
        ProcessInfo.processInfo.environment["BLISS_\(role)_PASSWORD"] ?? ""
    }

    func element(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    func reveal(_ label: String) {
        for _ in 0..<6 {
            let control = element(label)
            if control.exists && control.isHittable { return }
            app.swipeUp()
        }
        XCTFail("Missing visible app control: \(label)")
    }

    func tap(_ label: String) {
        XCTAssertTrue(element(label).waitForExistence(timeout: 30), "Missing app control: \(label)")
        if !element(label).isHittable { reveal(label) }
        element(label).tap()
    }

    func account(_ displayName: String) {
        XCTAssertTrue(element("Hello, \(displayName)").waitForExistence(timeout: 30),
                      "Expected authenticated profile after browser callback or refresh")
        XCTAssertTrue(element("customer").exists)
        reveal("Sign out")
    }

    func restart() {
        app.terminate()
        XCTAssertTrue(app.wait(for: .notRunning, timeout: 15))
        app.launch()
        tap("Your account")
    }

    func consent() {
        // ASWebAuthenticationSession's consent prompt is owned by SpringBoard.
        for surface in [springboard, app] {
            let button = surface.alerts.buttons["Continue"].firstMatch
            if button.exists && button.isHittable { button.tap() }
        }
    }

    func browserField() -> (XCUIApplication, XCUIElement)? {
        let surfaces = [app, XCUIApplication(bundleIdentifier: "com.apple.SafariViewService"),
                        XCUIApplication(bundleIdentifier: "com.apple.AuthenticationServicesUI.AuthenticationServicesAgent")]
        for surface in surfaces {
            let field = surface.webViews.textFields.firstMatch
            if field.exists && field.isHittable { return (surface, field) }
        }
        return nil
    }

    func signIn(_ username: String, displayName: String) {
        tap("Sign in")
        // Cold hosted simulators can still be loading the first browser page
        // after consent. Require the real form within a bounded startup wait.
        let deadline = Date().addingTimeInterval(90)
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

    func signOut() {
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

    func checkpoint(_ description: String, _ name: String) {
        print("Verified iOS: \(description)")
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "bliss-ios-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
