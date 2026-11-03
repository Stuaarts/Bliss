import XCTest
import CoreGraphics
import Foundation

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
        try verifyLocalTransport()
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

    private func probe(_ url: String) -> [String: Any]? {
        // Run inside the simulator, independently of the host's readiness GET.
        // No shared cookies/credentials or authentication requests are used.
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 10
        configuration.timeoutIntervalForResource = 10
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let finished = expectation(description: "Local transport: \(url)")
        var document: [String: Any]?
        session.dataTask(with: URL(string: url)!) { data, response, error in
            defer { finished.fulfill() }
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            let failure = error as NSError?
            // Fixed unauthenticated URLs and status/error codes only; no bodies,
            // tokens, response headers or generated passwords enter diagnostics.
            print("Bliss simulator transport: \(url) status=\(status) error=\(failure?.domain ?? "none")/\(failure?.code ?? 0)")
            if error == nil, status == 200, let data = data {
                document = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            }
        }.resume()
        wait(for: [finished], timeout: 15)
        return document
    }

    private func verifyLocalTransport() throws {
        let discoveryPath = "/realms/bliss/.well-known/openid-configuration"
        let discovery = probe("http://localhost:8180" + discoveryPath)
        let readiness = probe("http://localhost:8088/q/health/ready")
        // The stack intentionally listens on IPv4 loopback. Record both address
        // families without treating absent IPv6 service as a failed requirement.
        for host in ["127.0.0.1", "[::1]"] {
            _ = probe("http://\(host):8180" + discoveryPath)
            _ = probe("http://\(host):8088/q/health/ready")
        }
        let issuer = try XCTUnwrap(discovery?["issuer"] as? String,
                                  "Simulator cannot read canonical OIDC discovery")
        XCTAssertEqual(issuer, "http://localhost:8180/realms/bliss")
        XCTAssertEqual(readiness?["status"] as? String, "UP",
                       "Simulator cannot reach the ready API")
        print("Verified iOS transport: canonical discovery and API readiness")
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
        let profile = element("Hello, \(displayName)")
        let deadline = Date().addingTimeInterval(30)
        repeat {
            if profile.exists { break }
            // A completed callback may dispose of the optional prompt. Avoid
            // tapping stale/off-screen AX elements after the profile is ready.
            for surface in [app, springboard] {
                let skip = surface.alerts["Save Password?"].buttons["Not Now"].firstMatch
                if skip.exists && skip.isHittable {
                    let frame = skip.frame
                    if !frame.isEmpty && app.frame.contains(frame) {
                        skip.tap()
                        break
                    }
                }
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        XCTAssertTrue(profile.exists,
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
