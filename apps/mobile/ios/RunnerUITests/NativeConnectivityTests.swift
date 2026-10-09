import XCTest

/// Fault injection belongs to the disposable runner, never to the app binary.
final class NativeConnectivityTests: NativeDemoTest {
    func testCatalogConnectionRecovery() throws {
        try network(available: false)
        defer { try? network(available: true) }
        app.launch()
        expectContaining("Let’s try that again")
        expect("Try again")
        checkpoint("connectivity catalog shows connection failure", "connectivity-unavailable")
        try network(available: true)
        // Stay on the failed screen and exercise its own retry control.
        button("Try again")
        expectContaining("Garden refresh")
        expectContaining("CAD 65.50")
        XCTAssertFalse(element("Try again").exists)
        checkpoint("connectivity same screen retry loads real catalog", "connectivity-recovered")
    }

    private func network(available: Bool) throws {
        let environment = ProcessInfo.processInfo.environment
        let address = try XCTUnwrap(environment["BLISS_IOS_NETWORK_CONTROL_URL"])
        let url = try XCTUnwrap(URL(string: address))
        XCTAssertEqual(url.host, "127.0.0.1")
        var request = URLRequest(url: url, timeoutInterval: 10)
        request.httpMethod = "PUT"
        request.setValue(try XCTUnwrap(environment["BLISS_IOS_NETWORK_SECRET"]), forHTTPHeaderField: "Authorization")
        request.httpBody = Data((available ? "online" : "offline").utf8)
        let finished = expectation(description: "Runner acknowledged network state")
        URLSession.shared.dataTask(with: request) { _, response, error in
            XCTAssertNil(error)
            XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 204)
            finished.fulfill()
        }.resume()
        wait(for: [finished], timeout: 15)
    }
}
