"""No-external-network tests for the AI proxy (localhost server, stdlib only)."""
import base64
import io
import json
import logging
import os
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from unittest import TestCase


def load_server(env):
    for key in ("META_API_KEY", "SIMULATE_SCENARIO", "KILL_SWITCH", "FREE_ALLOWANCE",
                "QUOTA_FILE", "ENTITLEMENT_FILE", "SIMULATE_ENTITLED", "META_MODEL"):
        os.environ.pop(key, None)
    os.environ.update(env)
    import proxy.server as server
    import importlib
    return importlib.reload(server)


class ProxyCase(TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.server_module = load_server({
            "QUOTA_FILE": os.path.join(cls.tmp.name, "quota.json"),
            "ENTITLEMENT_FILE": os.path.join(cls.tmp.name, "entitlements.json"),
            "FREE_ALLOWANCE": "2",
            "SIMULATE_SCENARIO": "SUPPORTED",
        })
        cls.httpd = ThreadingHTTPServer(("127.0.0.1", 0), cls.server_module.Handler)
        cls.port = cls.httpd.server_address[1]
        cls.thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        cls.thread.start()
        cls.logs = io.StringIO()
        handler = logging.StreamHandler(cls.logs)
        logging.getLogger("proxy").addHandler(handler)
        logging.getLogger("proxy").setLevel(logging.INFO)

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.thread.join(timeout=5)
        cls.tmp.cleanup()

    def call(self, method, path, payload=None):
        data = json.dumps(payload).encode() if payload is not None else None
        request = urllib.request.Request(f"http://127.0.0.1:{self.port}{path}", data=data,
                                         headers={"Content-Type": "application/json"}, method=method)
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                return response.status, json.loads(response.read())
        except urllib.error.HTTPError as exc:
            return exc.code, json.loads(exc.read())

    def photo(self, size=64):
        return {"mime": "image/jpeg", "data": base64.b64encode(bytes(size)).decode("ascii")}

    def test_healthz_reports_simulate_mode(self):
        code, body = self.call("GET", "/healthz")
        self.assertEqual(200, code)
        self.assertTrue(body["simulate"])

    def test_quota_starts_full(self):
        code, body = self.call("GET", "/v1/quota?install_id=test-install-1")
        self.assertEqual(200, code)
        self.assertEqual({"month": body["month"], "free_allowance": 2, "used": 0,
                          "remaining": 2, "entitled": False}, body)

    def test_analyze_returns_suggestions_and_consumes_quota(self):
        code, body = self.call("POST", "/v1/analyze",
                               {"install_id": "test-install-2", "photos": [self.photo()]})
        self.assertEqual(200, code)
        item = body["suggestions"][0]
        for key in ("observation", "photo_ids", "uncertainty", "missing_information", "model_version"):
            self.assertIn(key, item)
        _, quota = self.call("GET", "/v1/quota?install_id=test-install-2")
        self.assertEqual(1, quota["used"])

    def test_allowance_exhaustion_returns_429(self):
        for _ in range(2):
            code, _ = self.call("POST", "/v1/analyze",
                                {"install_id": "test-install-3", "photos": [self.photo()]})
            self.assertEqual(200, code)
        code, body = self.call("POST", "/v1/analyze",
                               {"install_id": "test-install-3", "photos": [self.photo()]})
        self.assertEqual(429, code)
        self.assertIn("allowance", body["error"])

    def test_limits_are_enforced(self):
        code, _ = self.call("POST", "/v1/analyze",
                            {"install_id": "test-install-4", "photos": [self.photo() for _ in range(4)]})
        self.assertEqual(400, code)
        code, _ = self.call("POST", "/v1/analyze",
                            {"install_id": "test-install-4", "photos": []})
        self.assertEqual(400, code)
        code, _ = self.call("POST", "/v1/analyze", {"install_id": "x", "photos": [self.photo()]})
        self.assertEqual(400, code)
        big = {"mime": "image/jpeg",
               "data": base64.b64encode(bytes(3 * 1024 * 1024 + 1)).decode("ascii")}
        code, _ = self.call("POST", "/v1/analyze", {"install_id": "test-install-4", "photos": [big]})
        self.assertEqual(413, code)

    def test_kill_switch_pauses_analysis(self):
        os.environ["KILL_SWITCH"] = "1"
        try:
            code, _ = self.call("POST", "/v1/analyze",
                                {"install_id": "test-install-5", "photos": [self.photo()]})
            self.assertEqual(503, code)
        finally:
            del os.environ["KILL_SWITCH"]

    def test_simulated_entitlement_unlocks_quota(self):
        os.environ["SIMULATE_ENTITLED"] = "1"
        try:
            code, body = self.call("POST", "/v1/entitlement",
                                   {"install_id": "buyer-install-1", "product_id": "ai_unlock",
                                    "purchase_token": "simulated-token-123"})
            self.assertEqual(200, code)
            self.assertTrue(body["entitled"])
            _, quota = self.call("GET", "/v1/quota?install_id=buyer-install-1")
            self.assertTrue(quota["entitled"])
            for _ in range(3):
                code, _ = self.call("POST", "/v1/analyze",
                                    {"install_id": "buyer-install-1", "photos": [self.photo()]})
                self.assertEqual(200, code)
        finally:
            del os.environ["SIMULATE_ENTITLED"]

    def test_verification_is_fail_closed_by_default(self):
        code, body = self.call("POST", "/v1/entitlement",
                               {"install_id": "buyer-install-2", "product_id": "ai_unlock",
                                "purchase_token": "forged-token-123"})
        self.assertEqual(200, code)
        self.assertFalse(body["entitled"])
        code, _ = self.call("POST", "/v1/entitlement",
                            {"install_id": "x", "product_id": "ai_unlock",
                             "purchase_token": "forged-token-123"})
        self.assertEqual(400, code)

    def test_logs_carry_no_photo_bytes_or_install_ids(self):
        marker = base64.b64encode(b"\x01\x02marker-bytes").decode("ascii")
        _, _ = self.call("POST", "/v1/analyze",
                         {"install_id": "secret-install-9",
                          "photos": [{"mime": "image/jpeg", "data": marker}]})
        logs = self.logs.getvalue()
        self.assertNotIn(marker, logs)
        self.assertNotIn("secret-install-9", logs)
        self.assertIn('"outcome": "ok"', logs)


if __name__ == "__main__":
    unittest.main()
