# Voice narration: transcribe the incident account

Spoken narration for the "Your account of the incident" field
(`Incident.notes`, wizard step 1). Decision: **native-first hybrid** —
Android `SpeechRecognizer` dictation ships with the base app; Muse Voice
Transcribe via the production proxy is a metered Phase 2 upgrade.

Status (31 Oct 2026): Phase 1 is implemented in tree
(`speech/SpeechTranscriber.kt`, `RECORD_AUDIO`, dictate/review dialogs on
step 1) and pending device QA. Review offers Keep (append), Replace and
Discard. Phase 2 (Muse) is unchanged below.
Feasibility: native device check and Muse `curl` probe results are
recorded at the end of this file; the Muse probe is still partial.

## What the user gets

One mic button inline on the narration field. The user holds (or taps)
to speak, sees the transcript appear, then keeps, edits, or discards it
like any typed text. Typing still works exactly as today; denial of the
microphone permission or a failed transcription never blocks capture.

## Engine comparison (inspected sources)

|                                   | Native Android (`SpeechRecognizer`) | Muse Voice Transcribe (`muse-voice-transcribe-1.0`) |
|-----------------------------------|-------------------------------------|-----------------------------------------------------|
| Engine                            | Built into Android, no new library; works even when offline; needs `RECORD_AUDIO` grant | Meta Model API speech-to-text: live WebSocket `wss://api.meta.ai/v1/asr/realtime` or file upload `POST https://api.meta.ai/v1/asr/transcribe` |
| New permissions                   | `RECORD_AUDIO` only; no network change | `RECORD_AUDIO` + `INTERNET` via the production proxy, never direct from the app |
| Cost                              | Free, no metering | $0.18/hour of audio processed, per whole second; failed/`429` requests not billed (a 2-minute narration is about $0.006 worst case) |
| Strengths                         | Offline, zero billing/privacy surface, smallest Play-review delta | 25 languages with code-switching, vocabulary biasing, endpointing/diarization, consistent quality across devices |
| File contract (Muse)              | n/a | RIFF/WAVE mono 16-bit PCM at 16/24 kHz; 32 MB body / 10-minute caps; modes `PUSH_TO_TALK` (default), `ENDPOINTING`, `DIARIZATION` |

Sources: Android `SpeechRecognizer` reference and ASR guide
(`developer.android.com/reference/android/speech/SpeechRecognizer`,
`developer.android.com/develop/xr/jetpack-xr-sdk/asr`); Meta speech-to-text
guide (`dev.meta.ai/docs/speech-to-text.md`).

## Why native first

Narration is captured at the roadside, where typing is hardest — so the
default path must work offline with no account, key, or payment. That
matches the standing bar: the offline incident flow works fully when the
service is down or unpaid. Muse transcription inherits the production
proxy architecture (consent, minimisation, per-install quota, no key in
the APK) as a metered enhancement, and stays Phase 2 so an AI slip never
blocks the base release or the photo-notes submission.

## UX placement (distress-fit, per `06-ai-ui.md`)

- One mic button on the narration field (wizard step 1), beside the
  existing text input. No chat UI, no new tab, no new free-text surface.
- Deterministic states only: idle → listening (with cancel) → review
  (Keep / Edit / Drop) → error with retry. Cancel or denial leaves
  `notes` byte-identical.
- Transcript text flows through the existing edit path (`vm.edit`), so
  autosave, validation ("Add a brief account of what happened"),
  encryption, and PDF output are unchanged.
- Review step keeps one quiet entry to re-transcribe or improve the
  account; capture is never interrupted.

## Data rules

- Text-only v1: audio is transcribed and discarded; no voice file is
  retained. No Room schema change, no storage-cap change, no new
  encrypted-file type.
- The transcript inherits `notes` handling: AES-GCM encrypted JSON
  payload, included in the PDF and AI-share text like typed words.
- Muse path follows the existing AI consent/minimisation rules:
  per-action consent naming recipient (Meta API via our proxy) and
  purpose; in-app conversion to the required WAV shape before upload;
  caps enforced client-side; server retains no audio.
- Copy must state: native audio is transcribed on-device and discarded;
  Muse audio is sent only on explicit per-action consent.

## Phase 1 — native dictation (base app, no billing)

1. Add `RECORD_AUDIO` to the manifest with a runtime-permission flow and
   a neutral denial message; typing remains fully available.
2. Push-to-talk `SpeechRecognizer` session scoped to the narration
   field; partials shown live, final text offered as Keep/Edit/Drop.
3. Compose tests: denial sends nothing; cancel leaves `notes` unchanged;
   each review action updates state; airplane-mode still transcribes.
4. Usability: TalkBack, large font, dark mode on the mic button and
   review cards; still completable in under five minutes.
5. Update the Play Data safety declaration for microphone use (folds
   into the existing roadmap redo item).

## Phase 2 — Muse improvement (AI tier, via proxy)

1. `curl` probe of `POST /v1/asr/transcribe` with a synthetic WAV first
   (same day-1 gate as photo-notes); no app or proxy code before it passes.
2. Proxy endpoint: Play Integrity + install-ID quota shared with the
   photo-notes allowance (reversible assumption), size/count limits,
   kill switch, structured logs with no audio bytes or PII.
3. App: "Improve transcript" entry reusing the consent sheet, progress
   with cancel, and accept/edit/discard cards; offline and exhausted
   states keep every other action working.
4. Goldens: clear, noisy, and multilingual synthetic clips; adversarial
   check that background speech is not silently merged as fact.

## Demo script (synthetic data only)

1. Open a synthetic incident, dictate the account hands-free, keep the
   transcript, generate the PDF showing it as the incident account.
2. Deny the microphone and show typing still works; cancel mid-speech
   and show `notes` unchanged.
3. Toggle offline and show native dictation and PDF still working.
4. (Phase 2) Run "Improve transcript" on a noisy clip with consent,
   discard one wrong suggestion on camera, regenerate the PDF.

## Non-goals for v1

Retained voice notes or audio attachments, word-level timestamps,
speaker-labelled multi-party interview mode, automatic report writing
from audio, any change to originals or hashes, and any release to Play
before the base app release is safe.

## Feasibility log

- Native device check: NOT RUN — the sandbox blocks the adb daemon
  (`could not install *smartsocket* listener: Operation not permitted`),
  so no device or emulator could be reached in this session. Rerun step 3
  of the plan on target hardware before implementation.
- Muse `curl` probe: PARTIAL (31 Oct 2026, no key). Unauthenticated POST
  to `https://api.meta.ai/v1/asr/transcribe` returns HTTP 415; a shaped
  multipart `request` part without audio returns a structured
  `invalid_request_error` ("Both a 'request' and an 'audio' part are
  required") with a `sessionId`. Endpoint is live and the `request` +
  `audio` contract matches the docs. Full transcription still needs a
  rerun with `MODEL_API_KEY` set and a synthetic WAV (revoke after)
  before any proxy or app code:
  `ffmpeg -i input.m4a -ac 1 -ar 24000 -c:a pcm_s16le -map_metadata -1 output.wav`
  then `curl --fail-with-body 'https://api.meta.ai/v1/asr/transcribe?sessionId=my-session-id'`
  `-H "Authorization: Bearer $MODEL_API_KEY"`
  `-F 'request={"mode":"PUSH_TO_TALK","model":"muse-voice-transcribe-1.0","audioEncoding":"WAV"};type=application/json'`
  `-F 'audio=@output.wav'`.
