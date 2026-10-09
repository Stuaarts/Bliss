#!/usr/bin/env python3
"""Real browser OIDC acceptance against a disposable, loopback-only Mac stack."""
import hashlib
import argparse
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
import threading
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
MARKETPLACE_CHECKPOINTS = [
    "marketplace customer request created", "marketplace provider quote submitted",
    "marketplace quote accepted and customer message sent", "marketplace two-way chat delivered",
    "marketplace scheduled work completed by provider", "marketplace customer confirmation and simulated capture",
    "marketplace restored booking chat and saved review", "marketplace review survives background and process restart",
    "marketplace lifecycle finished signed out",
]
MARKETPLACE_SCREENSHOTS = ["marketplace-" + name for name in [
    "request", "quote", "customer-chat", "chat", "provider-complete", "completed", "review", "restored", "signed-out",
]]


SECONDARY_CHECKPOINTS = [
    "secondary cancellation and simulated void survive process restart",
    "secondary direct booking created", "secondary provider completed scheduled work",
    "secondary customer opened dispute without decision controls",
    "secondary provider evidence awaits independent admin",
    "secondary independent admin applied customer favored decision",
    "secondary dispute settlement voided simulated authorization",
    "secondary decision survives background and process restart", "secondary lifecycle finished signed out",
]
SECONDARY_SCREENSHOTS = ["secondary-" + name for name in [
    "cancelled", "created", "provider-complete", "opened", "evidence", "resolved", "voided", "restored", "signed-out",
]]
CONNECTIVITY_CHECKPOINTS = ["connectivity catalog shows connection failure",
                          "connectivity same screen retry loads real catalog"]
CONNECTIVITY_SCREENSHOTS = ["connectivity-unavailable", "connectivity-recovered"]
SCENARIOS = {
    "auth": ("NativeOidcTests", "testRealBrowserOidcLifecycle", CHECKPOINTS, SCREENSHOTS),
    "marketplace": ("NativeMarketplaceTests", "testRequestToReviewLifecycle", MARKETPLACE_CHECKPOINTS, MARKETPLACE_SCREENSHOTS),
    "secondary": ("NativeSecondaryTests", "testDirectCancellationAndManualDispute", SECONDARY_CHECKPOINTS, SECONDARY_SCREENSHOTS),
    "connectivity": ("NativeConnectivityTests", "testCatalogConnectionRecovery", CONNECTIVITY_CHECKPOINTS, CONNECTIVITY_SCREENSHOTS),
}
SECONDARY_PROOF_KEYS = {
    "exactly_two_bookings", "direct_offering", "cancelled", "simulated_void",
    "exact_authorize_and_void", "one_customer_dispute", "one_provider_evidence",
    "independent_admin_applied_decision", "cancelled_booking_has_no_dispute",
}


class Runner:
    def __init__(self, scenario="auth"):
        self.scenario = scenario
        self.run_id = secrets.token_hex(6)
        evidence_root = ROOT / (".work/ios-oidc" if scenario == "auth" else f".work/ios-{scenario}")
        private_root = ROOT / ".work/ios-oidc-private"
        evidence_root.mkdir(parents=True, exist_ok=True)
        private_root.mkdir(parents=True, exist_ok=True)
        self.evidence = Path(tempfile.mkdtemp(prefix="run-", dir=evidence_root))
        self.private = Path(tempfile.mkdtemp(prefix="run-", dir=private_root))
        self.private.chmod(0o700)
        self.passwords = {role: secrets.token_hex(18) for role in ("customer", "provider", "admin", "db")}
        self.processes = []
        self.handles = []
        self.device = None
        self.pg_bin = None
        self.pg_started = False
        self.network = None
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
        roles = ("customer", "provider", "admin") if self.scenario == "secondary" else ("customer", "provider")
        for index, role in enumerate(roles, start=1):
            realm["users"].append({
                "id": f"50000000-0000-0000-0000-{index:012d}", "username": role,
                "enabled": True, "emailVerified": True, "firstName": role, "lastName": "Demo",
                "email": f"{role}@example.test", "realmRoles": ["customer"] + ([role] if role in ("provider", "admin") else []),
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
        marketplace = self.scenario == "marketplace"
        test_class, test_name, checkpoints, expected_screenshots = SCENARIOS[self.scenario]
        mobile = ROOT / "apps/mobile"
        self.run(["flutter", "pub", "get"], "flutter-pub", 300, mobile)
        api_url = "http://localhost:8088"
        if self.scenario == "connectivity":
            from ios_network_fixture import NetworkFixture
            self.network = NetworkFixture()
            self.passwords["network"] = self.network.secret
            if os.environ.get("GITHUB_ACTIONS") == "true":
                print(f"::add-mask::{self.network.secret}", flush=True)
            self.network.start()
            api_url = self.network.api_url
        self.run(["flutter", "build", "ios", "--config-only", "--simulator", "--debug", "--target", "lib/main.dart",
                  "--dart-define=OIDC_ISSUER=http://localhost:8180/realms/bliss",
                  f"--dart-define=API_BASE_URL={api_url}"], "flutter-config", 120, mobile)
        env = dict(os.environ, TEST_RUNNER_BLISS_IOS_AUTH_OPT_IN="isolated-local",
                   TEST_RUNNER_BLISS_CUSTOMER_PASSWORD=self.passwords["customer"],
                   TEST_RUNNER_BLISS_PROVIDER_PASSWORD=self.passwords["provider"],
                   TEST_RUNNER_BLISS_ADMIN_PASSWORD=self.passwords["admin"],
                   TEST_RUNNER_BLISS_IOS_MARKETPLACE_RUN_ID=self.run_id)
        if self.network:
            env.update(TEST_RUNNER_BLISS_IOS_NETWORK_CONTROL_URL=self.network.control_url,
                       TEST_RUNNER_BLISS_IOS_NETWORK_SECRET=self.network.secret)
        (self.evidence / "scenario.txt").write_text(f"{self.scenario}\n{self.run_id}\n{test_class}/{test_name}\n")
        stop = threading.Event()
        self.workflow_checkpoint = None
        self.workflow_error = None
        observer = threading.Thread(target=self.observe_workflow, args=(stop,)) if marketplace else None
        if observer:
            observer.start()
        try:
            log = self.run(["xcodebuild", "-workspace", "ios/Runner.xcworkspace", "-scheme", "BlissAuth",
                            "-configuration", "Debug", "-destination", f"platform=iOS Simulator,id={self.device}",
                            "-destination-timeout", "60", "-parallel-testing-enabled", "NO",
                            f"-only-testing:RunnerUITests/{test_class}/{test_name}",
                            "-resultBundlePath", self.private / "auth.xcresult", "test"], "xctest", 900 if self.scenario in ("marketplace", "secondary") else 600, mobile, env)
        finally:
            stop.set()
            if observer:
                observer.join(timeout=10)
                if observer.is_alive():
                    raise RuntimeError("Workflow observer did not stop")
        if not re.search(r"Test Case .*" + test_name + r".* passed", log):
            raise RuntimeError("Expected native XCTest did not pass")
        if "Executed 1 test, with 0 failures" not in log:
            raise RuntimeError("Expected exactly one successful browser XCTest")
        for checkpoint in checkpoints:
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
                for name in expected_screenshots:
                    base = "bliss-ios-" + name
                    if not suggested.startswith((base + "_", base + ".")):
                        continue
                    source = (exports / attachment["exportedFileName"]).resolve()
                    if source.parent != exports.resolve() or source.suffix.lower() != ".png":
                        raise RuntimeError("Unexpected screenshot export path/type")
                    shutil.copyfile(source, screenshots / f"{base}.png")
        if len(list(screenshots.glob("*.png"))) != len(expected_screenshots):
            raise RuntimeError("Missing post-authentication screenshots")
        if marketplace:
            self.verify_marketplace()
        if self.scenario == "secondary":
            self.verify_secondary()
        result = ("PASS: real iOS request, quote, booking, two-way chat, scheduled work, simulated capture, "
                  "process recovery, saved review and logout.\n" if marketplace else
                  "PASS: real iOS Keycloak browser PKCE, callback, customer/provider "
                  "process recovery, background/resume, logout and account switching.\n")
        if self.scenario == "secondary":
            result = "PASS: real iOS direct cancellation, manual customer/provider/admin dispute, simulated void, process recovery and logout.\n"
        elif self.scenario == "connectivity":
            result = "PASS: real iOS catalog connection failure and same-screen retry against the packaged API.\n"
        (self.evidence / "result.txt").write_text(result)
        print((self.evidence / "result.txt").read_text(), flush=True)

    def verify_marketplace(self):
        if self.workflow_error:
            raise RuntimeError(f"Workflow observer failed: {self.workflow_error}")
        if not self.workflow_checkpoint:
            raise RuntimeError("No persisted workflow checkpoint observed during scheduled human wait")
        # Read-only corroboration of the records created through the native UI.
        # The title suffix is generated here, never read from untrusted SQL input.
        sql = f"""SELECT json_build_object(
            'single_booking', count(*) = 1,
            'completed', bool_and(b.status = 'COMPLETED'),
            'agreed_price', bool_and(b.price = 85.50 AND b.currency = 'CAD'),
            'accepted_request_quote', bool_and(r.status = 'ACCEPTED' AND q.status = 'ACCEPTED'),
            'persisted_workflow', bool_and(b.id = '{self.workflow_checkpoint['booking_id']}'::uuid
                AND b.workflow_instance_id = '{self.workflow_checkpoint['workflow_instance_id']}'),
            'simulated_capture', bool_and(p.status = 'CAPTURED' AND p.simulated AND p.amount = 85.50),
            'single_capture', bool_and((SELECT count(*) FROM payment_transaction t
                WHERE t.booking_id = b.id AND t.operation = 'CAPTURE') = 1),
            'saved_review', bool_and((SELECT count(*) FROM booking_review v WHERE v.booking_id = b.id
                AND v.rating = 5 AND v.comment = 'iOS demo: careful garden work and clear communication.') = 1),
            'two_way_chat', bool_and((SELECT count(DISTINCT m.sender_id) FROM conversation c
                JOIN conversation_message m ON m.conversation_id = c.id WHERE c.booking_id = b.id
                AND m.body IN ('Customer ready for the garden visit {self.run_id}',
                               'Provider confirmed the garden visit {self.run_id}')) = 2)
            ) FROM booking b JOIN service_request r ON r.id = b.request_id
            JOIN quote q ON q.id = b.quote_id JOIN payment p ON p.booking_id = b.id
            WHERE b.title = 'iOS garden {self.run_id}'"""
        proof = self.run([self.pg_bin / "psql", "-h", "127.0.0.1", "-p", "5439", "-U", "bliss_ci",
                          "-d", "bliss", "-At", "-c", sql], "marketplace-database", 30,
                         env=dict(os.environ, PGPASSWORD=self.passwords["db"]))
        if not all(value is True for value in json.loads(proof).values()):
            raise RuntimeError("Native marketplace database corroboration failed")

    def verify_secondary(self):
        # These records must have been created by the native app UI. This query
        # only reads the fresh database and never advances a business state.
        sql = f"""WITH target AS (
            SELECT b.*, p.status AS payment_status, p.simulated, p.amount, p.currency AS payment_currency
            FROM booking b LEFT JOIN payment p ON p.booking_id = b.id
            WHERE b.city IN ('Demo {self.run_id} cancel', 'Demo {self.run_id} dispute')
        ) SELECT json_build_object(
            'exactly_two_bookings', (SELECT count(*) = 2 AND count(DISTINCT city) = 2 FROM target),
            'direct_offering', (SELECT bool_and(offering_id = '40000000-0000-0000-0000-000000000002'::uuid
                AND request_id IS NULL AND quote_id IS NULL AND offering_version IS NOT NULL) FROM target),
            'cancelled', (SELECT bool_and(status = 'CANCELLED') FROM target),
            'simulated_void', (SELECT bool_and(payment_status = 'VOIDED' AND simulated AND amount = 65.50
                AND price = 65.50 AND currency = 'CAD' AND payment_currency = 'CAD') FROM target),
            'exact_authorize_and_void', (SELECT bool_and(
                (SELECT count(*) FROM payment_transaction t WHERE t.booking_id = b.id) = 2 AND
                (SELECT count(*) FROM payment_transaction t WHERE t.booking_id = b.id AND t.operation = 'AUTHORIZE') = 1 AND
                (SELECT count(*) FROM payment_transaction t WHERE t.booking_id = b.id AND t.operation = 'VOID') = 1) FROM target b),
            'one_customer_dispute', (SELECT count(*) = 1 AND bool_and(d.status = 'RESOLVED_CUSTOMER'
                AND d.resolved_at IS NOT NULL AND d.workflow_instance_id IS NOT NULL
                AND d.reason = 'Local demo: unfinished garden cleanup for {self.run_id}'
                AND u.oidc_subject = '50000000-0000-0000-0000-000000000001')
                FROM dispute d JOIN target b ON b.id = d.booking_id JOIN bliss_user u ON u.id = d.opened_by
                WHERE b.city = 'Demo {self.run_id} dispute'),
            'one_provider_evidence', (SELECT count(*) = 1 AND bool_and(e.actor_role = 'PROVIDER'
                AND e.body = 'Provider demo response: cleanup details for {self.run_id}'
                AND u.oidc_subject = '50000000-0000-0000-0000-000000000002')
                FROM dispute_evidence e JOIN dispute d ON d.id = e.dispute_id JOIN target b ON b.id = d.booking_id
                JOIN bliss_user u ON u.id = e.actor_user_id WHERE b.city = 'Demo {self.run_id} dispute'),
            'independent_admin_applied_decision', (SELECT count(*) = 1 AND bool_and(a.processed_at IS NOT NULL
                AND a.outcome = 'CUSTOMER_FAVORED' AND a.actor_user_id <> b.customer_id
                AND a.actor_user_id <> d.opened_by AND u.oidc_subject = '50000000-0000-0000-0000-000000000003'
                AND a.rationale = 'Local demo decision: customer favored after reviewing both accounts for {self.run_id}')
                FROM dispute_decision a JOIN dispute d ON d.id = a.dispute_id JOIN target b ON b.id = d.booking_id
                JOIN bliss_user u ON u.id = a.actor_user_id WHERE b.city = 'Demo {self.run_id} dispute'),
            'cancelled_booking_has_no_dispute', NOT EXISTS(SELECT 1 FROM dispute d JOIN target b ON b.id = d.booking_id
                WHERE b.city = 'Demo {self.run_id} cancel')
        )"""
        proof = self.run([self.pg_bin / "psql", "-h", "127.0.0.1", "-p", "5439", "-U", "bliss_ci",
                          "-d", "bliss", "-At", "-c", sql], "secondary-database", 30,
                         env=dict(os.environ, PGPASSWORD=self.passwords["db"]))
        assertions = json.loads(proof)
        if set(assertions) != SECONDARY_PROOF_KEYS or not all(value is True for value in assertions.values()):
            raise RuntimeError("Native secondary database corroboration failed")

    def observe_workflow(self, stop):
        # Flow removes active instance/task rows after completion. Observe the
        # real persisted human wait, then require the same correlation at finish.
        sql = f"""SELECT json_build_object('booking_id', b.id,
            'workflow_instance_id', b.workflow_instance_id, 'status', b.status,
            'checkpoint_count', (SELECT count(*) FROM task_info_entity t
                WHERE t.application_id = 'bliss-api' AND t.workflow_instance_id = b.workflow_instance_id))
            FROM booking b JOIN workflow_instance_entity w
                ON w.instance_id = b.workflow_instance_id AND w.application_id = 'bliss-api'
            WHERE b.title = 'iOS garden {self.run_id}' AND b.status = 'SCHEDULED'
                AND (SELECT count(*) FROM task_info_entity t WHERE t.application_id = 'bliss-api'
                    AND t.workflow_instance_id = b.workflow_instance_id) = 3"""
        try:
            while not stop.is_set():
                result = subprocess.run([str(self.pg_bin / "psql"), "-h", "127.0.0.1", "-p", "5439",
                                         "-U", "bliss_ci", "-d", "bliss", "-At", "-c", sql],
                                        env=dict(os.environ, PGPASSWORD=self.passwords["db"]),
                                        capture_output=True, text=True, timeout=5, check=True)
                if result.stdout.strip():
                    checkpoint = json.loads(result.stdout)
                    # Bound SQL interpolation below to the persisted UUIDs this
                    # application generates, even though this DB is disposable.
                    if not re.fullmatch(r"[a-fA-F0-9-]{36}", checkpoint["booking_id"]) or not re.fullmatch(
                            r"[A-Za-z0-9_-]{1,255}", checkpoint["workflow_instance_id"]):
                        raise RuntimeError("Unexpected workflow correlation identifier")
                    self.workflow_checkpoint = checkpoint
                    (self.evidence / "workflow-wait.json").write_text(json.dumps(checkpoint, indent=2))
                    print("Verified persisted scheduled workflow with three task checkpoints", flush=True)
                    return
                stop.wait(1)
        except Exception as error:
            self.workflow_error = self.scrub(str(error))

    def cleanup(self):
        if self.network:
            self.network.close()
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario", choices=tuple(SCENARIOS), default="auth")
    args = parser.parse_args()
    if platform.system() != "Darwin":
        sys.exit("This check requires macOS, Xcode, PostgreSQL 17 (Homebrew), Java 21 and Flutter.")
    for tool in ("brew", "java", "flutter", "xcrun", "xcodebuild"):
        if not shutil.which(tool):
            sys.exit(f"Missing required tool: {tool}")
    runner = Runner(args.scenario)
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
