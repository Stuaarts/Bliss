#!/usr/bin/env python3
"""Check acceptance SQL with synthetic rows in an owned, networkless PostgreSQL.

This validates the evidence query, not the native UI or marketplace lifecycle.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import secrets
import signal
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
IMAGE = "postgres:17.6-alpine"
RUN_ID = "sql-acceptance-fixture"
CUSTOMER = "20000000-0000-0000-0000-000000000010"
ADMIN = "20000000-0000-0000-0000-000000000011"
OTHER = "20000000-0000-0000-0000-000000000012"
CANCELLED = "60000000-0000-0000-0000-000000000001"
DISPUTED = "60000000-0000-0000-0000-000000000002"
DISPUTE = "70000000-0000-0000-0000-000000000001"


def command(args, *, data=None, env=None, check=True):
    return subprocess.run(args, input=data, text=True, encoding="utf-8", capture_output=True,
                          timeout=90, env=env, check=check)


def fixture():
    return f"""
INSERT INTO bliss_user(id, oidc_subject, display_name) VALUES
('{CUSTOMER}', '50000000-0000-0000-0000-000000000001', 'customer'),
('{ADMIN}', '50000000-0000-0000-0000-000000000003', 'admin'),
('{OTHER}', 'unrelated-fixture-user', 'unrelated');
INSERT INTO user_role(user_id, role) VALUES ('{CUSTOMER}', 'CUSTOMER'), ('{ADMIN}', 'ADMIN');
INSERT INTO booking(id, customer_id, provider_id, title, description, city, price, currency,
                    status, workflow_instance_id, created_at, offering_id, offering_version, direct_key) VALUES
('{CANCELLED}', '{CUSTOMER}', '30000000-0000-0000-0000-000000000001', 'Garden refresh', 'Fixture garden',
 'Demo {RUN_ID} cancel', 65.50, 'CAD', 'CANCELLED', 'fixture-cancel-workflow', now(),
 '40000000-0000-0000-0000-000000000002', 0, gen_random_uuid()),
('{DISPUTED}', '{CUSTOMER}', '30000000-0000-0000-0000-000000000001', 'Garden refresh', 'Fixture garden',
 'Demo {RUN_ID} dispute', 65.50, 'CAD', 'CANCELLED', 'fixture-dispute-booking-workflow', now(),
 '40000000-0000-0000-0000-000000000002', 0, gen_random_uuid());
INSERT INTO payment(booking_id, amount, currency, status) VALUES
('{CANCELLED}',65.50,'CAD','VOIDED'), ('{DISPUTED}',65.50,'CAD','VOIDED');
INSERT INTO payment_transaction(id, booking_id, operation)
SELECT gen_random_uuid(), b.id, op FROM booking b CROSS JOIN (VALUES ('AUTHORIZE'), ('VOID')) t(op);
INSERT INTO dispute(id, booking_id, opened_by, reason, status, created_at, evidence_deadline,
                    resolved_at, workflow_instance_id) VALUES
('{DISPUTE}', '{DISPUTED}', '{CUSTOMER}', 'Local demo: unfinished garden cleanup for {RUN_ID}',
 'RESOLVED_CUSTOMER', now(), now() + interval '1 day', now(), 'fixture-dispute-workflow');
INSERT INTO dispute_evidence(id, dispute_id, actor_user_id, actor_role, client_key, body) VALUES
(gen_random_uuid(), '{DISPUTE}', '20000000-0000-0000-0000-000000000001', 'PROVIDER', gen_random_uuid(),
 'Provider demo response: cleanup details for {RUN_ID}');
INSERT INTO dispute_decision(dispute_id, actor_user_id, client_key, outcome, rationale, processed_at) VALUES
('{DISPUTE}', '{ADMIN}', gen_random_uuid(), 'CUSTOMER_FAVORED',
 'Local demo decision: customer favored after reviewing both accounts for {RUN_ID}', now());
"""


def cases():
    # Each mutation runs in a transaction that rolls back to the valid fixture.
    # Assertions must reject plausible incorrect data, not only malformed JSON.
    return [
        ("empty database", "TRUNCATE booking CASCADE;", {"exactly_two_bookings"}),
        ("missing booking", f"DELETE FROM payment_transaction WHERE booking_id='{CANCELLED}';"
         f"DELETE FROM payment WHERE booking_id='{CANCELLED}'; DELETE FROM booking WHERE id='{CANCELLED}';",
         {"exactly_two_bookings"}),
        ("wrong offering", f"UPDATE booking SET offering_id='40000000-0000-0000-0000-000000000001' WHERE id='{CANCELLED}';",
         {"direct_offering"}),
        ("wrong customer", f"UPDATE booking SET customer_id='{OTHER}' WHERE id='{CANCELLED}';", {"direct_offering"}),
        ("wrong provider", "INSERT INTO provider_profile(id,display_name,provider_type,description,city) "
         "VALUES ('30000000-0000-0000-0000-000000000002','Other','INDIVIDUAL','Fixture','Toronto');"
         f"UPDATE booking SET provider_id='30000000-0000-0000-0000-000000000002' WHERE id='{CANCELLED}';",
         {"direct_offering"}),
        ("wrong booking status", f"UPDATE booking SET status='COMPLETED' WHERE id='{CANCELLED}';", {"cancelled"}),
        ("wrong amount", f"UPDATE payment SET amount=66.50 WHERE booking_id='{CANCELLED}';", {"simulated_void"}),
        ("wrong currency", f"UPDATE payment SET currency='USD' WHERE booking_id='{CANCELLED}';", {"simulated_void"}),
        ("captured payment", f"UPDATE payment SET status='CAPTURED' WHERE booking_id='{CANCELLED}';", {"simulated_void"}),
        ("extra capture", f"INSERT INTO payment_transaction(id,booking_id,operation) VALUES(gen_random_uuid(),'{CANCELLED}','CAPTURE');",
         {"exact_authorize_and_void"}),
        ("missing void", f"DELETE FROM payment_transaction WHERE booking_id='{CANCELLED}' AND operation='VOID';",
         {"exact_authorize_and_void"}),
        ("missing payment", f"DELETE FROM payment_transaction WHERE booking_id='{CANCELLED}';"
         f"DELETE FROM payment WHERE booking_id='{CANCELLED}';", {"simulated_void"}),
        ("missing dispute", "TRUNCATE dispute CASCADE;", {"one_customer_dispute"}),
        ("wrong dispute opener", f"UPDATE dispute SET opened_by='{OTHER}';", {"one_customer_dispute"}),
        ("opener differs from booking customer", f"UPDATE booking SET customer_id='{OTHER}' WHERE id='{DISPUTED}';",
         {"one_customer_dispute"}),
        ("pending dispute", "UPDATE dispute SET status='UNDER_REVIEW',resolved_at=NULL;", {"one_customer_dispute"}),
        ("wrong reason", "UPDATE dispute SET reason='unrelated case';", {"one_customer_dispute"}),
        ("missing evidence", "DELETE FROM dispute_evidence;", {"one_provider_evidence"}),
        ("wrong evidence actor", f"UPDATE dispute_evidence SET actor_user_id='{CUSTOMER}';", {"one_provider_evidence"}),
        ("wrong evidence body", "UPDATE dispute_evidence SET body='unrelated evidence';", {"one_provider_evidence"}),
        ("missing decision", "DELETE FROM dispute_decision;", {"independent_admin_applied_decision"}),
        ("customer decided", f"UPDATE dispute_decision SET actor_user_id='{CUSTOMER}';", {"independent_admin_applied_decision"}),
        ("provider decided", "UPDATE dispute_decision SET actor_user_id='20000000-0000-0000-0000-000000000001';",
         {"independent_admin_applied_decision"}),
        ("unapplied decision", "UPDATE dispute_decision SET processed_at=NULL;", {"independent_admin_applied_decision"}),
        ("wrong outcome", "UPDATE dispute_decision SET outcome='PROVIDER_FAVORED';", {"independent_admin_applied_decision"}),
        ("wrong rationale", "UPDATE dispute_decision SET rationale='unrelated rationale';", {"independent_admin_applied_decision"}),
        ("dispute on cancelled booking", f"INSERT INTO dispute(id,booking_id,opened_by,reason,status,created_at,evidence_deadline) "
         f"VALUES(gen_random_uuid(),'{CANCELLED}','{CUSTOMER}','unexpected dispute','UNDER_REVIEW',now(),now()+interval '1 day');",
         {"cancelled_booking_has_no_dispute"}),
    ]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--isolated-docker", action="store_true", required=True,
                        help="Opt in to a disposable container; no host ports or persistent volumes")
    parser.parse_args()
    if command(["docker", "info", "--format", "{{.OSType}}"]).stdout.strip() != "linux":
        raise RuntimeError("This check requires a ready Linux Docker engine")
    spec = importlib.util.spec_from_file_location("ios_acceptance", ROOT / "infrastructure/scripts/test-ios-oidc.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    owner = secrets.token_hex(12)
    container = "bliss-ios-sql-" + owner
    env = dict(os.environ, POSTGRES_PASSWORD=secrets.token_hex(24))
    signal.signal(signal.SIGTERM, lambda number, _: sys.exit(128 + number))
    try:
        command(["docker", "run", "--detach", "--rm", "--name", container,
                 "--label", "bliss.ios-sql-owner=" + owner, "--network", "none",
                 "--tmpfs", "/var/lib/postgresql/data:rw", "--env", "POSTGRES_PASSWORD",
                 "--env", "POSTGRES_DB=ios_sql", IMAGE], env=env)
        for _ in range(60):
            # The image starts a temporary server during initialization. Wait
            # for the final PID 1 postgres process, then require a real query.
            process = command(["docker", "exec", container, "cat", "/proc/1/comm"], check=False)
            if process.stdout.strip() == "postgres":
                ready = command(["docker", "exec", container, "psql", "-X", "-qAt", "-U", "postgres",
                                 "-d", "ios_sql", "-c", "SELECT 1"], check=False)
                if ready.returncode == 0 and ready.stdout.strip() == "1":
                    break
            time.sleep(0.5)
        else:
            raise RuntimeError("Disposable PostgreSQL readiness deadline exceeded")

        def sql(text):
            return command(["docker", "exec", "-i", container, "psql", "-X", "-qAt",
                            "--set", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "ios_sql"], data=text).stdout.strip()

        for path in sorted((ROOT / "services/api/src/main/resources/db/migration").glob("V*.sql"),
                           key=lambda path: int(path.name.split("__")[0][1:])):
            sql(path.read_text(encoding="utf-8"))
        sql((ROOT / "services/api/src/main/resources/db/demo/R__local_demo_catalog.sql").read_text(encoding="utf-8"))
        sql(fixture())
        runner = module.Runner.__new__(module.Runner)
        runner.run_id = RUN_ID
        runner.pg_bin = Path("unused-by-docker-adapter")
        runner.passwords = {"db": env["POSTGRES_PASSWORD"]}
        for name, mutation, expected_bad in [("valid fixture", "", set())] + cases():
            proof = {}

            def query(args, *_args, **_kwargs):
                nonlocal proof
                output = sql("BEGIN;" + mutation + args[-1] + ";ROLLBACK;")
                proof = json.loads(output)
                return output

            runner.run = query
            rejected = False
            try:
                runner.verify_secondary()
            except RuntimeError:
                rejected = True
            bad = {key for key, value in proof.items() if value is not True}
            if rejected != bool(expected_bad) or not expected_bad.issubset(bad):
                raise AssertionError(f"{name}: expected rejection keys {sorted(expected_bad)}, got {sorted(bad)}")
            print(f"PASS: {name}", flush=True)
        print(f"PASS: {len(cases()) + 1} real PostgreSQL evidence-query cases; synthetic fixtures only.", flush=True)
    finally:
        inspected = command(["docker", "inspect", "--format", '{{index .Config.Labels "bliss.ios-sql-owner"}}', container], check=False)
        if inspected.returncode == 0:
            if inspected.stdout.strip() != owner:
                raise RuntimeError("Refusing cleanup of a container not owned by this check")
            command(["docker", "rm", "--force", container])


if __name__ == "__main__":
    main()
