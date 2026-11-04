# Auth and privacy: why no user authentication is needed

## Decision

**No user accounts, no sign-in, no session tokens, no backend auth.**
The user supplies their own Meta API key in Settings. Calls bill to their Meta
account. There is no server of ours to protect and no shared quota to abuse, so
an authentication system would add work with zero security benefit.

A hosted proxy needs caller metering, rate limits, and a kill switch instead.
That is exactly what the [production plan](05-production-startup-track.md)
builds, on the submission timeline rather than the 2-day spike.

## Key handling

- Stored encrypted with the app's existing Keystore-backed crypto, in the same
  style as incident payloads. Never in plain preferences, logs, screenshots, or
  backups (`allowBackup` is already false).
- Sent only as a `Bearer` header over HTTPS (`usesCleartextTraffic` is already
  false). Never as a query parameter.
- A key field shows only the last 4 characters. Replacing or clearing the key
  is one tap. Revocation happens in the user's Meta console, not in our app.

## Consent and minimisation

- Per-action consent sheet names the exact photos, the recipient (Meta API),
  and the purpose (suggesting observations). Default is opt-out; denial runs
  the normal offline flow with no transfer.
- Maximum 3 photos per request, display copies only. EXIF location stripped
  before upload. No registration plates, names, or contact details are sent
  unless visible inside the chosen photos, and the sheet says so.
- AI notes are stored as suggestions, never merged into the user's incident
  record. Accepting copies text into the appendix; the originals and SHA-256
  manifest are byte-identical before and after.

## Failure rules

- No network, timeout, quota error, or malformed model reply: show a neutral
  message and return to Review. Offline capture and PDF generation are never
  affected. Failed calls leave no partial notes.
- Schema-invalid or ungrounded replies are discarded with a
  "could not verify" message, not shown as observations.

## Demo hygiene

Judges use a personal or event-issued key on a synthetic incident. No real
personal data in fixtures, video, or attachments. The demo key is revoked after
submission.
