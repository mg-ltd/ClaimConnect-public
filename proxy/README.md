# AI proxy (M2 skeleton)

Stdlib-only Python service fronting the Meta API: per-install free quota,
request limits, kill switch, and structured logs with no photo bytes or
install IDs. Without `META_API_KEY` it answers from the simulator so the
app and billing tracks build offline.

## Run locally

```bash
python3 proxy/server.py            # :8080, simulate mode
python3 -m unittest discover -s proxy -p 'test_server.py'
```

## Environment

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | 8080 | Listen port (Cloud Run injects this). |
| `META_API_KEY` | unset → simulate | Meta key; Secret Manager at deploy, never in the app. |
| `META_BASE_URL` | `https://api.meta.ai/v1` | Upstream base (probe day confirms). |
| `META_MODEL` | unset | Chat model ID (probe day confirms). |
| `FREE_ALLOWANCE` | 10 | Free analyses per install per month. |
| `QUOTA_FILE` | `quota.json` | JSON quota store (swap for managed storage later). |
| `SIMULATE_SCENARIO` | SUPPORTED | Simulator response (`SUPPORTED`, `AMBIGUOUS`). |
| `KILL_SWITCH` | unset | Set to `1` to pause analysis with 503s. |
| `SIMULATE_ENTITLED` | unset → reject | Set to `1` in dev to accept purchase tokens. |
| `ENTITLEMENT_FILE` | `entitlements.json` | JSON unlock store (managed storage later). |

`POST /v1/entitlement` records paid unlocks after verification. It is
fail-closed: without `SIMULATE_ENTITLED=1` every token is rejected until
probe day wires the Play Developer API check.

## Deploy (owner provisions)

Build this directory to Cloud Run (or equivalent) with `META_API_KEY`
from Secret Manager. Quota counters live in `QUOTA_FILE` on local disk
in the skeleton — acceptable for the pilot; move to managed storage if
the service ever runs more than one instance.

## Probe-day checklist (keys present)

- [ ] Confirm base URL, model ID, image part shape, error envelope.
- [ ] Verify a live end-to-end analysis through the proxy (synthetic photo).
- [ ] Enable Play Integrity verification (M4 wires the client token).
- [ ] Confirm cost alerts and daily spend dashboard exist.
