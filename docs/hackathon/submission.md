# Submission draft (DRAFT — edit before 4 Nov 21:00 ET)

Submitted by Melosome Group Ltd. Proprietary — see [LICENSE](../../LICENSE).

## Title

ClaimConnect: guided incident evidence with a verifiable AI check

## Tagline

Capture a vehicle incident offline, then let AI suggest clearly-labelled,
review-only photo observations for the evidence pack.

## What it does

- Guided, autosaved incident record with hashed originals and an
  encrypted PDF evidence pack — fully offline.
- Optional AI photo check on the Review step: per-action consent naming
  exact photos, cancellable progress, and accept/edit/discard cards.
- Strict verification: valid JSON, grounded photo references, and no
  liability, speed, injury, or cost conclusions — anything else is
  discarded with a neutral message.
- Accepted notes bake into the PDF as "AI-assisted observations
  (unverified)" with photo links and model version; later edits
  invalidate them instead of carrying them silently.
- BYOK v1: the user pastes their own Meta API key in Settings
  (encrypted on-device, last-4 display only); usage bills to their Meta
  account. A proxied free/paid tier follows as the production track.

## Architecture (for judges)

App (Kotlin, offline-first) → Meta chat completions with images, direct
with the judge's own API key (BYOK v1: key encrypted on-device, display
copies only, per-action consent). Without a key the simulator exercises
the identical verification path, and the consent dialog labels it. The
proxy track (stdlib Python: quota, limits, kill switch, no-photo logs)
follows as the production follow-up — no app flow changes needed.

## Privacy

Records stay AES-GCM encrypted on-device. Display copies (downscaled,
EXIF-stripped) leave only on explicit consent. Anonymous install UUID
for quota; purchase tokens verified server-side. No account, no ads,
no analytics.

## Pre-submit checklist

- [ ] Probe results logged; wire shape confirmed or adapted.
- [ ] BYOK key flow verified on device: save → live check → remove.
- [ ] Billing deferred to the proxy track (not in v1).
- [ ] Data safety + listing updates submitted (non-English locales TODO).
- [ ] Demo video recorded per `demo.md` with synthetic data only.
- [ ] Final full-suite green on the submission commit.
