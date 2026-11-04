# Probe results log

## Step 1 — model list (31 Oct 2026, user-run)

Source: `meta_model_probe.json` (repo root, user's file — not committed).
Base URL `https://api.meta.ai/v1` and Bearer auth both work.

Available IDs: `muse-spark-1.3` (chat, latest — use for step 2),
`muse-spark-1.2`, `muse-spark-1.1`, `*-contributor` variants,
`muse-voice-transcribe-1.0` (matches the transcription plan),
`muse-image-1.0`, `muse-wonder-1.0`, `sam-3.1`.

## Step 2 — image understanding probe (31 Oct 2026, user-run)

Source: `image_payload.json` (repo root, user's file — not committed).

- Wire shape CONFIRMED: OpenAI-compatible envelope, `image_url` parts
  accepted without error, `usage` block present.
- Reasoning model: the trivial prompt consumed 298 reasoning tokens, hit
  `max_tokens: 300`, and returned `content: null` with
  `finish_reason: length`. Consequence: production `max_tokens` raised to
  4000 (client + proxy), and `length` cut-offs now surface a retryable
  "reply was cut off" failure instead of a silent reject.
- Still open: per-image size/count limits, quota/timeout error envelope.
  Re-run step 2 with `"max_tokens": 4000` to confirm content arrives.

## Step 2b — image probe at 4000 tokens (31 Oct 2026, user-run)

Source: `image_payload_4000.json` (repo root, user's file — not committed).

- Happy path CONFIRMED: `finish_reason: stop`, strict-JSON content,
  token usage reported (953 reasoning + ~88 visible for a trivial image —
  watch for cut-offs on real photos; 4000 holds for now).
- Schema deviations adapted (parser + simulator + prompt):
  `photo_ids` arrives as `["input_0"]` strings (accepted alongside
  zero-based ints; all other strings still rejected) and `uncertainty`
  arrives numeric (accepted alongside sentences).
- Prompt now demands zero-based integer indices with an example; parser
  stays liberal for the observed `input_N` spelling either way.
