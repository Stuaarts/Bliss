"""Acceptance must fail on missing/false database proof, not emit a false PASS."""
import importlib.util
import json
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "test-ios-oidc.py"
spec = importlib.util.spec_from_file_location("ios_acceptance", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SecondaryProofTests(unittest.TestCase):
    def verify(self, proof):
        runner = module.Runner.__new__(module.Runner)
        runner.run_id = "generated-local-run"
        runner.passwords = {"db": "disposable-local-test"}
        runner.pg_bin = Path("unused-postgres-bin")
        runner.run = lambda *_args, **_kwargs: json.dumps(proof)
        runner.verify_secondary()

    def test_requires_each_assertion_to_be_boolean_true(self):
        passing = dict.fromkeys(module.SECONDARY_PROOF_KEYS, True)
        self.verify(passing)
        for key in passing:
            for bad_value in (False, None, 1, "true"):
                with self.subTest(key=key, value=bad_value):
                    with self.assertRaises(RuntimeError):
                        self.verify(dict(passing, **{key: bad_value}))
            missing = passing.copy()
            del missing[key]
            with self.assertRaises(RuntimeError):
                self.verify(missing)

    def test_empty_or_extra_proof_is_rejected(self):
        for proof in ({}, dict.fromkeys(module.SECONDARY_PROOF_KEYS | {"unexpected"}, True)):
            with self.assertRaises(RuntimeError):
                self.verify(proof)

    def test_admin_and_network_secrets_are_redacted(self):
        runner = module.Runner.__new__(module.Runner)
        runner.passwords = {"admin": "test-admin-secret", "network": "test-network-secret"}
        self.assertEqual(runner.scrub("test-admin-secret test-network-secret"), "[REDACTED] [REDACTED]")


if __name__ == "__main__":
    unittest.main()
