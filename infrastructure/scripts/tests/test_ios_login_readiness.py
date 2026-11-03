import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch
import urllib.error
from urllib.parse import parse_qs, urlsplit

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("ios_login_runner", SCRIPTS / "test-ios-oidc.py")
runner_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner_module)

FORM = (b"<h1>Bliss</h1><input name='username'><input type='password' name='password'>"
        b"<button type='submit'>Sign In</button>")


class LoginReadinessTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="bliss-login-readiness-")
        self.addCleanup(directory.cleanup)
        self.runner = runner_module.Runner.__new__(runner_module.Runner)
        self.runner.evidence = Path(directory.name)

    def response(self, body):
        response = MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        response.read.return_value = body
        return response

    def test_readiness_uses_fresh_pkce_get_without_credentials_or_browser_cookies(self):
        with patch.object(runner_module.urllib.request, "build_opener") as build:
            opener = build.return_value
            opener.open.return_value = self.response(FORM)
            self.runner.warm_login_form()
            request = opener.open.call_args.args[0]
            url = urlsplit(request.full_url)
            query = parse_qs(url.query)
            self.assertEqual((url.scheme, url.netloc), ("http", "127.0.0.1:8180"))
            self.assertEqual(request.get_method(), "GET")
            self.assertIsNone(request.data)
            self.assertFalse({name.lower() for name in request.headers} & {"cookie", "authorization"})
            self.assertEqual(query["client_id"], ["bliss-mobile"])
            self.assertEqual(query["redirect_uri"], ["com.bliss.mobile:/oauthredirect"])
            self.assertEqual(query["prompt"], ["login"])
            self.assertEqual(query["code_challenge_method"], ["S256"])
            self.assertEqual(len(query["code_challenge"][0]), 43)
            self.assertTrue(query["state"][0] and query["nonce"][0])
            self.assertIsInstance(build.call_args.args[0], runner_module.NoRedirect)
            self.assertEqual(opener.open.call_args.kwargs["timeout"], 120)
            self.assertTrue((self.runner.evidence / "login-form-ready.txt").read_text().startswith("PASS:"))

    def test_readiness_refuses_incomplete_or_wrong_realm_form(self):
        for body in (FORM.replace(b"name='password'", b"name='other'"), FORM.replace(b"Bliss", b"Other")):
            with self.subTest(body=body), patch.object(runner_module.urllib.request, "build_opener") as build:
                build.return_value.open.return_value = self.response(body)
                with self.assertRaisesRegex(RuntimeError, "controls are incomplete"):
                    self.runner.warm_login_form()
                self.assertFalse((self.runner.evidence / "login-form-ready.txt").exists())

    def test_readiness_refuses_redirect_and_suppresses_protocol_query_in_errors(self):
        handler = runner_module.NoRedirect()
        self.assertIsNone(handler.redirect_request(None, None, 302, "Found", {}, "https://example.test"))
        with patch.object(runner_module.urllib.request, "build_opener") as build:
            build.return_value.open.side_effect = urllib.error.URLError("?state=synthetic-private-state")
            with self.assertRaises(RuntimeError) as caught:
                self.runner.warm_login_form()
            self.assertEqual(str(caught.exception), "Cold Keycloak login-form readiness failed")
            self.assertFalse((self.runner.evidence / "login-form-ready.txt").exists())


if __name__ == "__main__":
    unittest.main()
