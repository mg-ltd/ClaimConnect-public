# Hackathon brainstorm: Meta AI feature in 2 days

Context: ClaimConnect Android is an offline incident recorder (see [project readme](/Users/oso/Projects/mgltd/ClaimConnect-android/README.md)).
This directory is fresh brainstorming for the startup-track submission: a
production-ready AI service with free and paid tiers, scoped to the window
before the 4 Nov deadline. Older per-track plans are parked and not referenced
here.

## Recommendation (startup track: production ready)

Build **[the production AI service](05-production-startup-track.md)**: a central
proxy holding the Meta key, anonymous install metering with a free allowance,
paid unlock through Play Billing, and the same accept/edit/discard review flow
ending in a labelled PDF appendix. No user accounts; no key in the app.

 Startup track judges score a real product, so the weekend BYOK demo is demoted
to fallback: it cannot show real users, real billing, or real reliability.
The 2-day BYOK slice survives only as the day-1 integration spike and the
offline fallback if API access fails.

## Authentication: install identity, not user accounts

Short version: **no sign-in system.** The backend meters an anonymous install
UUID backed by Play Integrity verdicts, and Play Billing owns entitlements.
Full analysis in [auth and privacy](02-auth-and-privacy.md), as amended by the
[production plan](05-production-startup-track.md).

## Files

- [01-recommended-feature-ai-photo-notes.md](01-recommended-feature-ai-photo-notes.md) —
  feature spec, prompt contract, 2-day work split, demo script.
- [02-auth-and-privacy.md](02-auth-and-privacy.md) —
  why no auth is needed, key storage, consent, redaction, failure rules.
- [03-architecture.md](03-architecture.md) —
  component, data-flow, and screen-flow diagrams.
- [04-alternatives-and-fallbacks.md](04-alternatives-and-fallbacks.md) —
  options considered and rejected, plus the fallback if the API is unreachable.
- [05-production-startup-track.md](05-production-startup-track.md) —
  the submission architecture: proxy, tiers, billing, dates, and cuts.
- [06-ai-ui.md](06-ai-ui.md) —
  guided check instead of chat: distress-fit UI, placement, wireframe.
  **Approved**: capture flow stays as-is; AI adds one Review entry plus cards.
- [07-voice-narration.md](07-voice-narration.md) —
  spoken incident account: native-first dictation with Muse Voice
  Transcribe as the metered Phase 2 upgrade. Feasibility checks pending.
- [spike/probe.md](spike/probe.md) + [spike/contract.md](spike/contract.md) —
  M1 Meta API wire probe and the assumed contract the offline build targets.
- [demo.md](demo.md) — 3-minute recorded demo script (synthetic data only).
- [submission.md](submission.md) — DRAFT submission text and pre-submit checklist.

## Hard boundaries (all options)

- AI work ships only when it is reviewed, tested, and declared in the store
  listing. No public build contains an unfinished AI flow.
- No Meta key in the APK, no upload without per-action consent, no changes to
  original photo bytes or hashes, no liability or damage-cost conclusions.
- Assumption to verify on day 1: the Meta Model API accepts OpenAI-compatible
  chat completions with base64 image parts at `https://api.meta.ai/v1`.
  First task is a `curl` probe against the official docs, not app code.
