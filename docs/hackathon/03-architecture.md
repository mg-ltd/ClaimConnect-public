# Architecture: AI Photo Notes

All new code lives in the app, on a hackathon branch. No backend, no new
dependencies except an `INTERNET` permission (HTTP via stdlib).

## Component diagram

```
+---------------------+      +----------------------+      +------------------+
| Settings            |      | Review screen        |      | PDF generator    |
| - key field (last4) |      | - select <=3 photos  |      | + AI appendix    |
| - Keystore-backed   |      | - consent sheet      |      |   (unverified,   |
|   key storage       |      | - progress + cancel  |      |   photo-linked,  |
+----------+----------+      | - accept/edit/drop   |      |   stale-on-edit) |
           |                 +----------+-----------+      +--------+---------+
           | key                        | accepted notes            ^
           v                            v                           |
+---------------------+      +----------------------+               |
| Muse API client     |      | Suggestion store     |---------------+
| - HTTPS POST        |----->| (suggestions only,   |
| - timeout + cancel  | JSON |  never incident data)|
| - strict validation |<-----|                       |
+----------+----------+      +----------------------+
           |
           |  Bearer <user key>, display copies, EXIF stripped
           v
   https://api.meta.ai/v1  (OpenAI-compatible chat completions;
                            exact shape verified by day-1 curl probe)
```

Existing pieces reused unchanged: photo capture, `materialiseForAi` sessions
with expiry and startup sweep, incident repository and encryption, PDF manifest
and hashes.

## Data flow (one analysis)

```
User picks photos --> consent --> display copies --> strip EXIF --> HTTPS POST
                                                                     |
Model JSON <-- validate schema <-- ground every claim to a photo id <--+
   |
   +-- valid: show per-item accept/edit/discard
   +-- invalid/ungrounded: discard with neutral message
Accepted text --> PDF appendix --> edit of evidence invalidates notes
```

The original photo bytes never leave the device except inside the consented
request, and the stored incident never contains model text except by explicit
accept.

## Screen flow

```
Review ──> Analyse photos ──> Pick <=3 ──> Consent ──> Progress (cancel)
                                                      │
                                    ┌─────────────────┴─────────────────┐
                                    │ success                           │ offline/error
                                    v                                   v
                          What AI noticed                       neutral message
                          [accept/edit/drop]                     back to Review
                                    │ accepted
                                    v
                          Regenerate PDF ──> appendix shown ──> share/save
```

## What stays offline-first

Everything except the analysis call. Capture, autosave, reopen, PDF preview,
save, share, archive, delete, and backup all work with no key and no network.
The Analyse entry is hidden or disabled with an explanation when offline.
