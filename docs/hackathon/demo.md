# Demo script (3 minutes, synthetic data only)

Spoken script: [narration.md](narration.md). Build: `main`
with no `CC_AI_PROXY_URL` and an event-issued Meta key saved in Settings
(revoke after recording); without a key the simulator answers and the
consent dialog says so on screen. Airplane mode is fine for the whole demo
except the map snapshot, which needs tiles — preload it or use the offline
diagram; both are real behaviour.

## 0:00–0:30 — Capture as usual

1. New incident → safety → vehicle step (synthetic plate `SYN 000T`).
2. Incident step: dictate one line of the account, keep it.
3. Photos step: add 2 synthetic photos with captions, then Add map snapshot.

Say: "Everything so far works offline. The AI check is one optional entry
on the Review step — no chat, no changes to capture."

## 0:30–1:30 — The AI check

4. Settings → paste the event key → Save key (masked; last-4 status).
5. Review step → Check my record. Read the consent dialog aloud: exact
   photos, display copies to Meta, billed to your key.
6. Progress → Cancel once ("cancelled work is discarded"), run again.
7. Review cards: Keep the bumper observation, Edit the mark observation
   to add "driver confirms", Drop one wrong suggestion on camera.
8. Generate the pack → open the PDF → scroll to "AI-assisted
   observations (unverified)" with photo links and model version.

Say: "Nothing reaches the pack without review. Rejected replies —
liability claims, malformed JSON — never appear as observations."

## 1:30–2:15 — Stale and limits

9. Back to Photos, edit a caption → Review shows the stale banner.
10. Re-check in one tap; generate again.

Say: "Edits invalidate kept notes instead of silently carrying them."

## 2:15–3:00 — Trust and money

11. Show the Settings key status (last-4 only) and the Review header
    ("Live analysis · billed to your Meta key"). State plainly: your
    key, your billing, encrypted on-device, revocable in Meta console.
12. Close on the PDF appendix and the Privacy screen's AI section.

## Recording checklist

- [ ] Do-not-disturb on; synthetic data only (no real plates, faces, keys).
- [ ] Event-issued key; revoked after recording.
- [ ] Consent dialog legible in frame; Meta + billing line read aloud.
- [ ] One kept, one edited, one dropped suggestion shown.
- [ ] Stale banner + re-check shown.
- [ ] PDF appendix scrolled slowly enough to read.
- [ ] No full key, token, or personal data visible at any point.
