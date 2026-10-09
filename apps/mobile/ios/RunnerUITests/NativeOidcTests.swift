import XCTest

final class NativeOidcTests: NativeDemoTest {
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

}
