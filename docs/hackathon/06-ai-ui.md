# AI UI: guided check, not chat

Status (31 Oct 2026): shipped as built with one promotion — the entry is
now a prominent `AI photo check` card (sparkle icon, live quota subtitle,
quota bar, "How AI checks work" explainer) instead of a plain row, and the
Photos step links to it. Limits and the unlock path are visible before
exhaustion. The rules below (no chat, capture untouched, review-only
suggestions) still hold.

## The context that decides this

People open this app at the roadside: shaky hands, glare, time pressure, possibly
shock. The current UI already designs for that: a linear 6-step wizard with Back
and Continue, one primary button per screen at 48-56dp, short labels, autosave
with a visible Saved state, and no required typing beyond essentials. Any AI
surface must meet that bar or stay out of the capture flow.

## Recommendation: do not build a chat UI

A free-text conversation is the wrong shape for distress use:

- It demands reading, typing, and turn-taking when attention is scarcest.
- Open-ended replies are harder to ground to photos, harder to validate, and
  harder to fit into the PDF appendix model (one accepted item each).
- Free chat invites oversharing of medical and personal detail the app
  explicitly tells users to avoid, and every extra word is sent, billed, and stored.
- Long threads cost more tokens per session and are miserable with TalkBack,
  large fonts, and one-handed use.

If a repair turn is ever needed, ask one constrained question with tappable
answers ("Which side is shown? Front / Rear / Side / Skip"), never an open box.

## Recommended pattern: guided check on the Review step

Reuse the exact components the app already uses: an `AlertDialog` for consent
(the Review screen already has `aiDialog`), cards for results (like `PhotoCard`
and `ReportCard`), a `LinearProgressIndicator` for the wait, and full-width
buttons. No new tab: the bottom bar is top-level destinations only, and AI is a
pushed flow like Settings, not a destination.

Placement:

1. **Review step** gets one primary-adjacent entry, "Check my record", beside
   "Generate evidence pack". This replaces the external share-sheet handoff as
   the default AI path.
2. **Photos step** gets a quiet nudge only when useful ("2 photos without
   captions"), never a blocking prompt. Capture is never interrupted.

Wireframe (Review step, results state):

```
+--------------------------------------------------+
| Review your record                  [Ready]      |
+--------------------------------------------------+
| AI check                            [Re-check]   |
|  2 suggestions · 1 question                      |
| +----------------------------------------------+ |
| | Photo 2 shows front-left damage.             | |
| | [Keep] [Edit] [Drop]                         | |
| +----------------------------------------------+ |
| | Number plate readable in photo 1 —           | |
| | consider retaking without it.                | |
| | [Keep] [Edit] [Drop]                         | |
| +----------------------------------------------+ |
| | ? What best describes the location?          | |
| | [Road] [Car park] [Roundabout] [Skip]        | |
| +----------------------------------------------+ |
| [Generate evidence pack]                         |
+--------------------------------------------------+
```

States, all deterministic (no open conversation):

- Idle, consent (what is sent, where, billed to plan), progress with cancel,
  results (cards above), error with retry (offline and failure keep every other
  action working), stale (evidence edited after a check → banner, one-tap re-check).

## Why this helps capture, not just review

The highest-value output is not observations but **missing-information prompts
that fill fields**. Each question chip writes back through the existing edit
path (location, captions, time), so the AI actively completes the record instead
of commenting on it. That is the distress win: the app notices what a shaken
person forgot, and fixing it is one tap.

## Fit with production tiers

The same surface works for free and paid: the free allowance gates the
"Check my record" entry with a remaining-count label ("3 free checks left"),
and exhaustion routes to the paywall instead of the model. Consent text names
the service, not a key, since custody moved server-side.

## Validation

- Compose test: consent deny sends nothing; cancel leaves no notes; each card
  action updates state; evidence edit marks notes stale.
- Usability: synthetic incident with check still completes in under five
  minutes; large font, dark mode, TalkBack pass on the new cards.
- No free-text input is added anywhere in the AI flow.
