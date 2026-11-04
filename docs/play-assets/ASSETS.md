# Play assets

Source: the brand icon in `docs/` (actual size 4267x4267). Built with the scratch
tool in `/tmp/assetgen` (not committed); rebuild by re-running it if the brand changes.

## Final (upload as-is)

- `icon-512.png`: 512x512 PNG, ~25 KB (limit 1 MB).
- `feature-graphic-<locale>.png`: 1024x500 PNG each (`en`, `fr`, `de`, `es`,
  `it`), ~25 KB each (limit 15 MB). Use `en` for both en-GB and en-US listings.
  Brand teal `#49969D`, icon, name and translated tagline. Text auto-fits.
  Per-locale screenshots are not possible yet: the app UI is English-only, so
  all locales share the English screenshots until UI strings are externalised.

## Drafts (do NOT upload)

`shot-1-safety.png` … `shot-4-review.png` are spec-size placeholders
(1080x1920, 9:16) watermarked DRAFT. Play rejects non-app imagery.

Real replacements come from the instrumented suite: `CaptureFlowTest` saves
home, safety, photo, review and pack shots (synthetic data) to the device's
`play-shots` directory, and both emulator jobs pull and upload them as the
`play-screenshots` artifact (30-day retention).

On `main` the release job goes one step further: passing suites publish the
PNGs to the `ci/play-screenshots` review branch (spec-checked for 9:16 and
size, skipped on test failure, never touching `main`, never failing the job).
Review that branch, then merge it here to replace the drafts. No auto-merge:
a human confirms the shots show the app correctly first.
