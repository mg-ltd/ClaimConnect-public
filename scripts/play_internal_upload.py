#!/usr/bin/env python3
"""Publish a signed AAB to a Google Play track using a short-lived OIDC access token.

Track comes from CC_PLAY_TRACK (default: internal). Each track deploys only
from its branch: develop feeds internal and alpha, main feeds beta, live feeds
production. Re-running for a second track reuses the identical uploaded bundle.
Tracks that require review (alpha, beta, production) are skipped while Play
reports changes already in review; internal needs no review and is never gated.

Requires a Play Console app that has already received its first APK/AAB via the Console,
and Play Console testing-track permissions for the service account.
No JSON service-account keys or external Python dependencies; production deploys only from live.
"""
import hashlib
import json
import os
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://androidpublisher.googleapis.com"
# Predefined track identifiers are production, beta, alpha and internal; anything
# else must already exist as a custom track or the API answers 404.
DEFAULT_TRACK = "internal"
ALLOWED_TRACKS = ("internal", "alpha", "beta", "production")
# Branch each track deploys from. Local runs (no GITHUB_REF) skip the check.
TRACK_BRANCHES = {
    "internal": "refs/heads/develop",
    "alpha": "refs/heads/develop",
    "beta": "refs/heads/main",
    "production": "refs/heads/live",
}
# Tracks whose releases require Play review. The internal track is exempt and
# is never gated on the in-review probe.
REVIEW_REQUIRED_TRACKS = ("alpha", "beta", "production")


def request(method, endpoint, token, payload=None, content_type="application/json", timeout=180):
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8") if content_type == "application/json" else payload
    req = urllib.request.Request(
        BASE + endpoint,
        data=data,
        headers={
            "Authorization": "Bearer " + token,
            "Content-Type": content_type,
            "Accept": "application/json",
        },
        method=method,
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as exc:
        body = exc.read(4096).decode("utf-8", "replace")
        try:
            error = json.loads(body).get("error", {})
            message = error.get("message", body)
        except ValueError:
            message = body
        if exc.code == 404 and endpoint.endswith("/edits"):
            raise RuntimeError(
                "Google Play app not bootstrapped or service account not authorised. "
                "Create app.melosome.claimconnect in Play Console and upload the FIRST signed "
                "bundle manually; then grant the service account testing-track access."
            ) from exc
        raise RuntimeError(f"Play API {method} failed (HTTP {exc.code}): {str(message)[:500]}") from exc


def resolve_track():
    """Track from CC_PLAY_TRACK (default: internal). Each track deploys only
    from its branch; mismatches are refused before any Play call."""
    track = os.environ.get("CC_PLAY_TRACK", DEFAULT_TRACK)
    if track not in ALLOWED_TRACKS:
        raise ValueError(f"Unknown Play track {track!r}; expected one of {', '.join(ALLOWED_TRACKS)}")
    ref = os.environ.get("GITHUB_REF", "")
    want = TRACK_BRANCHES[track]
    if ref and ref != want:
        raise RuntimeError(f"Track {track} deploys only from {want} (this run is {ref})")
    return track


def report(summary_line):
    """Print an outcome line, and append it to the GitHub step summary when
    running in Actions so skipped tracks stay visible on a green run."""
    print(summary_line)
    path = os.environ.get("GITHUB_STEP_SUMMARY", "")
    if path:
        with open(path, "a", encoding="utf-8") as handle:
            handle.write(summary_line + "\n")


def changes_in_review(token, package):
    """True when Play already has changes in review for this app.

    The API exposes no review-status resource; committing an (empty) edit
    with ERROR_IF_IN_REVIEW errors iff a review is in flight (documented
    400 "You already have changes in review"). The probe flag is hard-coded:
    the API default would cancel the live review and resubmit. Any other
    probe error propagates so the upload fails loudly instead of blindly.
    """
    app_path = "/androidpublisher/v3/applications/" + urllib.parse.quote(package, safe="")
    edit_id = request("POST", app_path + "/edits", token, {})["id"]
    edit_path = app_path + "/edits/" + urllib.parse.quote(edit_id, safe="")
    try:
        request("POST", edit_path + ":commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW", token, {})
        return False
    except RuntimeError as exc:
        if "HTTP 400" in str(exc) and "changes in review" in str(exc).lower():
            return True
        raise
    finally:
        try:
            request("DELETE", edit_path, token)
        except Exception:
            pass


def publish(token, aab, package, commit):
    """Upload to the resolved track. Returns "published", "already-serving"
    or "skipped-in-review"."""
    if not package or package != "app.melosome.claimconnect":
        raise ValueError("Package ID must be app.melosome.claimconnect")
    track = resolve_track()
    if not aab.is_file() or aab.suffix.lower() != ".aab":
        raise ValueError("Signed .aab not found")
    if not token:
        raise ValueError("OIDC access token was not obtained")
    if track in REVIEW_REQUIRED_TRACKS:
        print(f"Review check: probing {package} for changes already in review (track '{track}' requires review)...")
        if changes_in_review(token, package):
            print("=" * 64)
            print(f"SKIP: not uploading to track '{track}': '{package}' already has")
            print("changes in review. The pending Play review is left untouched;")
            print("re-run after the review completes.")
            print("=" * 64)
            report(f"- Track `{track}`: SKIPPED - changes already in review; upload not attempted.")
            return "skipped-in-review"
        print("Review check: no changes in review; proceeding with upload.")
    else:
        print(f"Review check: track '{track}' needs no review; proceeding with upload.")
    data = aab.read_bytes()
    checksum = hashlib.sha256(data).hexdigest()
    app_path = "/androidpublisher/v3/applications/" + urllib.parse.quote(package, safe="")
    edit = request("POST", app_path + "/edits", token, {})
    edit_id = edit["id"]
    edit_path = app_path + "/edits/" + urllib.parse.quote(edit_id, safe="")
    existing = request("GET", edit_path + "/bundles", token).get("bundles", [])
    # CI's versionCode is derived from the protected release workflow run number.
    expected_code = int(os.environ["CC_BUILD_VERSION_CODE"])
    present = next((b for b in existing if b.get("versionCode") == expected_code), None)
    if present:
        if present.get("sha256", "").lower() != checksum:
            raise RuntimeError(
                f"versionCode {expected_code} already exists with different contents. "
                "Do not overwrite the version. Increase CC_PLAY_VERSION_CODE_OFFSET "
                "if needed, or start a fresh release run."
            )
        version = expected_code
        print(f"Existing identical signed bundle (versionCode {version}); reusing it.")
    else:
        result = request(
            "POST",
            "/upload" + edit_path + "/bundles?uploadType=media",
            token,
            data,
            "application/octet-stream",
            timeout=300,
        )
        version = result["versionCode"]
        if int(version) != expected_code:
            raise RuntimeError(f"Play returned versionCode {version}, expected {expected_code}")
        if result.get("sha256") and result["sha256"].lower() != checksum:
            raise RuntimeError("Uploaded bundle checksum differs from source")
    try:
        current = request("GET", edit_path + f"/tracks/{track}", token)
        releases = current.get("releases", [])
    except RuntimeError as exc:
        if "HTTP 404" not in str(exc):
            raise
        releases = []
    if any(
        str(version) in [str(v) for v in release.get("versionCodes", [])]
        and release.get("status") == "completed"
        for release in releases
    ):
        print(f"Track {track} already serves versionCode {version}; no changes required.")
        report(f"- Track `{track}`: already serves versionCode {version}; no changes made.")
        return "already-serving"
    release = {"track": track, "releases": [{
        "name": f"ClaimConnect {str(commit)[:12]}"
        if track == "production" else f"ClaimConnect test {str(commit)[:12]}",
        "versionCodes": [str(version)],
        "status": "completed",
        "releaseNotes": [{"language": "en-GB", "text": "ClaimConnect internal testing build."}]
        if track != "production" else [{"language": "en-GB", "text": "ClaimConnect release."}],
    }]}
    request("PUT", edit_path + f"/tracks/{track}", token, release)
    # Do not cancel an unrelated Play Console review in progress.
    request("POST", edit_path + ":commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW", token, {})
    print(f"Google Play {track} edit committed: {package}, versionCode {version}, sha256 {checksum}")
    print("This does not prove tester delivery; confirm track status in Play Console.")
    report(f"- Track `{track}`: published versionCode {version} (edit committed).")
    return "published"


def main():
    token = os.environ.get("CC_PLAY_ACCESS_TOKEN", "")
    path = pathlib.Path(os.environ.get("CC_SIGNED_AAB", "signed-release.aab"))
    package = os.environ.get("CC_PACKAGE", "app.melosome.claimconnect")
    commit = os.environ.get("GITHUB_SHA", "unknown")
    publish(token, path, package, commit)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, KeyError) as exc:
        print(f"Play internal upload blocked: {exc}", file=sys.stderr)
        sys.exit(1)
