"""Loopback-only GET proxy and authenticated outage control for native tests."""
import hmac
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import secrets
import socket
import threading


class NetworkFixture:
    def __init__(self, upstream_port=8088):
        self.secret = secrets.token_hex(24)
        self.online = threading.Event()
        self.online.set()
        self.threads = []
        fixture = self

        class QuietHandler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass  # Never log request headers or the private control secret.

        class Proxy(QuietHandler):
            def do_GET(self):
                if not fixture.online.is_set():
                    self.close_connection = True
                    try:
                        self.connection.shutdown(socket.SHUT_RDWR)
                    except OSError:
                        pass
                    return
                # Only the public discovery scenario uses this fixture. Do not
                # support domain mutations, arbitrary upstreams or absolute URLs.
                if not self.path.startswith("/api/v1/") or self.path.startswith("//"):
                    self.send_error(404)
                    return
                connection = http.client.HTTPConnection("127.0.0.1", upstream_port, timeout=10)
                try:
                    connection.request("GET", self.path, headers={"Accept": "application/json"})
                    response = connection.getresponse()
                    body = response.read(4 * 1024 * 1024 + 1)
                    if len(body) > 4 * 1024 * 1024:
                        self.send_error(502)
                        return
                    self.send_response(response.status)
                    self.send_header("Content-Type", response.getheader("Content-Type", "application/json"))
                    self.send_header("Content-Length", str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)
                except (OSError, http.client.HTTPException):
                    self.send_error(502)
                finally:
                    connection.close()

        class Control(QuietHandler):
            def do_PUT(self):
                if self.path != "/network" or not hmac.compare_digest(
                        self.headers.get("Authorization", ""), fixture.secret):
                    self.send_error(403)
                    return
                if self.headers.get("Content-Length") not in ("6", "7"):
                    self.send_error(400)
                    return
                self.connection.settimeout(5)
                body = self.rfile.read(int(self.headers["Content-Length"]))
                if body == b"online":
                    fixture.online.set()
                elif body == b"offline":
                    fixture.online.clear()
                else:
                    self.send_error(400)
                    return
                self.send_response(204)
                self.end_headers()

        self.proxy = ThreadingHTTPServer(("127.0.0.1", 0), Proxy)
        try:
            self.control = ThreadingHTTPServer(("127.0.0.1", 0), Control)
        except BaseException:
            self.proxy.server_close()
            raise
        self.api_url = f"http://localhost:{self.proxy.server_port}"
        self.control_url = f"http://127.0.0.1:{self.control.server_port}/network"

    def start(self):
        for server in (self.proxy, self.control):
            thread = threading.Thread(target=server.serve_forever)
            thread.start()
            self.threads.append((server, thread))

    def close(self):
        for server in (self.control, self.proxy):
            if any(started is server for started, _ in self.threads):
                server.shutdown()
            server.server_close()
        for _, thread in self.threads:
            thread.join(timeout=10)
            if thread.is_alive():
                raise RuntimeError("Network fixture did not stop")
        self.threads.clear()
