import importlib.util
from pathlib import Path
import sys
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("ios_acceptance_runner", SCRIPTS / "test-ios-oidc.py")
runner_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner_module)


class DiagnosticRedactionTest(unittest.TestCase):
    def setUp(self):
        # Test sanitization without creating realms, files, passwords or processes.
        self.runner = runner_module.Runner.__new__(runner_module.Runner)
        self.runner.passwords = {"customer": "synthetic-complete-password"}

    def test_full_password_and_jwt_are_removed(self):
        result = self.runner.scrub("synthetic-complete-password token=eyJfixture.payload.signature")
        self.assertEqual("[REDACTED] token=[REDACTED JWT]", result)

    def test_truncated_secure_keystrokes_are_removed_but_username_is_retained(self):
        for suffix in ("...", "…"):
            source = f"Type 'prefix{suffix}' into \"Password\" SecureTextField\nType 'customer' into TextField"
            result = self.runner.scrub(source)
            self.assertNotIn("prefix", result)
            self.assertIn("Type '[REDACTED]' into SecureTextField", result)
            self.assertIn("Type 'customer' into TextField", result)

    def test_protocol_query_is_removed_but_path_status_and_checkpoint_are_retained(self):
        source = ("INFO [org.keycloak.http.access-log] (thread) POST /realms/bliss/login-actions/authenticate"
                  "?session_code=synthetic-code&client_data=synthetic-data 302\n"
                  "INFO [org.keycloak.http.access-log] (thread) POST /realms/bliss/protocol/openid-connect/token 200\n"
                  "Verified iOS: customer browser PKCE callback and authenticated profile")
        result = self.runner.scrub(source)
        self.assertNotIn("session_code", result)
        self.assertNotIn("synthetic-data", result)
        self.assertIn("POST /realms/bliss/login-actions/authenticate 302", result)
        self.assertIn("/openid-connect/token 200", result)
        self.assertIn("Verified iOS: customer browser PKCE callback", result)


if __name__ == "__main__":
    unittest.main()
