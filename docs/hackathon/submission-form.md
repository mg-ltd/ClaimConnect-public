# Submission form answers (DRAFT — paste into the form before 4 Nov 21:00 ET)

## 1. Project description — what problem does it resolve?

After a vehicle collision, drivers must produce an accurate, complete,
tamper-evident record while stressed, often with nothing but a phone
camera and a notes app. Insurers and fleets then receive messy photo
dumps with no structure, no provenance, and inconsistent accounts —
while drivers have no easy way to check their own record for gaps.

ClaimConnect is an offline-first Android app that guides incident
capture (vehicle, driver, location, account, people), retains up to 10
hashed original photographs encrypted on-device, and generates a
paginated PDF evidence pack with a full manifest. New in this
submission: an optional AI photo check that suggests clearly-labelled,
review-only observations (accept / edit / discard) which bake into the
PDF appendix as "AI-assisted observations (unverified)" with photo
links and model version. The app organises user-supplied information
only — it never submits claims, assesses liability, or verifies fault.

## 2. Describe your use of Meta products to build the submitted project

We call Meta's chat-completions API with image understanding
(`muse-spark-1.3` at `https://api.meta.ai/v1`) directly from the app
using the user's own API key (BYOK v1, encrypted on-device). Each
check sends up to 3 display copies — downscaled, EXIF-stripped JPEGs,
never originals — with a strict-JSON prompt asking for one visible
detail per observation, the zero-based indices of the supporting
photos, an uncertainty note, one follow-up question, and the model
version. The client verifies every reply (valid JSON, grounded photo
references, keyword guard rejecting fault / speed / injury / cost
conclusions) and discards anything unverifiable with a neutral
message. Wire shape was confirmed by `curl` probes before any app code
(see `docs/hackathon/spike/`); the simulator exercises the identical
verification path for keyless builds and tests.

## 3. Why are Meta's models important to your solution?

The whole feature rests on trustworthy multimodal grounding: each
suggestion must name exactly which photos support it, or it is
worthless as evidence assistance. Muse Spark follows the strict-JSON
contract and photo-index grounding reliably enough that the
accept / edit / discard review loop works in practice, and its
uncertainty + missing-information fields directly feed the most
valuable UX — telling the driver what extra photo or detail would
complete the record. The OpenAI-compatible request shape kept the
client to stdlib `HttpURLConnection` with no new dependencies, and
per-check cost is low enough that a free monthly tier plus one-time
unlock (the planned hosted follow-up) is economically sane.

## 4. Feedback from your experience using partner technologies

Meta API (probed 31 Oct via `curl`, synthetic images only):

- Worked well: `/models` listing, Bearer [REDACTED] `image_url` parts
  accepted first try, `usage` block with reasoning-token breakdown,
  OpenAI-compatible envelope meant zero SDK friction.
- Friction: a reasoning model spending ~300–950 tokens before answering
  is surprising when `max_tokens` is small — our first probe returned
  `content: null` with `finish_reason: length`, which reads as an
  outage rather than a too-small cap. A documented minimum or a
  distinct signal for "raised the cap and retry" would help.
- Schema: `photo_ids` arrived as `["input_0"]` strings and
  `uncertainty` as a number despite the prompt asking for integer
  indices and sentences. We adapted the parser to accept both, but
  example-driven index formatting in the docs would reduce guessing.
- Still wanted: documented per-image size/count limits and the
  quota / timeout / malformed error envelopes, so clients can map
  them to accurate UI states instead of generic retries.
