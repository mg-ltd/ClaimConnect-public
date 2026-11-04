# AI cost model (offline build + step-2 probe)

Question: is per-image token spend commercially viable? Short answer: yes —
about a penny per analysis, so the free allowance costs less than the
payment fee on one unlock. Detail and assumptions below; every number is
flagged observed, reported, or assumed.

## Inputs

**Observed (step-2 probe, `muse-spark-1.3`, trivial 1 px image):**

- Prompt tokens: 44. Completion: 1041 (953 reasoning + ~88 visible).
- Lesson: reasoning dominates output (~90%); visible JSON is cheap.

**Reported (multiple secondary sources, NOT verified on Meta's own page —
confirm in console on probe day):**

- Standard tier: $1.25 / 1M input tokens, $4.25 / 1M output tokens
  (reasoning tokens bill as output).
- Contributor tier ($0.10 / $0.20) is REJECTED: it requires letting Meta
  train on user prompts and outputs, which contradicts the consent and
  privacy story. Never use it for incident data.

**Assumed (flagged for measurement via the `usage` block on a real
1024 px probe):**

- Image input tokens per display copy: 500 / 1000 / 2000 scenarios.
- Real-photo reasoning: 1000–2500 tokens; visible answer: 150–400.
- Average analysis: 2 photos (cap is 3).

## Cost per analysis (2 photos, $1.25 / $4.25)

| Scenario | Input tokens | Input $ | Output tokens | Output $ | Total |
|---|---|---|---|---|---|
| Lean (500/img, 1200 out) | ~1,150 | $0.0014 | ~1,200 | $0.0051 | **~$0.0065 (~0.5p)** |
| Base (1000/img, 1500 out) | ~2,150 | $0.0027 | ~1,500 | $0.0064 | **~$0.009 (~0.7p)** |
| Heavy (2000/img, 2500 out) | ~4,150 | $0.0052 | ~2,500 | $0.0106 | **~$0.016 (~1.2p)** |

## Allowance and SKU math

- Free allowance (10/month) costs at most ~$0.16 (~12p) per heavy user
  per month — and incidents are rare, so most users cost ~1p ever.
- One-time unlock at £2.99 keeps ~£2.54 (~$3.20) after Play's 15%
  small-business fee (~£4.24 at £4.99). One payer funds roughly
  200–500 analyses ($3.20 ÷ $0.0065–$0.016).
- At 1,000 payers (£2.99): ~£2,540 net covers ~200k–500k free analyses.
  Margin per payer is ~95%+; the commercial risk is volume, not unit cost.
- Fixed costs: ~£0 (Cloud Run free tier covers the pilot; $25 Play fee
  already sunk). No model hosting.

## Abuse bounds

- Free quota is per anonymous install; reinstalls reset it. Farming cost
  is bounded (~12p per 10 analyses) and acceptable for the pilot.
- Planned mitigations, in order: Play Integrity verdicts, per-IP rate
  limits, proxy kill switch, monthly global spend cap with alerts.

## Levers that keep it sensible

1. Display copies capped at 1024 px JPEG (already) — bounds image tokens.
2. 3 photos per analysis, 10 free per month (already) — bounds worst case.
3. JSON-only prompt keeps visible output short; reasoning is the wild
   card (no effort parameter observed — flag if a cheaper model or
   effort setting appears).
4. Monitor the `usage` block per call; alert if mean analysis cost
   exceeds $0.03 or image tokens exceed 2500/photo.
5. Future lever: serve cached suggestions for identical photo hashes
   (note staleness rules first — edits must still invalidate).

## Open measurements (probe day)

- [ ] Per-image input tokens for a real 1024 px JPEG (read `usage`).
- [ ] Reasoning distribution over 10+ real analyses (mean, p95).
- [ ] Confirm $1.25/$4.25 on Meta's own pricing/console page.
- [ ] Quota/timeout error envelope (still unobserved).
