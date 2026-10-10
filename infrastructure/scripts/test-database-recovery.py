#!/usr/bin/env python3
"""Rehearse logical backup/restore using owned, networkless synthetic databases."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
IMAGE = "postgres:17.6-alpine"
LABEL = "bliss.recovery-owner"


class Drill:
    def __init__(self):
        self.owner = secrets.token_hex(12)
        self.containers = []
        evidence_root = ROOT / ".work/database-recovery"
        private_root = ROOT / ".work/database-recovery-private"
        for directory in (evidence_root, private_root):
            directory.mkdir(parents=True, exist_ok=True)
        self.evidence = Path(tempfile.mkdtemp(prefix="run-", dir=evidence_root))
        self.private = Path(tempfile.mkdtemp(prefix="run-", dir=private_root))
        self.private.chmod(0o700)
        self.archive = self.private / "bliss.dump"
        self.proof = {"scope": "synthetic PostgreSQL logical backup; no API or identity-provider recovery",
                      "image": IMAGE, "checks": {}, "timings_seconds": {}}

    @staticmethod
    def command(args, **kwargs):
        return subprocess.run(args, capture_output=True, timeout=90, check=True, **kwargs)

    def sql(self, container, statement, check=True):
        result = subprocess.run(["docker", "exec", "-i", container, "psql", "-X", "-qAt", "--set",
                                 "ON_ERROR_STOP=1", "-U", "postgres", "-d", "bliss"],
                                input=statement, text=True, encoding="utf-8", capture_output=True,
                                timeout=30, check=check)
        return result

    def create(self, role):
        name = f"bliss-recovery-{self.owner}-{role}"
        self.containers.append(name)  # Track before launch so timeout cleanup can find it.
        env = dict(os.environ, POSTGRES_PASSWORD=secrets.token_hex(24))
        self.command(["docker", "run", "--detach", "--rm", "--name", name,
                      "--label", f"{LABEL}={self.owner}", "--network", "none",
                      "--tmpfs", "/var/lib/postgresql/data:rw", "--env", "POSTGRES_PASSWORD",
                      "--env", "POSTGRES_DB=bliss", IMAGE], env=env)
        for _ in range(60):
            process = self.command(["docker", "exec", name, "cat", "/proc/1/comm"])
            if process.stdout.strip() == b"postgres" and self.sql(name, "SELECT 1", check=False).returncode == 0:
                return name
            time.sleep(0.5)
        raise RuntimeError("Disposable PostgreSQL readiness deadline exceeded")

    def snapshot(self, container):
        tables = self.sql(container, "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename").stdout.splitlines()
        rows = {}
        for table in tables:
            if not re.fullmatch(r"[a-z_][a-z_0-9]*", table):
                raise RuntimeError("Unexpected drill table identifier")
            data = self.sql(container, f'SELECT row_to_json(t)::text FROM public."{table}" t ORDER BY row_to_json(t)::text').stdout
            rows[table] = {"rows": len(data.splitlines()), "sha256": hashlib.sha256(data.encode("utf-8")).hexdigest()}
        # Restoring reparses CHECK expressions and can move equivalent varchar
        # array casts. Compare their names/keys/validation metadata rather than
        # deparser text; exercise representative constraints with invalid writes.
        schema = self.sql(container, """SELECT json_build_object(
            'columns', (SELECT json_agg(row_to_json(c) ORDER BY table_name,ordinal_position) FROM (
                SELECT table_name,column_name,ordinal_position,data_type,udt_name,is_nullable,column_default
                FROM information_schema.columns WHERE table_schema='public') c),
            'constraints', (SELECT json_agg(row_to_json(c) ORDER BY table_name,conname) FROM (
                SELECT r.relname AS table_name,c.conname,c.contype,c.convalidated,c.condeferrable,c.condeferred,c.conkey,
                    CASE WHEN c.contype <> 'c' THEN pg_get_constraintdef(c.oid) END AS definition
                FROM pg_constraint c JOIN pg_class r ON r.oid=c.conrelid JOIN pg_namespace n ON n.oid=r.relnamespace
                WHERE n.nspname='public') c),
            'indexes', (SELECT json_agg(row_to_json(i) ORDER BY tablename,indexname) FROM (
                SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public') i))""").stdout
        catalog = json.loads(schema)
        canonical = json.dumps(catalog, sort_keys=True, separators=(",", ":"))
        return {"tables": rows, "schema": catalog, "schema_sha256": hashlib.sha256(canonical.encode("utf-8")).hexdigest()}

    def verified_restore(self, container, archive, expected_sha256):
        # Only an owned empty target can receive even a trusted drill archive.
        owned = self.command(["docker", "inspect", "--format", '{{index .Config.Labels "' + LABEL + '"}}', container]).stdout.decode().strip()
        if owned != self.owner:
            raise RuntimeError("Restore destination is not owned by this drill")
        if self.sql(container, "SELECT count(*) FROM pg_tables WHERE schemaname='public'").stdout.strip() != "0":
            raise RuntimeError("Restore destination is not empty")
        if hashlib.sha256(archive.read_bytes()).hexdigest() != expected_sha256:
            raise RuntimeError("Backup SHA-256 mismatch")
        with archive.open("rb") as source:
            return self.command(["docker", "exec", "-i", container, "pg_restore", "--exit-on-error",
                                 "--single-transaction", "--no-owner", "--no-privileges", "-U", "postgres",
                                 "--dbname=bliss"], stdin=source)

    def check(self, name, passed):
        self.proof["checks"][name] = bool(passed)
        if not passed:
            raise RuntimeError(f"Recovery check failed: {name}")
        print(f"PASS: {name}", flush=True)

    def run(self):
        if self.command(["docker", "info", "--format", "{{.OSType}}"]).stdout.strip() != b"linux":
            raise RuntimeError("A ready Linux Docker engine is required")
        self.proof["commit"] = self.command(["git", "rev-parse", "HEAD"]).stdout.decode().strip()
        self.proof["working_tree_dirty"] = bool(self.command(["git", "status", "--porcelain"]).stdout.strip())
        source = self.create("source")
        migrations = sorted((ROOT / "services/api/src/main/resources/db/migration").glob("V*.sql"),
                            key=lambda path: int(path.name.split("__")[0][1:]))
        for migration in migrations:
            self.sql(source, migration.read_text(encoding="utf-8"))
        self.proof["migrations"] = [path.name for path in migrations]
        self.sql(source, (ROOT / "services/api/src/main/resources/db/demo/R__local_demo_catalog.sql").read_text(encoding="utf-8"))
        self.sql(source, (ROOT / "infrastructure/scripts/fixtures/database-recovery.sql").read_text(encoding="utf-8"))
        baseline = self.snapshot(source)
        required = {"booking": 4, "payment": 4, "payment_transaction": 5, "conversation_message": 2,
                    "booking_review": 1, "dispute": 1, "dispute_evidence": 2, "dispute_decision": 1,
                    "workflow_instance_entity": 3, "task_info_entity": 3, "cloud_event_entity": 1}
        self.check("fixture_contains_marketplace_and_workflow_records",
                   all(baseline["tables"][name]["rows"] == count for name, count in required.items()))
        started = time.monotonic()
        with self.archive.open("wb") as target:
            dumped = subprocess.run(["docker", "exec", source, "pg_dump", "--format=custom", "--no-privileges",
                            "-U", "postgres", "--dbname=bliss"], stdout=target, stderr=subprocess.PIPE,
                           timeout=90, check=True)
            if dumped.stderr:
                raise RuntimeError("pg_dump emitted diagnostics; backup was not accepted")
        self.proof["timings_seconds"]["dump"] = round(time.monotonic() - started, 3)
        digest = hashlib.sha256(self.archive.read_bytes()).hexdigest()
        self.proof.update(archive_sha256=digest, archive_bytes=self.archive.stat().st_size)
        self.sql(source, "INSERT INTO recovery_drill_marker VALUES(2,'after backup snapshot')")
        target = self.create("target")
        corrupted = self.private / "corrupt.dump"
        corrupted.write_bytes(self.archive.read_bytes()[:128])
        try:
            self.verified_restore(target, corrupted, digest)
        except RuntimeError as error:
            self.check("checksum_mismatch_rejected", str(error) == "Backup SHA-256 mismatch")
        else:
            self.check("checksum_mismatch_rejected", False)
        try:
            self.verified_restore(target, corrupted, hashlib.sha256(corrupted.read_bytes()).hexdigest())
        except subprocess.CalledProcessError:
            self.check("truncated_archive_rejected", True)
        else:
            self.check("truncated_archive_rejected", False)
        self.check("failed_restore_left_target_empty",
                   self.sql(target, "SELECT count(*) FROM pg_tables WHERE schemaname='public'").stdout.strip() == "0")
        started = time.monotonic()
        self.verified_restore(target, self.archive, digest)
        self.proof["timings_seconds"]["restore"] = round(time.monotonic() - started, 3)
        restored = self.snapshot(target)
        self.proof["snapshot"] = baseline
        if restored != baseline:
            self.proof["mismatched_restore_snapshot"] = restored
        self.check("all_rows_columns_indexes_and_constraint_metadata_match", restored == baseline)
        self.check("post_backup_write_absent", self.sql(target, "SELECT count(*) FROM recovery_drill_marker").stdout.strip() == "1")
        self.check("source_retained_post_backup_write", self.sql(source, "SELECT count(*) FROM recovery_drill_marker").stdout.strip() == "2")
        try:
            self.verified_restore(target, self.archive, digest)
        except RuntimeError as error:
            self.check("nonempty_target_rejected", str(error) == "Restore destination is not empty")
        else:
            self.check("nonempty_target_rejected", False)
        invalid = [
            ("restored_money_constraint_enforced", "UPDATE payment SET amount='NaN'", "ck_payment_amount_finite"),
            ("restored_booking_status_constraint_enforced", "UPDATE booking SET status='INVALID'", "ck_booking_status"),
            ("restored_review_rating_constraint_enforced", "UPDATE booking_review SET rating=6", "booking_review_rating_check"),
            ("restored_dispute_outcome_constraint_enforced", "UPDATE dispute_decision SET outcome='INVALID'", "dispute_decision_outcome_check"),
            ("restored_payment_uniqueness_enforced", "INSERT INTO payment_transaction(id,booking_id,operation) "
             "SELECT gen_random_uuid(),booking_id,operation FROM payment_transaction LIMIT 1", "payment_transaction_booking_id_operation_key"),
            ("restored_chat_participant_fk_enforced", "UPDATE conversation_message SET sender_id='ffffffff-ffff-ffff-ffff-ffffffffffff'",
             "conversation_message_conversation_id_sender_id_fkey"),
        ]
        for name, mutation, constraint in invalid:
            rejected = self.sql(target, "BEGIN;" + mutation + ";COMMIT;", check=False)
            self.check(name, rejected.returncode != 0 and constraint in rejected.stderr)
        self.check("failed_mutation_preserved_restored_snapshot", self.snapshot(target) == baseline)
        self.proof["snapshot"] = baseline

    def cleanup(self):
        errors = []
        for container in reversed(self.containers):
            try:
                result = subprocess.run(["docker", "inspect", "--format", '{{index .Config.Labels "' + LABEL + '"}}', container],
                                        capture_output=True, timeout=30, check=False)
                if result.returncode == 0:
                    if result.stdout.decode().strip() != self.owner:
                        raise RuntimeError("Refusing cleanup of a container owned by someone else")
                    self.command(["docker", "rm", "--force", container])
                elif "No such object" not in result.stderr.decode(errors="replace"):
                    raise RuntimeError("Could not confirm container cleanup")
            except Exception as error:
                errors.append(str(error))
        if self.private.resolve().parent != (ROOT / ".work/database-recovery-private").resolve():
            errors.append("Refusing private cleanup outside the drill root")
        else:
            shutil.rmtree(self.private)
        if errors:
            raise RuntimeError("; ".join(errors))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--isolated-docker", action="store_true", required=True,
                        help="Opt in to owned disposable containers; never targets existing databases")
    parser.parse_args()
    drill = Drill()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda number, _: sys.exit(128 + number))
    passed = False
    try:
        drill.run()
        passed = True
    finally:
        drill.cleanup()
        drill.proof["result"] = "PASS" if passed else "FAIL"
        (drill.evidence / "proof.json").write_text(json.dumps(drill.proof, indent=2), encoding="utf-8")
        print(f"Recovery evidence: {drill.evidence}", flush=True)


if __name__ == "__main__":
    main()
