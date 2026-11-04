# ClaimConnect pilot release

Build verification must pass before distributing a pilot. The initial distribution target is Google Play Internal Testing. A public production listing depends on the developer account's eligibility, applicable testing requirements and Google's review.

## Signing

Use the organisation's controlled Play upload key. Do not commit signing keys, passwords or production incident data. Keep a recoverable copy of the upload key in your approved secret store.

Create the GitHub `pilot` environment and configure these secrets:

| Secret | Value |
| --- | --- |
| `CC_KEYSTORE_BASE64` | Base64 of the upload keystore as one single line: `base64 -w0 upload-key.jks` (Linux) or `base64 -i upload-key.jks` (macOS). Wrapped multi-line output also decodes, but a single line avoids paste errors. |
| `CC_KEYSTORE_PASSWORD` | Keystore password |
| `CC_KEY_ALIAS` | Upload key alias |
| `CC_KEY_PASSWORD` | Key password |

A reviewed merge to a release branch automatically runs verification, the emulator suite, AAB signing, and upload to that branch's track(s). No tags are needed. The single exception is the very first package if the app was never uploaded: retain the signed AAB artifact and upload it once manually in Play Console (the Publishing API cannot create the first release).

## Branches and tracks

| Branch | Track(s) | Environment |
| --- | --- | --- |
| `develop` | Internal Testing + Closed Testing (alpha) — **PAUSED, merge to `main` to run** | `pilot` |
| `main` | Open Testing (beta) | `pilot` |
| `live` | Production | `production` |

Promote by fast-forwarding `main` -> `live` from a tested commit. The
`develop` pipeline is paused: pushes there run nothing until it is
re-enabled and the `pilot` claim admits `refs/heads/develop` again.

Before the first production run, create the GitHub `production` environment:

- Same four secrets as `pilot` (`CC_KEYSTORE_BASE64`, `CC_KEYSTORE_PASSWORD`,
  `CC_KEY_ALIAS`, `CC_KEY_PASSWORD`), same upload key.
- Same variables as `pilot` (`GCP_WIF_PROVIDER`, `GCP_SERVICE_ACCOUNT`,
  `CC_PLAY_VERSION_CODE_OFFSET`), and require human reviewers on the
  environment so no push ships without approval.
- Workload-identity claim conditions must admit their branches: `pilot` admits
  `refs/heads/main`, `production` admits `refs/heads/live`.

Safety notes: the uploader refuses any track/branch mismatch before any Play
call, and all branches share one workflow run-number counter so later
versionCodes always exceed earlier ones on every track. The first production
release still needs Google's review; for tracks that require review (alpha,
beta, production) the uploader first probes for changes already in review and
skips the upload - leaving the pending review untouched - instead of failing.
The internal track needs no review and is never gated. Skipped tracks are named
in the job log and summary; re-run after the review completes.

For a local signed build, supply `CC_KEYSTORE_PATH`, `CC_KEYSTORE_PASSWORD`, `CC_KEY_ALIAS` and `CC_KEY_PASSWORD` in the environment and run `./gradlew bundleRelease`. Do not place passwords in command history.

## Play preparation

1. Confirm the approved application ID and signing key before the first upload.
2. Complete the physical-device checks in `DEVICE_QA.md`; record tested models, API versions and results.
3. Provide a real support contact, public privacy-policy URL, pilot terms, app icon and device screenshots.
4. Describe the app as organising incident information supplied by the user. Avoid claims settlement, insurer submission, liability or independently verified evidence promises.
5. Complete Play Console's app-content, content-rating and Data safety declarations against the shipped binary and actual distribution arrangements. With AI in v1 this includes network use: selected photos transmitted to the AI service for analysis, plus purchases if billing ships. Map snapshots add approximate-location transmission to the OpenFreeMap tile service on explicit user action. User-directed exports and other apps' behaviour still need accurate explanation.
6. Upload the signed AAB to Internal Testing, configure the tester list and publish the internal release.
7. Install through the opt-in link on a physical device and repeat capture, restart, photograph, PDF, save/share and deletion checks.

Stop the pilot if a critical data-loss, privacy, encryption or export-completeness defect is found. Local records cannot be recovered from a lost device or after uninstall. Users must keep their own exported copies.

## Readiness board (October 2026 audit)

Technical compliance is confirmed: target API 37 exceeds Play's API 36 minimum;
min SDK 30 is allowed; the app ships no native libraries so 16 KB page alignment
and 64-bit requirements do not apply; telephony, camera and location hardware are
correctly optional; no advertising ID is used.

| Item | Status |
| --- | --- |
| Valid `pilot` signing secret | Fixed. `CC_KEYSTORE_BASE64` re-pasted as one line; release runs sign the AAB and publish to tracks. |
| First Play upload | Open. If never uploaded, one manual Console upload of the first signed AAB, then automation takes over. |
| Real phone screenshots (2 minimum) | Automated: green release runs publish suite PNGs to `ci/play-screenshots`; merge that branch into `docs/play-assets/`. Draft templates there are placeholders only. |
| App icon 512 px, feature graphic 1024x500 | Ready in `docs/play-assets/`, verified against spec sizes. |
| Store copy (6 locales) | One file per language in `docs/play-listing/` with Play locale tags (`en-GB`, `en-US`, `fr-FR`, `de-DE`, `es-ES`, `it-IT`), limits verified. Non-English needs native review. Remove AI clauses if v1 ships without AI. Per-locale feature graphics in `docs/play-assets/`; screenshots stay English until UI strings are externalised. |
| Privacy policy page | Published. Confirm the URL is set on the Play Console store listing. |
| Data safety form | Must declare AI photo transmission and any purchases. |
| Content rating, target audience, app content, app access, ads | Must all be answered in Console (no ads, no login, no health/finance/news features). |
| Developer organisation verification, payments profile | Required for the org account and for any billing SKU. |
| Pre-launch report, tester list, opt-in install check | After first upload. |
| Kotlin `NonCancellable` deprecation (`AppViewModel`) | Fixed in tree (cleanup now uses `withContext(NonCancellable)`); pending CI compile. |
| Native debug symbols warning | Accepted, no action. The only `.so` in the bundle is Jetpack's `libandroidx.graphics.path` (via Compose UI graphics, non-removable); Google publishes no symbols for it. All app code is Kotlin with obfuscation off, so app frames stay readable. |
| Emergency numbers per market | In tree (GB/US/DE/FR/ES + 112 fallback); pending CI tests. |
