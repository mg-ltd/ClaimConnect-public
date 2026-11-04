# M1 contract: Meta analysis call (assumed, keyless build)

Until the [probe](probe.md) runs with a real key, all code targets this
assumed shape and all tests run against the simulator. Nothing here sends
network traffic without a configured key and base URL.

## Assumed wire shape (OpenAI-compatible chat completions)

```
POST {baseUrl}/chat/completions
Authorization: Bearer <key>
Content-Type: application/json

{
  "model": "<chat-model-id>",
  "messages": [{
    "role": "user",
    "content": [
      {"type": "text", "text": "<strict-JSON prompt>"},
      {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,<photo-0>"}},
      ...
    ]
  }],
  "max_tokens": 4000
}
```

4000, not 800: the step 2 probe showed the model spending ~300 reasoning
tokens before answering, so small caps return `content: null`.

Response envelope: `{choices: [{message: {content: "<strict JSON array>"}}]}`.
`content` is a JSON array of suggestion objects:

```json
[
  {"observation": "...", "photo_ids": [0, 2], "uncertainty": "...",
   "missing_information": "... or null", "model_version": "..."}
]
```

`photo_ids` are zero-based indices into the sent photos; the client maps
them to evidence IDs and rejects anything out of range or empty.

## Validation rules (implemented in `ai/AiResponseParser.kt`)

- Strict JSON only; malformed envelope or content is discarded wholesale.
- Every object needs all five keys with the right types.
- `photo_ids` must be non-empty and reference sent photos only.
- Keyword guard rejects liability/cost/medical conclusions (fault,
  liability, speed, injury, cost, price, currency, speed units).
- Image captions and user notes are untrusted input, never instructions.

## Confirmation checklist (probe day, keys present)

- [x] Exact base URL, auth scheme, and chat model ID (step 1 probe:
  `https://api.meta.ai/v1`, Bearer, `muse-spark-1.3`).
- [x] Image part shape (`image_url` accepted, step 2 probe).
- [ ] Size/count limits and quota/timeout error envelope (step 2 re-run).
- [ ] Error envelope for quota/timeout/malformed (map to neutral UI states).
- [ ] Replace `SimulatedAiClient` with the confirmed `MetaApiClient` config
      in the proxy; app code does not change.
