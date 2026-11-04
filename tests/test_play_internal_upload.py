"""No-network regression tests for the Play Internal Testing uploader."""
import hashlib
import os
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, main
from unittest.mock import patch

from scripts import play_internal_upload as publisher


class TestInternalPublisher(TestCase):
    def setUp(self):
        self.tmp = TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.aab = Path(self.tmp.name, "signed.aab")
        self.aab.write_bytes(b"example signed bundle test payload")
        self.env = patch.dict(os.environ, {"CC_BUILD_VERSION_CODE": "2"})
        self.env.start()
        self.addCleanup(self.env.stop)
        # Hermetic track/summary state: ambient CI variables must not leak in,
        # and tests must not append to the real step summary. Tests that need
        # these set them explicitly.
        saved = {}
        for key in ("GITHUB_REF", "CC_PLAY_TRACK", "GITHUB_STEP_SUMMARY"):
            if key in os.environ:
                saved[key] = os.environ.pop(key)
        self.addCleanup(os.environ.update, saved)

    def test_publishes_only_to_internal_with_review_safety(self):
        calls = []

        def fake_request(method, endpoint, token, payload=None, content_type="application/json", timeout=180):
            calls.append((method, endpoint, payload))
            if endpoint.endswith("/edits") and method == "POST":
                return {"id": "edit-1"}
            if endpoint.endswith("/bundles") and method == "GET":
                return {"bundles": []}
            if "/upload/" in endpoint:
                return {"versionCode": 2, "sha256": hashlib.sha256(self.aab.read_bytes()).hexdigest()}
            if endpoint.endswith("/tracks/internal") and method == "GET":
                return {"track": "internal", "releases": []}
            return {"id": "edit-1"}

        with patch.object(publisher, "request", side_effect=fake_request):
            publisher.publish("test-token", self.aab, "app.melosome.claimconnect", "abc123")
        self.assertTrue(any(c[0] == "PUT" and c[1].endswith("/tracks/internal")
                            and c[2]["releases"][0]["status"] == "completed" for c in calls))
        self.assertTrue(any(c[1].endswith(":commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW") for c in calls))
        self.assertFalse(any("/tracks/production" in c[1] for c in calls))

    def test_existing_version_with_different_sha_fails_before_track_update(self):
        digest = "deadbeef"
        def fake_request(method, endpoint, token, payload=None, content_type="application/json", timeout=180):
            if method == "POST" and endpoint.endswith("/edits"):
                return {"id": "1"}
            if method == "GET" and endpoint.endswith("/bundles"):
                return {"bundles": [{"versionCode": 2, "sha256": digest}]}
            self.fail("No upload or track mutation expected")
        with patch.object(publisher, "request", side_effect=fake_request):
            with self.assertRaisesRegex(RuntimeError, "already exists with different contents"):
                publisher.publish("test-token", self.aab, "app.melosome.claimconnect", "abc")

    def test_default_track_is_internal(self):
        # Custom track names 404 unless pre-created in Play Console; the default
        # must stay a predefined non-production identifier.
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("CC_PLAY_TRACK", None)
            os.environ.pop("GITHUB_REF", None)
            self.assertEqual(publisher.resolve_track(), "internal")

    def test_each_track_deploys_only_from_its_branch(self):
        good = [("production", "refs/heads/live"), ("beta", "refs/heads/main"),
                ("alpha", "refs/heads/develop"), ("internal", "refs/heads/develop")]
        for track, ref in good:
            with patch.dict(os.environ, {"CC_PLAY_TRACK": track, "GITHUB_REF": ref}):
                self.assertEqual(publisher.resolve_track(), track)
        bad = [("production", "refs/heads/main"), ("beta", "refs/heads/develop"),
               ("alpha", "refs/heads/main"), ("internal", "refs/heads/main")]
        for track, ref in bad:
            with patch.dict(os.environ, {"CC_PLAY_TRACK": track, "GITHUB_REF": ref}):
                with self.assertRaisesRegex(RuntimeError, "deploys only from"):
                    publisher.resolve_track()

    def test_local_runs_skip_branch_check(self):
        with patch.dict(os.environ, {"CC_PLAY_TRACK": "beta"}):
            os.environ.pop("GITHUB_REF", None)
            self.assertEqual(publisher.resolve_track(), "beta")

    def test_unknown_track_is_rejected_before_any_play_call(self):
        with patch.dict(os.environ, {"CC_PLAY_TRACK": "qa"}):
            with self.assertRaisesRegex(ValueError, "Unknown Play track"):
                publisher.resolve_track()

    def test_production_release_drops_test_labelling(self):
        calls = []

        def fake_request(method, endpoint, token, payload=None, content_type="application/json", timeout=180):
            calls.append((method, endpoint, payload))
            if endpoint.endswith("/edits") and method == "POST":
                return {"id": "edit-1"}
            if endpoint.endswith("/bundles") and method == "GET":
                return {"bundles": []}
            if "/upload/" in endpoint:
                return {"versionCode": 2, "sha256": hashlib.sha256(self.aab.read_bytes()).hexdigest()}
            if endpoint.endswith("/tracks/production") and method == "GET":
                return {"track": "production", "releases": []}
            return {"id": "edit-1"}

        with patch.dict(os.environ, {"CC_PLAY_TRACK": "production",
                                     "GITHUB_REF": "refs/heads/live"}):
            with patch.object(publisher, "request", side_effect=fake_request):
                publisher.publish("test-token", self.aab, "app.melosome.claimconnect", "abc123")
        puts = [c for c in calls if c[0] == "PUT" and c[1].endswith("/tracks/production")]
        self.assertEqual(len(puts), 1)
        release = puts[0][2]["releases"][0]
        self.assertNotIn("test", release["name"])
        self.assertNotIn("testing", release["releaseNotes"][0]["text"])

    def test_probe_detects_changes_in_review_and_deletes_probe_edit(self):
        calls = []

        def fake_request(method, endpoint, token, payload=None, content_type="application/json", timeout=180):
            calls.append((method, endpoint))
            if method == "POST" and endpoint.endswith("/edits"):
                return {"id": "probe-edit"}
            if method == "POST" and ":commit" in endpoint:
                raise RuntimeError("Play API POST failed (HTTP 400): You already have changes in review. "
                                   "Cancel this review or wait for it to complete before you try again.")
            return {}

        with patch.object(publisher, "request", side_effect=fake_request):
            self.assertTrue(publisher.changes_in_review("token", "app.melosome.claimconnect"))
        self.assertTrue(any(m == "DELETE" and e.endswith("/edits/probe-edit") for m, e in calls),
                        "probe edit must be deleted so nothing half-made is left behind")

    def test_probe_reports_clear_when_empty_commit_succeeds(self):
        calls = []

        def fake_request(method, endpoint, token, payload=None, content_type="application/json", timeout=180):
            calls.append((method, endpoint))
            if method == "POST" and endpoint.endswith("/edits"):
                return {"id": "probe-edit"}
            return {}

        with patch.object(publisher, "request", side_effect=fake_request):
            self.assertFalse(publisher.changes_in_review("token", "app.melosome.claimconnect"))
        commit = [e for m, e in calls if m == "POST" and ":commit" in e]
        self.assertEqual(len(commit), 1)
        self.assertIn("changesInReviewBehavior=ERROR_IF_IN_REVIEW", commit[0])

    def test_probe_reraises_unexpected_commit_errors(self):
        def fake_request(method, endpoint, token, payload=None, content_type="application/json", timeout=180):
            if method == "POST" and endpoint.endswith("/edits"):
                return {"id": "probe-edit"}
            if method == "POST" and ":commit" in endpoint:
                raise RuntimeError("Play API POST failed (HTTP 500): internal error")
            return {}

        with patch.object(publisher, "request", side_effect=fake_request):
            with self.assertRaisesRegex(RuntimeError, "HTTP 500"):
                publisher.changes_in_review("token", "app.melosome.claimconnect")

    def test_publish_skips_review_required_track_while_in_review(self):
        calls = []
        summary = Path(self.tmp.name, "summary.md")

        def fake_request(method, endpoint, token, payload=None, content_type="application/json", timeout=180):
            calls.append((method, endpoint, payload))
            if method == "POST" and endpoint.endswith("/edits"):
                return {"id": "probe-edit"}
            if method == "POST" and ":commit" in endpoint:
                raise RuntimeError("Play API POST failed (HTTP 400): You already have changes in review. "
                                   "Cancel this review or wait for it to complete before you try again.")
            return {}

        env = {"CC_PLAY_TRACK": "beta", "GITHUB_REF": "refs/heads/main",
               "GITHUB_STEP_SUMMARY": str(summary)}
        with patch.dict(os.environ, env):
            with patch.object(publisher, "request", side_effect=fake_request):
                self.assertEqual(
                    publisher.publish("test-token", self.aab, "app.melosome.claimconnect", "abc123"),
                    "skipped-in-review")
        self.assertFalse(any("/upload/" in e for _, e, _ in calls), "no bundle must be uploaded on skip")
        self.assertFalse(any(m == "PUT" for m, _, _ in calls), "no track must be touched on skip")
        text = summary.read_text()
        self.assertIn("SKIPPED", text)
        self.assertIn("beta", text)

    def test_publish_does_not_probe_internal_track(self):
        edit_posts = []

        def fake_request(method, endpoint, token, payload=None, content_type="application/json", timeout=180):
            if method == "POST" and endpoint.endswith("/edits"):
                edit_posts.append(endpoint)
                return {"id": "edit-1"}
            if endpoint.endswith("/bundles") and method == "GET":
                return {"bundles": []}
            if "/upload/" in endpoint:
                return {"versionCode": 2, "sha256": hashlib.sha256(self.aab.read_bytes()).hexdigest()}
            if endpoint.endswith("/tracks/internal") and method == "GET":
                return {"track": "internal", "releases": []}
            return {"id": "edit-1"}

        with patch.object(publisher, "request", side_effect=fake_request):
            self.assertEqual(
                publisher.publish("test-token", self.aab, "app.melosome.claimconnect", "abc123"),
                "published")
        self.assertEqual(len(edit_posts), 1, "internal track needs no review probe")

    def test_publish_probes_beta_and_proceeds_when_clear(self):
        calls = []
        edits = iter(["probe-edit", "real-edit"])

        def fake_request(method, endpoint, token, payload=None, content_type="application/json", timeout=180):
            calls.append((method, endpoint))
            if method == "POST" and endpoint.endswith("/edits"):
                return {"id": next(edits)}
            if endpoint.endswith("/bundles") and method == "GET":
                return {"bundles": []}
            if "/upload/" in endpoint:
                return {"versionCode": 2, "sha256": hashlib.sha256(self.aab.read_bytes()).hexdigest()}
            if endpoint.endswith("/tracks/beta") and method == "GET":
                return {"track": "beta", "releases": []}
            return {"id": "ok"}

        env = {"CC_PLAY_TRACK": "beta", "GITHUB_REF": "refs/heads/main"}
        with patch.dict(os.environ, env):
            with patch.object(publisher, "request", side_effect=fake_request):
                self.assertEqual(
                    publisher.publish("test-token", self.aab, "app.melosome.claimconnect", "abc123"),
                    "published")
        kinds = [(m, "probe-commit" if ":commit" in e and "probe-edit" in e
                  else "upload" if "/upload/" in e
                  else "beta-put" if m == "PUT" and e.endswith("/tracks/beta") else "other")
                 for m, e in calls]
        order = [k for _, k in kinds if k in ("probe-commit", "upload", "beta-put")]
        self.assertEqual(order, ["probe-commit", "upload", "beta-put"])

    def test_wrong_application_or_missing_token_fails(self):
        with self.assertRaisesRegex(ValueError, "Package ID"):
            publisher.publish("token", self.aab, "app.example.bad", "a")
        with self.assertRaisesRegex(ValueError, "OIDC access token"):
            publisher.publish("", self.aab, "app.melosome.claimconnect", "a")


if __name__ == "__main__":
    main()
