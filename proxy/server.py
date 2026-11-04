#!/usr/bin/env python3
"""ClaimConnect AI proxy (M2 skeleton, offline build).

POST /v1/analyze  consented photo analysis with per-install quota
GET  /v1/quota    free-allowance status for an install
GET  /healthz     liveness

Stdlib only. The Meta key lives in the environment (Secret Manager at
deploy) and never in the app. Without a key the proxy answers from the
simulator so the app and billing tracks build offline; probe day flips
one variable. Logs carry counts and sizes only — never photo bytes,
install IDs, or model text.
"""
import hashlib
import json
import logging
import os
import threading
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

logger = logging.getLogger("proxy")

CONFIG = {
    "port": int(os.environ.get("PORT", "8080")),
    "meta_base_url": os.environ.get("META_BASE_URL", "https://api.meta.ai/v1"),
    "meta_model": os.environ.get("META_MODEL", "muse-spark-1.3"),
    "free_allowance": int(os.environ.get("FREE_ALLOWANCE", "10")),
    "max_photos": 3,
    "max_photo_bytes": 3 * 1024 * 1024,
    "max_body_bytes": 10 * 1024 * 1024,
    "upstream_timeout": 60,
    "quota_file": os.environ.get("QUOTA_FILE", "quota.json"),
    "entitlement_file": os.environ.get("ENTITLEMENT_FILE", "entitlements.json"),
    "simulate_scenario": os.environ.get("SIMULATE_SCENARIO", "SUPPORTED"),
}
_quota_lock = threading.Lock()


def meta_key():
    key = os.environ.get("META_API_KEY", "")
    return key if key.strip() else None


def killed():
    return os.environ.get("KILL_SWITCH", "") == "1"


def short_hash(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()[:12]


def month_key(now=None):
    return time.strftime("%Y-%m", time.gmtime(now))


def read_entitlements():
    try:
        with open(CONFIG["entitlement_file"], encoding="utf-8") as handle:
            data = json.load(handle)
            return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def is_entitled(install_id):
    return install_id in read_entitlements()


def grant_entitlement(install_id, product_id):
    with _quota_lock:
        store = read_entitlements()
        store[install_id] = {"product_id": product_id, "verified_at": int(time.time())}
        tmp = CONFIG["entitlement_file"] + ".tmp"
        with open(tmp, "w", encoding="utf-8") as handle:
            json.dump(store, handle)
        os.replace(tmp, CONFIG["entitlement_file"])


def verify_purchase(product_id, purchase_token):
    """Simulated accept in the offline build; Play Developer API on probe day.

    Fail-closed: without SIMULATE_ENTITLED=1 every token is rejected, so a
    skeleton deploy can never grant unlocks to forged requests.
    """
    if not product_id or len(purchase_token) < 8:
        return False
    if os.environ.get("SIMULATE_ENTITLED", "") != "1":
        logger.warning("entitlement unverified (Developer API not configured)")
        return False
    return True


def read_quota():
    try:
        with open(CONFIG["quota_file"], encoding="utf-8") as handle:
            data = json.load(handle)
            return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def quota_used(install_id, store):
    entry = store.get(install_id)
    if not isinstance(entry, dict) or entry.get("month") != month_key():
        return 0
    used = entry.get("used", 0)
    return used if isinstance(used, int) and used >= 0 else 0


def quota_consume(install_id):
    with _quota_lock:
        store = read_quota()
        used = quota_used(install_id, store)
        if used >= CONFIG["free_allowance"]:
            return None
        store[install_id] = {"month": month_key(), "used": used + 1}
        tmp = CONFIG["quota_file"] + ".tmp"
        with open(tmp, "w", encoding="utf-8") as handle:
            json.dump(store, handle)
        os.replace(tmp, CONFIG["quota_file"])
        return used + 1


SIMULATED = {
    "SUPPORTED": [
        {"observation": "The front bumper shows a visible crack on the left side.",
         "photo_ids": [0], "uncertainty": "The far edge is in shadow and hard to read.",
         "missing_information": "Which side of the car is shown in photo 1?",
         "model_version": "proxy-sim-1"},
    ],
    "AMBIGUOUS": [
        {"observation": "A light-coloured mark is visible on the door.",
         "photo_ids": [0], "uncertainty": "It is unclear whether this is damage, dirt, or a reflection.",
         "missing_information": "Can you add a closer photo of the mark?",
         "model_version": "proxy-sim-1"},
    ],
}


def call_meta(photos, timeout):
    """Forward display copies to Meta; returns the suggestion list. Raises on failure."""
    import base64  # local import keeps module import side-effect free for tests
    parts = [{"type": "image_url", "image_url": {"url": "data:%s;base64,%s" % (
        photo["mime"], base64.b64encode(photo["bytes"]).decode("ascii"))}} for photo in photos]
    body = json.dumps({
        "model": CONFIG["meta_model"],
        "messages": [{"role": "user", "content": [
            {"type": "text", "text": (
                "Reply with strict JSON only: a list of objects with keys observation, "
                "photo_ids, uncertainty, missing_information, model_version. Each observation "
                "names one visible detail in plain words; photo_ids MUST be the zero-based "
                "integer indices of the sent photos that support it, e.g. [0, 2]; uncertainty "
                "is a short sentence. Never conclude fault, speed, injury, or costs, and never "
                "invent facts not visible in the photos.")},
            *parts]}],
        # 4000: the step-2 probe showed ~300 reasoning tokens before any answer.
        "max_tokens": 4000,
    }).encode("utf-8")
    request = urllib.request.Request(
        CONFIG["meta_base_url"].rstrip("/") + "/chat/completions", data=body,
        headers={"Authorization": "Bearer " + meta_key(), "Content-Type": "application/json",
                 "Accept": "application/json"}, method="POST")
    with urllib.request.urlopen(request, timeout=timeout) as response:
        payload = json.loads(response.read())
    content = payload["choices"][0]["message"]["content"]
    items = json.loads(content)
    if not isinstance(items, list):
        raise ValueError("upstream shape")
    return items


class Handler(BaseHTTPRequestHandler):
    server_version = "ClaimConnectProxy/0.1"

    def log_message(self, *args):
        logger.info("%s %s", self.address_string(), args[0] % args[1:])

    def _send(self, code, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return None, (413, {"error": "Request too large."})
        if length <= 0 or length > CONFIG["max_body_bytes"]:
            return None, (413, {"error": "Request too large."})
        try:
            return json.loads(self.rfile.read(length)), None
        except ValueError:
            return None, (400, {"error": "Malformed request."})

    def do_GET(self):  # noqa: N802 (stdlib handler naming)
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/healthz":
            self._send(200, {"ok": True, "simulate": meta_key() is None})
            return
        if parsed.path == "/v1/quota":
            install_id = urllib.parse.parse_qs(parsed.query).get("install_id", [""])[0]
            if not valid_install_id(install_id):
                self._send(400, {"error": "Unknown install."})
                return
            used = quota_used(install_id, read_quota())
            self._send(200, {"month": month_key(), "free_allowance": CONFIG["free_allowance"],
                             "used": used, "remaining": max(0, CONFIG["free_allowance"] - used),
                             "entitled": is_entitled(install_id)})
            return
        self._send(404, {"error": "Not found."})

    def do_POST(self):  # noqa: N802 (stdlib handler naming)
        path = urllib.parse.urlparse(self.path).path
        if path == "/v1/entitlement":
            self._entitlement()
            return
        if path != "/v1/analyze":
            self._send(404, {"error": "Not found."})
            return
        started = time.monotonic()
        outcome, code, payload = self._analyze()
        elapsed_ms = int((time.monotonic() - started) * 1000)
        logger.info(json.dumps({k: v for k, v in outcome.items() if k != "install_id"},
                               default=str) + f' "elapsed_ms":{elapsed_ms}')
        self._send(code, payload)

    def _entitlement(self):
        payload, error = self._read_json()
        if error is not None:
            self._send(*error)
            return
        install_id = payload.get("install_id", "")
        product_id = payload.get("product_id", "")
        token = payload.get("purchase_token", "")
        if not valid_install_id(install_id) or not isinstance(product_id, str) or not product_id:
            self._send(400, {"error": "Malformed request."})
            return
        if verify_purchase(product_id, token if isinstance(token, str) else ""):
            grant_entitlement(install_id, product_id)
            logger.info(json.dumps({"event": "entitlement", "outcome": "granted",
                                    "install": short_hash(install_id)}))
            self._send(200, {"entitled": True})
        else:
            logger.info(json.dumps({"event": "entitlement", "outcome": "rejected",
                                    "install": short_hash(install_id)}))
            self._send(200, {"entitled": False})

    def _analyze(self):
        if killed():
            outcome = {"event": "analyze", "outcome": "killed"}
            return outcome, 503, {"error": "The analysis service is paused. Try again later."}
        payload, error = self._read_json()
        if error is not None:
            return {"event": "analyze", "outcome": "rejected"}, *error
        install_id = payload.get("install_id", "")
        if not valid_install_id(install_id):
            outcome = {"event": "analyze", "outcome": "rejected"}
            return outcome, 400, {"error": "Unknown install."}
        photos, error = read_photos(payload.get("photos"))
        hashed = short_hash(install_id)
        if error is not None:
            return {"event": "analyze", "outcome": "rejected", "install": hashed}, *error
        entitled = is_entitled(install_id)
        if not entitled:
            used = quota_consume(install_id)
            if used is None:
                outcome = {"event": "analyze", "outcome": "quota_exhausted", "install": hashed}
                return outcome, 429, {"error": "The free allowance is used up for this month."}
        total_bytes = sum(len(photo["bytes"]) for photo in photos)
        try:
            if meta_key() is None:
                items = [dict(item) for item in SIMULATED.get(CONFIG["simulate_scenario"], SIMULATED["SUPPORTED"])]
                upstream = "simulated"
            else:
                items = call_meta(photos, CONFIG["upstream_timeout"])
                upstream = "meta"
        except Exception:  # noqa: BLE001 (upstream failures stay neutral by design)
            outcome = {"event": "analyze", "outcome": "upstream_error", "install": hashed,
                       "photos": len(photos), "bytes": total_bytes}
            return outcome, 502, {"error": "The analysis service failed. Try again shortly."}
        outcome = {"event": "analyze", "outcome": "ok", "install": hashed,
                   "photos": len(photos), "bytes": total_bytes, "upstream": upstream}
        return outcome, 200, {"suggestions": items}


def valid_install_id(value):
    return isinstance(value, str) and 8 <= len(value) <= 64 and all(
        ch.isalnum() or ch in "-_" for ch in value)


def read_photos(raw):
    import base64
    if not isinstance(raw, list) or not raw or len(raw) > CONFIG["max_photos"]:
        return None, (400, {"error": "Send between 1 and 3 photos."})
    photos = []
    for entry in raw:
        if not isinstance(entry, dict):
            return None, (400, {"error": "Malformed photo."})
        mime = entry.get("mime", "")
        if mime not in ("image/jpeg", "image/png", "image/webp"):
            return None, (400, {"error": "Photos must be JPEG, PNG or WebP."})
        try:
            data = base64.b64decode(entry.get("data", ""), validate=True)
        except Exception:  # noqa: BLE001 (any decode failure is one client error)
            return None, (400, {"error": "Malformed photo."})
        if not data or len(data) > CONFIG["max_photo_bytes"]:
            return None, (413, {"error": "A photo exceeds the size limit."})
        photos.append({"mime": mime, "bytes": data})
    return photos, None


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s")
    server = ThreadingHTTPServer(("0.0.0.0", CONFIG["port"]), Handler)
    logger.info("proxy listening on :%d (simulate=%s)", CONFIG["port"], meta_key() is None)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
