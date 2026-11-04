# Startup track: production-ready AI service

Status (4 Nov): v1 submits on BYOK; this doc is the production follow-up
track. It supersedes the BYOK recommendation in [README](README.md) once
the proxy ships.
BYOK drops to fallback: a weekend demo key flow does not clear the startup bar
(real users, real billing, real reliability). The submission architecture is a
central proxy with free and paid tiers, built during the event on a feature
branch and released when the checklist below is green.

## Bar for "production ready"

- A stranger can install the app, analyse photos, and pay without contacting us.
- No Meta key in the APK or repo. Secrets live in the backend secret store only.
- Every upload is consented, minimised, and metered. Server retains no photos.
- The offline incident flow works fully when the service is down or unpaid.
- Costs are capped and observable; abuse is throttled; privacy claims match code.

## Architecture

```
Android app                      Our backend (new)                 Meta
-----------                      -----------------                 ----
Review -> consent -> HTTPS POST  -> Play Integrity check    POST https://api.meta.ai/v1
  (select <=3 photos,               -> install-ID quota     (Bearer server-held key,
   EXIF stripped)                   -> size/count limits     OpenAI-compatible shape,
                                    -> forward Minimised      verified day-1 spike)
   accept/edit/discard <- JSON  <-- validate + strip PII ---------+
   PDF appendix          from logs
         |
Play Billing (entitlements) <-- server-side verification (Developer API / RTDN)
Free X/month per install, then subscription or one-time unlock.
```

Identity without accounts: random install UUID created on first AI use, bound
with Play Integrity verdicts, counters server-side. Accounts become an option
later for cross-device entitlements, not a launch requirement.

## What must be live at submission

1. Proxy: HTTPS, secret store, per-install quota, request size/count/timeout
   limits, kill switch, structured logs with no photo bytes or PII, cost alerts.
2. App: consent sheet, progress with cancel, per-item accept/edit/discard, AI
   appendix marked unverified with photo links, stale-on-edit, offline and
   denied states, no key field anywhere.
3. Billing: at least one paid SKU through Play Billing with server-side
   verification; free allowance enforced server-side (never client-side).
4. Trust: privacy policy published, Data safety declaration matching the binary
   (network + AI processing), support contact, no liability or forensic claims.
5. Proof: golden synthetic set (supported, ambiguous, adversarial image text),
   on-device demo recording, architecture note, confirmed submission receipt.

## Phases against real dates

- 27-28 Oct: backend skeleton, Meta integration via day-1 `curl` spike, quota
  and limits, no app changes yet.
- 29-30 Oct: app integration (consent, progress, review list, appendix).
- 31 Oct-1 Nov: Play release prep for the base app (signing, listing, QA).
- 2 Nov: billing SKU wired and sandbox-verified, privacy and listing updates.
- 3 Nov: internal lock. Full demo, goldens, video, submission draft.
- 4 Nov 21:00 ET: submit. Nothing merges after the lock except a showstopper.

## Unit economics (estimate, confirm from invoices)

A 3-photo analysis costs on the order of a few cents in tokens at published
rates. A free tier of 10 analyses a month costs well under a dollar per active
user per month worst case. Set the paid price at a multiple of heavy-user cost,
cap free usage server-side, and alert on daily spend from day one.

## Risks and explicit cuts

- Play release slips: AI pauses. Nothing public ships until the base app release is safe.
- Billing review friction: ship free tier live with paid SKU sandbox-verified;
  flip to live sale the moment verification passes. Never fake a purchase UI.
- Model quality on staged photos: narrow to missing-information questions if
  observations disappoint (still valuable, still honest).
- Key custody: exactly one backend identity holds the Meta key, rotation
  documented, never in CI logs or crash reports.
