# Writing conventions

All user-visible copy in ClaimConnect follows these rules. The reader is often
standing at a roadside, shaken, in a hurry — every string must survive a
two-second glance.

## Case: sentence case everywhere

Per [Material Design 3](https://m3.material.io/foundations/content-design/style-guide/ux-writing-best-practices):
all titles, headings, labels, menu items, navigation, app bars and buttons use
sentence-style capitalisation — first word only, plus proper nouns and acronyms.

- "New incident", not "New Incident"
- "Generate evidence pack", not "Generate Evidence Pack"
- "Save PDF", "AI photo check", "Google Play" (acronyms and proper nouns keep capitals)

## Brevity: write for a stressed reader

- Buttons: 1–3 words, verb first. "Save PDF", "Retry save", "Discard".
- Errors: what happened + what to do, one short sentence each.
  "Microphone denied. Type your account instead."
- No throat-clearing. "Saves automatically as you go." beats
  "A new record saves automatically as you complete it."
- Prefer "photos" over "photographs", contractions over stiff forms
  ("can't", "don't", "won't").

## Consistency

- Referencing a button in body text? Quote its exact label and casing:
  "Tap Retry save", 'reads your position once for "Add map snapshot"'.
- Renaming a button means grepping for every reference: UI text, the Privacy
  screen, `docs/`, and the tests (`CaptureFlowTest` taps literal text).
- Dialog titles ask or name; buttons answer. Title "Share this evidence
  pack?" → button "Share", not "Continue to share".

## Out of scope

- `ProductLimits.NOTICE` is legal-adjacent text that also ships inside the PDF;
  do not reword it without review.
- The AI prompt template (`aiPrompt`) is model input, not user copy.
