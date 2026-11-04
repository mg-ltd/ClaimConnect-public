# Submission narration (BYOK v1, ~3½ minutes, synthetic data only)

Read-aloud script for the demo video. Actions mirror [demo.md](demo.md);
timestamps assume a calm ~140 wpm delivery. Build: `main`
with no `CC_AI_PROXY_URL`. Use an event-issued Meta key and revoke it
after recording — the key field is masked and Settings shows only the
last 4 characters, so both are safe to keep in frame.

## 0:00–0:30 — Capture as usual (30s, ~70 words)

> "ClaimConnect records a vehicle incident and produces a PDF evidence
> pack — fully offline. I'll create an incident with a synthetic plate,
> dictate one line of the account, and attach two synthetic photos with
> captions, plus a map snapshot. Everything so far works with no
> connection at all. The AI check is one optional entry on the Review
> step — no chat, no changes to capture, and nothing leaves the device
> unless I say so."

On screen: New incident → safety → vehicle (`SYN 000T`) → dictate,
Keep → Photos (+2, captions, map snapshot).

## 0:30–1:00 — Your key, your billing (30s, ~70 words)

> "For live analysis, I paste my own Meta API key in Settings. It's
> encrypted on this device — the app never shows more than the last four
> characters — and it's only ever sent to the Meta API when I confirm a
> check. Usage bills to my Meta account, and I can revoke the key in my
> Meta console any time. No account, no subscription, no server of ours
> holding secrets."

On screen: Settings → AI photo check → paste key → Save key → status
`••••1234`. Then open Review.

## 1:00–2:10 — The AI check (70s, ~160 words)

> "On Review, I tap Check my record. The consent sheet names the exact
> photos and where display copies go — downscaled, location-stripped
> copies, never my originals. I'll run it, cancel once to show cancelled
> work is discarded, and run it again. Each suggestion comes back as a
> review card: I'll Keep the bumper observation, Edit the mark
> observation to add what I confirm, and Drop the one it got wrong.
> Generating the pack bakes the kept notes into the PDF as AI-assisted
> observations — unverified, linked to their photos, with the model
> version. Anything the reply can't verify — liability claims, malformed
> answers — never appears as an observation at all."

On screen: consent → Analyse → Cancel → Analyse → Keep / Edit / Drop
→ Generate pack → PDF appendix, slow scroll.

## 2:10–2:50 — Stale and limits (40s, ~90 words)

> "The notes don't silently survive edits. I'll change a caption, and
> Review flags the kept notes as stale — they're left out of the next
> pack unless I re-check in one tap. Ten photos per incident, three per
> check, and if the service is unreachable, capture and PDF generation
> carry on exactly as before. The AI is an assistant to the record, never
> part of it."

On screen: edit caption → stale banner → Re-check → generate again.

## 2:50–3:30 — Trust and close (40s, ~90 words)

> "To recap: records stay encrypted on this device, originals and hashes
> are byte-identical before and after any check, and every AI word in the
> pack passed human review. Bring your own key today; a hosted free and
> paid tier follows the same consent and verification path. Thanks —
> everything you saw runs on synthetic data, in the build linked below."

On screen: Privacy screen AI section → PDF appendix → Home.

## Recording checklist

- [ ] Do-not-disturb on; synthetic data only (no real plates, faces, keys).
- [ ] Event-issued key; revoked after recording.
- [ ] Consent dialog legible in frame; Meta + billing line read aloud.
- [ ] One kept, one edited, one dropped suggestion shown.
- [ ] Stale banner + re-check shown.
- [ ] PDF appendix scrolled slowly enough to read.
- [ ] No full key, token, or personal data visible at any point.
