# M1 probe: Meta Model API wire shape

Goal: confirm the chat-with-images request shape before any app or proxy
code is written. Run from any machine with `curl` and `python3`. Synthetic
data only. Never paste your key anywhere — replace it with `$KEY` in what
you send back.

```bash
export MODEL_API_KEY="<paste-your-key-here>"
export BASE="https://api.meta.ai/v1"
```

## Step 1 — list models

```bash
curl -s "$BASE/models" -H "Authorization: Bearer $MODEL_API_KEY"
```

Paste back: the full JSON (it contains no secret). I need the exact chat
model IDs (expect something like `muse-spark-*`) to use in step 2.

## Step 2 — image understanding probe

Generate a 1 px synthetic PNG (red dot, no personal data):

```bash
python3 -c "
import base64
png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==')
open('/tmp/dot.png','wb').write(png)
"
IMG=$(base64 -i /tmp/dot.png 2>/dev/null || base64 -w0 /tmp/dot.png)
```

Then send it with the strict-JSON prompt from the feature spec, using one
model ID from step 1 as `$MODEL`:

```bash
curl -s -w "\nHTTP %{http_code}\n" "$BASE/chat/completions" \
  -H "Authorization: Bearer $MODEL_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"model\": \"$MODEL\",
    \"messages\": [{
      \"role\": \"user\",
      \"content\": [
        {\"type\": \"text\", \"text\": \"Reply with strict JSON only: a list of objects with keys observation, photo_ids, uncertainty, missing_information, model_version. Describe one visible detail of this image.\"},
        {\"type\": \"image_url\", \"image_url\": {\"url\": \"data:image/png;base64,$IMG\"}}
      ]
    }],
    \"max_tokens\": 4000
  }"
```

Paste back: the full JSON response body, the HTTP code, and roughly how
long it took. If the shape above is rejected, paste the error verbatim —
adapting to the documented shape is part of M1.

## What happens next

I log the confirmed shape in `docs/hackathon/spike/results.md`, commit M1
on `hackathon/startup-track` (no push), and start the M2 proxy skeleton
against your GCP project.
