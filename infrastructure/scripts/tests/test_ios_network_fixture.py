"""Exercise real sockets: outage/retry, control authorization and cleanup."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import http.client
from pathlib import Path
import socket
import sys
import threading
import unittest
import urllib.error
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from ios_network_fixture import NetworkFixture


class NetworkFixtureTests(unittest.TestCase):
    def setUp(self):
        self.requests = []
        requests = self.requests

        class Upstream(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def do_GET(self):
                requests.append(self.path)
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b'{"title":"Garden refresh","price":"65.50"}')

        self.upstream = ThreadingHTTPServer(("127.0.0.1", 0), Upstream)
        self.thread = threading.Thread(target=self.upstream.serve_forever)
        self.thread.start()
        self.fixture = NetworkFixture(self.upstream.server_port)
        self.fixture.start()

    def tearDown(self):
        self.fixture.close()
        self.upstream.shutdown()
        self.upstream.server_close()
        self.thread.join(timeout=10)

    def control(self, state, secret=None):
        request = urllib.request.Request(self.fixture.control_url, data=state.encode(), method="PUT",
                                         headers={"Authorization": secret or self.fixture.secret})
        with urllib.request.urlopen(request, timeout=5) as response:
            return response.status

    def fetch(self, path="/api/v1/offerings"):
        with urllib.request.urlopen(self.fixture.api_url + path, timeout=5) as response:
            return response.read()

    def test_real_connection_failure_then_recovery_without_upstream_restart(self):
        expected = self.fetch()
        self.assertEqual(self.control("offline"), 204)
        with self.assertRaises((http.client.RemoteDisconnected, urllib.error.URLError)):
            self.fetch()
        self.assertEqual(len(self.requests), 1, "Offline requests must not reach API")
        self.assertEqual(self.control("online"), 204)
        self.assertEqual(self.fetch(), expected)
        self.assertEqual(len(self.requests), 2)

    def test_bad_secret_and_invalid_state_cannot_change_connection(self):
        for state, secret, status in (("offline", "wrong", 403), ("invalid", None, 400)):
            with self.assertRaises(urllib.error.HTTPError) as error:
                self.control(state, secret)
            self.assertEqual(error.exception.code, status)
            error.exception.close()
        self.assertTrue(self.fixture.online.is_set())
        self.fetch()

    def test_non_api_routes_and_mutations_are_rejected(self):
        with self.assertRaises(urllib.error.HTTPError) as error:
            self.fetch("/q/health/ready")
        self.assertEqual(error.exception.code, 404)
        error.exception.close()
        request = urllib.request.Request(self.fixture.api_url + "/api/v1/bookings", data=b"{}", method="POST")
        with self.assertRaises(urllib.error.HTTPError) as error:
            urllib.request.urlopen(request, timeout=5)
        self.assertEqual(error.exception.code, 501)
        error.exception.close()
        self.assertEqual(self.requests, [])

    def test_listeners_close(self):
        ports = [self.fixture.proxy.server_port, self.fixture.control.server_port]
        self.fixture.close()
        for port in ports:
            with socket.socket() as probe:
                self.assertNotEqual(probe.connect_ex(("127.0.0.1", port)), 0)


if __name__ == "__main__":
    unittest.main()
