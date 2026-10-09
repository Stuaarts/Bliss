#!/usr/bin/env python3
"""Real browser OIDC acceptance against a disposable, loopback-only Mac stack."""
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import tarfile
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
KEYCLOAK = "26.7.4"
KEYCLOAK_SHA256 = "04823c336b797a7e18889a44262a7a64e6bf624cbbcc518c85e2622176ff2eee"
CHECKPOINTS = [
    "customer browser PKCE callback and authenticated profile",
    "customer refresh-token recovery after process termination",
    "customer background and resume",
    "signed-out state survives process termination",
    "provider fresh browser login and provider capability",
    "provider refresh-token recovery",
    "switch back to customer without stale provider controls",
    "final logout remains signed out",
]
SCREENSHOTS = ["customer-signed-in", "customer-restored", "customer-resumed", "signed-out",
               "provider-signed-in", "provider-restored", "customer-switched", "final-signed-out"]


class Runner:
    def __init__(self):
        evidence_root = ROOT / ".work/ios-oidc"
        private_root = ROOT / ".work/ios-oidc-private"
        evidence_root.mkdir(parents=True, exist_ok=True)
        private_root.mkdir(parents=True, exist_ok=True)
        self.evidence = Path(tempfile.mkdtemp(prefix="run-", dir=evidence_root))
        self.private = Path(tempfile.mkdtemp(prefix="run-", dir=private_root))
        self.private.chmod(0o700)
        self.passwords = {role: secrets.token_hex(18) for role in ("customer", "provider", "db")}
        self.processes = []
        self.handles = []
        self.device = None
        self.pg_bin = None
        self.pg_started = False
        for password in self.passwords.values():
            if os.environ.get("GITHUB_ACTIONS") == "true":
                print(f"::add-mask::{password}", flush=True)

    def scrub(self, text):
        for password in self.passwords.values():
            text = text.replace(password, "[REDACTED]")
        # Defense in depth for diagnostics: never retain token-shaped values.
        return re.sub(r"\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", "[REDACTED JWT]", text)

    def run(self, args, name, timeout=300, cwd=ROOT, env=None, check=True):
        print(f"Running: {name}", flush=True)
        process = subprocess.Popen([str(arg) for arg in args], cwd=cwd, env=env,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, start_new_session=True)
        expired = False
        try:
            output, _ = process.communicate(timeout=timeout)
        except subprocess.TimeoutExpired:
            expired = True
            self.stop_group(process)
            output, _ = process.communicate()
        except BaseException:
            self.stop_group(process)
            output, _ = process.communicate()
            (self.evidence / f"{name}.log").write_text(self.scrub(output))
            raise
        output = self.scrub(output)
        (self.evidence / f"{name}.log").write_text(output)
        failed = expired or process.returncode != 0
        if failed:
            print("\n".join(output.splitlines()[-80:]), flush=True)
            print(f"{name}: {'deadline exceeded' if expired else 'exit ' + str(process.returncode)}", flush=True)
            if check:
                raise RuntimeError(f"{name} failed")
        return output

    @staticmethod
    def stop_group(process):
        try:
            os.killpg(process.pid, signal.SIGTERM)
            process.wait(timeout=10)
        except (ProcessLookupError, subprocess.TimeoutExpired):
            pass
        finally:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait()

    def start(self, args, name, env=None):
        handle = (self.private / f"{name}.log").open("w")
        self.handles.append(handle)
        process = subprocess.Popen([str(arg) for arg in args], cwd=ROOT, env=env,
                                   stdout=handle, stderr=subprocess.STDOUT, start_new_session=True)
        self.processes.append((process, name))
        return process

    def wait_url(self, url, process, timeout=180):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError("Local test service exited before readiness")
            try:
                with urllib.request.urlopen(url, timeout=3) as response:
                    if response.status == 200:
                        return json.load(response)
            except (urllib.error.URLError, TimeoutError):
                pass
            time.sleep(1)
        raise RuntimeError("Local test service readiness deadline exceeded")

    def services(self):
        # Refuse to touch anything already listening on these ports.
        for port in (5439, 8180, 8088, 9190):
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", port))
        prefix = subprocess.check_output(["brew", "--prefix", "postgresql@17"], text=True).strip()
        self.pg_bin = Path(prefix) / "bin"
        version = self.run([self.pg_bin / "postgres", "--version"], "postgres-version", 30)
        if not re.search(r"PostgreSQL\) 17\.", version):
            raise RuntimeError("PostgreSQL 17 is required")
        pwfile = self.private / "pg-password"
        pwfile.write_text(self.passwords["db"] + "\n")
        pwfile.chmod(0o600)
        data = self.private / "pg-data"
        self.run([self.pg_bin / "initdb", "-D", data, "--username=bliss_ci",
                  "--auth-local=trust", "--auth-host=scram-sha-256", f"--pwfile={pwfile}"], "postgres-init", 60)
        with (data / "postgresql.conf").open("a") as config:
            config.write(f"\nlisten_addresses = '127.0.0.1'\nport = 5439\nunix_socket_directories = '{self.private}'\n")
        self.run([self.pg_bin / "pg_ctl", "-D", data, "-l", self.private / "postgres.log", "-w", "start"],
                 "postgres-start", 60)
        self.pg_started = True
        db_env = dict(os.environ, PGPASSWORD=self.passwords["db"])
        self.run([self.pg_bin / "createdb", "-h", "127.0.0.1", "-p", "5439", "-U", "bliss_ci", "bliss"],
                 "database-create", 30, env=db_env)

        archive = self.private / "keycloak.tar.gz"
        url = f"https://github.com/keycloak/keycloak/releases/download/{KEYCLOAK}/keycloak-{KEYCLOAK}.tar.gz"
        print(f"Downloading pinned Keycloak {KEYCLOAK}", flush=True)
        with urllib.request.urlopen(url, timeout=120) as source, archive.open("wb") as target:
            shutil.copyfileobj(source, target)
        with archive.open("rb") as downloaded:
            if hashlib.file_digest(downloaded, "sha256").hexdigest() != KEYCLOAK_SHA256:
                raise RuntimeError("Keycloak archive SHA-256 mismatch")
        with tarfile.open(archive) as package:
            package.extractall(self.private, filter="data")
        keycloak = self.private / f"keycloak-{KEYCLOAK}"
        realm = json.loads((ROOT / "infrastructure/local/keycloak/bliss-realm.template.json").read_text())
        realm["users"] = []
        for index, role in enumerate(("customer", "provider"), start=1):
            realm["users"].append({
                "id": f"50000000-0000-0000-0000-{index:012d}", "username": role,
                "enabled": True, "emailVerified": True, "firstName": role, "lastName": "Demo",
                "email": f"{role}@example.test", "realmRoles": ["customer"] + (["provider"] if role == "provider" else []),
                "credentials": [{"type": "password", "value": self.passwords[role], "temporary": False}],
            })
        imports = keycloak / "data/import"
        imports.mkdir(parents=True)
        (imports / "bliss-realm.json").write_text(json.dumps(realm))
        kc_env = dict(os.environ, JAVA_OPTS_KC_HEAP="-Xms128m -Xmx768m")
        kc = self.start([keycloak / "bin/kc.sh", "start-dev", "--import-realm", "--http-host=127.0.0.1",
                         "--http-port=8180", "--hostname=http://localhost:8180", "--health-enabled=true",
                         "--http-management-host=127.0.0.1", "--http-management-port=9190"], "keycloak", kc_env)
        discovery = self.wait_url("http://localhost:8180/realms/bliss/.well-known/openid-configuration", kc)
        if discovery["issuer"] != "http://localhost:8180/realms/bliss":
            raise RuntimeError("Unexpected local issuer")
        print("Real local Keycloak discovery ready", flush=True)

        api_env = dict(os.environ, BLISS_DB_URL="jdbc:postgresql://localhost:5439/bliss", BLISS_DB_USER="bliss_ci",
                       BLISS_DB_PASSWORD=self.passwords["db"], BLISS_OIDC_URL=discovery["issuer"], BLISS_API_PORT="8088")
        # Flyway locations are fixed during Quarkus augmentation. Build and run
        # with the same local profile so the real demo migrations are included.
        self.run(["./mvnw", "-B", "-ntp", "package", "-DskipTests", "-Dquarkus.profile=local"],
                 "api-package", 600, ROOT / "services/api", api_env)
        api = self.start(["java", "-Dquarkus.profile=local", "-jar", ROOT / "services/api/target/quarkus-app/quarkus-run.jar"],
                         "api", api_env)
        readiness = self.wait_url("http://localhost:8088/q/health/ready", api)
        if readiness["status"] != "UP":
            raise RuntimeError("API did not become healthy")
        seeded = self.run([self.pg_bin / "psql", "-h", "127.0.0.1", "-p", "5439", "-U", "bliss_ci", "-d", "bliss",
                           "-At", "-c", "SELECT display_name FROM bliss_user WHERE oidc_subject = '50000000-0000-0000-0000-000000000002'"],
                          "provider-seed", 30, env=db_env).strip()
        if seeded != "Alex Demo":
            raise RuntimeError("Expected local demo provider seed is missing")
        (self.evidence / "services.txt").write_text(
            f"PostgreSQL 17, fresh cluster, loopback:5439\nKeycloak {KEYCLOAK}, fresh realm, loopback:8180\n"
            "Bliss packaged API, local profile, loopback:8088, readiness UP\n")
        print("Real Bliss API and fresh PostgreSQL ready", flush=True)

    def simulator(self):
        inventory = json.loads(self.run(["xcrun", "simctl", "list", "--json"], "simulator-inventory", 30))
        runtimes = [r for r in inventory["runtimes"] if r.get("isAvailable") and ".iOS-" in r["identifier"]]
        runtime = max(runtimes, key=lambda r: tuple(map(int, r["version"].split("."))))
        parts = [int(p) for p in runtime["version"].split(".")]
        parts += [0] * (3 - len(parts))
        version = (parts[0] << 16) | (parts[1] << 8) | parts[2]
        types = [d for d in inventory["devicetypes"] if d["name"].startswith("iPhone")
                 and d.get("minRuntimeVersion", 0) <= version <= d.get("maxRuntimeVersion", 0xFFFFFFFF)]
        device_type = types[-1]
        device = self.run(["xcrun", "simctl", "create", f"Bliss_Auth_iOS_{os.getpid()}",
                           device_type["identifier"], runtime["identifier"]], "simulator-create", 60).strip()
        if not re.fullmatch(r"[A-Fa-f0-9-]{36}", device):
            raise RuntimeError("Invalid newly created simulator UUID")
        self.device = device
        (self.evidence / "device.txt").write_text(f"{device}\n{device_type['name']}\n{runtime['version']}\n")
        self.run(["xcrun", "simctl", "boot", device], "simulator-boot", 60)
        self.run(["xcrun", "simctl", "bootstatus", device, "-b"], "simulator-ready", 180)

    def test(self):
        mobile = ROOT / "apps/mobile"
        self.run(["flutter", "pub", "get"], "flutter-pub", 300, mobile)
        self.run(["flutter", "build", "ios", "--config-only", "--simulator", "--debug", "--target", "lib/main.dart",
                  "--dart-define=OIDC_ISSUER=http://localhost:8180/realms/bliss",
                  "--dart-define=API_BASE_URL=http://localhost:8088"], "flutter-config", 120, mobile)
        env = dict(os.environ, TEST_RUNNER_BLISS_IOS_AUTH_OPT_IN="isolated-local",
                   TEST_RUNNER_BLISS_CUSTOMER_PASSWORD=self.passwords["customer"],
                   TEST_RUNNER_BLISS_PROVIDER_PASSWORD=self.passwords["provider"])
        log = self.run(["xcodebuild", "-workspace", "ios/Runner.xcworkspace", "-scheme", "BlissAuth",
                        "-configuration", "Debug", "-destination", f"platform=iOS Simulator,id={self.device}",
                        "-destination-timeout", "60", "-parallel-testing-enabled", "NO", "-only-testing:RunnerUITests",
                        "-resultBundlePath", self.private / "auth.xcresult", "test"], "xctest", 600, mobile, env)
        if not re.search(r"Test Case .*testRealBrowserOidcLifecycle.* passed", log):
            raise RuntimeError("Expected real browser XCTest did not pass")
        if "Executed 1 test, with 0 failures" not in log:
            raise RuntimeError("Expected exactly one successful browser XCTest")
        for checkpoint in CHECKPOINTS:
            if f"Verified iOS: {checkpoint}" not in log:
                raise RuntimeError(f"Missing acceptance checkpoint: {checkpoint}")
        exports = self.private / "attachments"
        self.run(["xcrun", "xcresulttool", "export", "attachments", "--path", self.private / "auth.xcresult",
                  "--output-path", exports], "screenshot-export", 60)
        manifest = json.loads((exports / "manifest.json").read_text())
        screenshots = self.evidence / "screenshots"
        screenshots.mkdir()
        for test in manifest:
            for attachment in test.get("attachments", []):
                suggested = attachment["suggestedHumanReadableName"]
                for name in SCREENSHOTS:
                    base = "bliss-ios-" + name
                    if not suggested.startswith((base + "_", base + ".")):
                        continue
                    source = (exports / attachment["exportedFileName"]).resolve()
                    if source.parent != exports.resolve() or source.suffix.lower() != ".png":
                        raise RuntimeError("Unexpected screenshot export path/type")
                    shutil.copyfile(source, screenshots / f"{base}.png")
        if len(list(screenshots.glob("*.png"))) != len(SCREENSHOTS):
            raise RuntimeError("Missing post-authentication screenshots")
        (self.evidence / "result.txt").write_text("PASS: real iOS Keycloak browser PKCE, callback, customer/provider "
                                                  "process recovery, background/resume, logout and account switching.\n")
        print((self.evidence / "result.txt").read_text(), flush=True)

    def cleanup(self):
        if self.device:
            self.run(["xcrun", "simctl", "shutdown", self.device], "simulator-shutdown", 60, check=False)
            self.run(["xcrun", "simctl", "delete", self.device], "simulator-delete", 60, check=False)
        for process, _ in reversed(self.processes):
            self.stop_group(process)
        for handle in self.handles:
            handle.close()
        if self.pg_started or (self.private / "pg-data/postmaster.pid").exists():
            self.run([self.pg_bin / "pg_ctl", "-D", self.private / "pg-data", "-m", "immediate", "-w", "stop"],
                     "postgres-stop", 60, check=False)
        for name in ("api", "keycloak", "postgres"):
            source = self.private / f"{name}.log"
            if source.exists():
                (self.evidence / f"{name}.log").write_text(self.scrub(source.read_text(errors="replace")))
        # Raw XCTest bundles may contain typed disposable credentials; never upload them.
        # This path was created by this invocation and must stay inside its private root.
        if self.private.resolve().parent != (ROOT / ".work/ios-oidc-private").resolve():
            raise RuntimeError("Refusing cleanup outside the private test root")
        shutil.rmtree(self.private)


def main():
    if platform.system() != "Darwin":
        sys.exit("This check requires macOS, Xcode, PostgreSQL 17 (Homebrew), Java 21 and Flutter.")
    for tool in ("brew", "java", "flutter", "xcrun", "xcodebuild"):
        if not shutil.which(tool):
            sys.exit(f"Missing required tool: {tool}")
    runner = Runner()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda number, _: sys.exit(128 + number))
    try:
        runner.run(["git", "rev-parse", "HEAD"], "commit", 30)
        runner.run(["xcodebuild", "-version"], "xcode-version", 30)
        runner.run(["flutter", "--version"], "flutter-version", 30)
        runner.services()
        runner.simulator()
        runner.test()
    finally:
        runner.cleanup()


if __name__ == "__main__":
    main()
