# Alternatives and fallbacks

| Option | Verdict | Reason |
| --- | --- | --- |
| BYOK AI Photo Notes | Fallback and day-1 spike | Real Muse analysis in 2 days, but no billing or reliability story. See [feature spec](01-recommended-feature-ai-photo-notes.md). |
| Hosted Meta proxy with tiers ([production plan](05-production-startup-track.md)) | Recommended for startup track | The only submittable architecture: key custody, quotas, billing, privacy. Needs the full window to 4 Nov, not a weekend. |
| SAM-style region overlays | Too big | Needs the gateway first, plus coordinate mapping and overlay UI. A later release, not the submission. |
| Share-to-Meta-app intent (no API) | Fallback only | Nearly zero code via the existing AI-share session, but no Muse integration to demonstrate. Weak submission story. |
| On-device analysis | Rejected | No Muse model runs on-device; would mean swapping in a different model and losing the Meta track. |
| Full conversational copilot | Rejected | Chat UI, history, and grounding review are a second project on top of the API call. |

## Fallback ladder (in order)

1. **API shape differs from OpenAI-compatible.** Adapt the client to the
   documented shape on day 1; the UI and appendix do not care about wire format.
2. **No API access at all (no key, event blocks keys).** Ship the fallback:
   consent plus share-to-installed-assistant via the existing AI-share session,
   and demo the accept/edit/discard and appendix flow with a canned sample
   response clearly labelled as such. Judges still see the product; the live
   call becomes stretch.
3. **Model quality is poor on synthetic photos.** Narrow the prompt to
   missing-information questions ("which side of the car is shown?") instead of
   observations. Prompting for missing facts is valuable even when vision is weak.

## What approval unlocks

- The recommended feature and its boundaries ([spec](01-recommended-feature-ai-photo-notes.md)).
- BYOK with no auth system ([analysis](02-auth-and-privacy.md)).
- A feature branch cut from a stable main, merged when the production checklist is green.
