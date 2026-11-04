# ClaimConnect functional go/no-go review

**Assessment date:** 9 October 2026  
**Decision:** **NO-GO for live functional use**

> **Superseded in part (26 October 2026):** the 9 October test table and findings below are the historical baseline. Where they say `RepositoryTest` cannot run, or that location/telephony lint fails, see "A1 (#4) evidence" at the end of this file for current results. The overall decision remains NO-GO until #5–#7 complete.

The core incident journey works in the current emulator test, but the app is not yet functionally proven for live use. AI photo sharing is broken by its cache/FileProvider configuration, the repository test class cannot run, and the physical camera, external PDF actions, process recovery and low-storage requirements remain unverified.

This decision covers user-facing behaviour, data integrity and recovery only. App signing, bundle publication and store administration are outside this review.

## Source-folder correction

The Kotlin source roots were renamed without changing package names or application logic:

- `app/src/main/java` to `app/src/main/kotlin`
- `app/src/test/java` to `app/src/test/kotlin`
- `app/src/androidTest/java` to `app/src/androidTest/kotlin`

A clean Gradle run successfully discovered and compiled all three new roots. No source-set override was needed.

## Test results

| Test | Result | What it proves |
| --- | --- | --- |
| `./gradlew clean testDebugUnitTest assembleDebug assembleDebugAndroidTest` | **Pass** | The renamed Kotlin roots are valid; app, JVM tests and instrumented tests compile; the debug APK and test APK assemble. |
| `IncidentTest` | **6/6 pass** | Required-field validation, date/time validation, pack invalidation, SHA-256, fixed emergency numbers and archived status. |
| `CaptureFlowTest` on API 36 emulator | **Pass** | Onboarding persistence across activity recreation, incident capture, autosave progression, one photo import, pack generation, completion choices, share cancellation, return home, report archive/restore, deletion and onboarding replay. |
| Complete `connectedDebugAndroidTest` | **Fail** | `RepositoryTest` is rejected during JUnit initialisation, so none of its eight repository tests run. |
| `lintDebug` | **Fail: 2 errors, 15 warnings** | A location permission boundary and telephony device compatibility are unresolved. These are functional/device concerns, not packaging concerns. |

Current local reports:

- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`

## Functional requirement coverage

| Requirement | Status | Evidence or gap |
| --- | --- | --- |
| Onboarding appears on first run and stays dismissed | **Pass** | The UI test dismisses it, recreates the activity and confirms it does not return. The state is stored in app preferences. |
| Onboarding can be opened again from Settings | **Pass** | Exercised by `CaptureFlowTest`. |
| Safety acknowledgement before incident entry | **Pass** | Exercised by `CaptureFlowTest`. |
| Guided incident entry and validation | **Pass with limited coverage** | The main required fields and happy path pass. Invalid date/time and incomplete records are covered by JVM tests. Long text, extreme values and every optional field are not exercised through the UI. |
| Autosave and navigation guard | **Partial** | The normal journey progresses and survives activity recreation after generation. Force-stop during typing, immediate navigation during a pending write and retry after a save failure are not tested. |
| Reopen saved draft after restart/reboot | **Not proven** | The repository test intended to cover database reopen cannot run; process restart and reboot remain manual device checks. |
| Import and retain original photographs | **Partial** | One synthetic photo imports and displays. Camera capture/cancellation, picker limits, ten photos, rotation, corrupt files and process death in the camera are not currently proven. |
| Generate a complete, readable PDF | **Partial** | Pack generation reaches the completion screen. The repository test intended to render and inspect a ten-photo PDF cannot run. |
| Completion prompt offers Preview, Save as PDF and Share PDF | **Pass** | All three actions are asserted by `CaptureFlowTest`, including survival across activity recreation. |
| Completion ends the journey and allows return to Home | **Partial** | Explicit **Back to home** and share cancellation are exercised. Successful preview, document save and external share return paths require controlled intent/provider tests or physical-device verification. |
| Save marks the pack as `Saved copy` only after a destination is written | **Not proven end to end** | The implementation updates status after `saveCopy`, but the repository test is blocked and the Compose test does not complete the system document picker. |
| Share cancellation does not claim delivery | **Pass at UI level** | The confirmation is cancelled and the completion prompt remains. Recipient-app behaviour is not tested. |
| Reports list supports real archive, hide and restore behaviour | **Pass** | The report leaves the active list, appears only after expanding Archived reports, and returns to the active list after restore. |
| Delete removes an incident and returns Home | **Pass at UI level** | Exercised by `CaptureFlowTest`. Removal of every database/file/cache artefact is intended to be covered by the blocked repository test. |
| Encrypted records survive reopen and reject tampering | **Not currently proven** | Tests exist, but the containing `RepositoryTest` class cannot initialise. |
| Same-device backup and restore | **Not currently proven** | The existing round-trip test cannot run. Malformed and interrupted restore behaviour has no test. |
| Emergency calling works with and without permission/telephony | **Not proven** | No automated device test. Lint reports that `CALL_PHONE` implicitly restricts devices without declared optional telephony hardware. |
| Current-location helper degrades safely without permission/provider | **Not proven** | No test covers denied/revoked permission or disabled providers. Lint reports an unresolved `MissingPermission` call. |
| AI handoff shares selected incident text and photos | **Fail** | See blocker 1 below. |

## Blocking functional findings

### 1. AI photo sharing cannot complete and its plaintext cache is not cleaned

`AppViewModel.shareWithAi()` writes decrypted photographs to `cacheDir/share/`. The UI passes those files to `FileProvider.getUriForFile()`, but `app/src/main/res/xml/file_paths.xml` exposes only `camera/` and `shared/`. For an incident with photographs, URI creation fails before the chooser can open.

The same `share/` directory is missing from incident deletion and the 24-hour startup cleanup, which handle only `shared/` and `camera/`. Decrypted evidence can therefore remain in private cache until Android or the user removes it.

Required improvement:

- use a narrowly scoped FileProvider path for AI-share files;
- isolate files by incident or share session;
- clean them after the handoff where possible and through bounded startup cleanup;
- remove related files when an incident is edited or deleted;
- add an instrumented test that resolves every generated URI and verifies cleanup.

### 2. Repository behaviour is hidden by an invalid test signature

`RepositoryTest.backupMergeRestoresADeletedIncidentWithItsEvidenceAndPack()` ends with `backupFile.delete()` inside an expression-bodied `runBlocking`. The resulting test method returns `Boolean`, but JUnit 4 requires `void`/Kotlin `Unit`. JUnit rejects the whole class before running any repository test.

Make the method explicitly return `Unit`, then rerun the complete suite. Until it passes, database reopen, encryption persistence, tamper rejection, archive persistence, backup/restore, ten-photo PDF integrity, stale-pack invalidation and full deletion are not verified in this assessment.

### 3. Permission and device compatibility paths need resolution

`lintDebug` reports:

- `DeviceLocation.kt:33`: `getLastKnownLocation()` lacks a permission check that lint can verify;
- `AndroidManifest.xml:7`: `CALL_PHONE` implies required telephony hardware, excluding devices unless telephony is explicitly optional.

Make the location permission boundary explicit at the point of use and test denial/revocation. For emergency numbers, prefer opening the dialler if direct calling is not essential; otherwise declare telephony optional and verify useful behaviour when no phone app or telephony hardware exists.

### 4. Critical external and recovery flows have not been exercised

An emulator Compose test cannot prove the physical camera, process death while another app is open, reboot recovery, local/cloud document providers, PDF viewers, share recipients, low storage, TalkBack or OEM-specific behaviour. Complete the functional checks in `docs/DEVICE_QA.md` with synthetic data and record device model, Android version and result.

## Further improvements

1. **Make restore bounded and atomic.** `BackupManager.restore()` reads ZIP entries fully into memory without entry-count or uncompressed-size limits, then applies rows/files incrementally. A malformed or interrupted restore can exhaust memory or leave partial records. Validate and cap the complete input, authenticate referenced ciphertext, stage output, then commit or roll back as one operation.
2. **Test actual completion actions.** Add intent/provider tests for PDF preview, successful save, cancelled save, successful share launch and unavailable target apps. Assert that only a successful save sets `Saved copy` and that the user lands on Home after a successful chosen action.
3. **Cover autosave failure modes.** Test leaving while a write is pending, injected write failure, retry, force-stop immediately after typing and reopen after process recreation.
4. **Expand accessibility coverage.** Verify content descriptions, focus order, touch targets, large font, keyboard navigation and TalkBack for the editor, photo cards, completion dialog and archived reports.
5. **Resolve the remaining `AppViewModel` risks.** `docs/AppViewModel.md` records main-thread file work, an unbounded command queue and callback/event coupling that can affect responsiveness and recovery under heavier evidence loads.

## Functional go criteria

Change this decision to **GO** only when one clean candidate build demonstrates all of the following:

- AI photo sharing opens successfully for incidents with photos and temporary plaintext is cleaned.
- All JVM and instrumented tests pass, with every `RepositoryTest` method executed.
- Lint has no permission or device-compatibility errors.
- Ten-photo PDF generation, rendering, saving, previewing and sharing pass with content/hash checks.
- Draft autosave and recovery pass across navigation, force-stop and reboot.
- Camera/picker cancellation and process-death recovery do not lose or duplicate evidence.
- Archive, restore and deletion remove or retain the correct records and files after restart.
- Backup/restore passes normal, duplicate, malformed, interrupted and low-storage cases without partial data.
- The applicable physical-device checks in `docs/DEVICE_QA.md` pass, including accessibility and external-app/provider flows.


## A1 (#4) evidence — 26 October 2026

**Decision remains NO-GO overall**; this records observed A1 results only. Devices: emulator `Pixel_8a` (API 34, full `connectedDebugAndroidTest` green: 17/17) and `Medium_Phone_API_37.0` (API 37).

| Check | Result |
| --- | --- |
| `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest bundleRelease` | **Pass** (lint has no errors) |
| `RepositoryTest` (`connectedDebugAndroidTest`) | **8/8 executed and passed** |
| `AiShareAndPermissionsTest` (new) | **8/8 pass**: FileProvider URIs readable for 1 and 10 photos; files outside share paths rejected; independent per-session expiry; stale-session sweep; edit/delete remove only that incident's plaintext; failed materialisation leaves nothing; denied then coarse-only location; `ACTION_DIAL` and no-dialler handling |
| `CaptureFlowTest` | **Pass on API 34**; fails on API 37 with `NoSuchMethodException: InputManager.getInstance` (Compose/Espresso incompatibility, not an app defect) |

Plaintext AI-share strategy for v1 (best-effort, not guaranteed): each share is its own session with a 15-minute recipient-read grace period (`IncidentRepository.AI_SHARE_GRACE_MS`). An in-process timer deletes each session when it expires, copies are deleted immediately if the chooser cannot open, and a sweep on app start and every `onStart` deletes anything expired. If Android kills the process, decrypted copies can remain in the app-private cache until the app next starts; there is no guaranteed maximum lifetime. The files are only readable by apps granted a URI by the share intent. Optional AI image sharing can be disabled for v1 if this residual risk is not acceptable.

**Residual risk:** no durable (WorkManager/alarm) job, so plaintext can outlive its grace period after a process kill until the next app start; revocation of an already-granted location permission (which kills the process) is not exercised by an automated test; physical-device and CI evidence are still outstanding.

**Additional A1 changes:** `DeviceLocation` now skips GPS when only coarse permission is held and tries each enabled permitted provider in turn (JVM-tested in `DeviceLocationTest`). The CI (`android-ci.yml`) and release skeleton (`android-release.yml`) workflows are included; the release workflow signs only in the protected `pilot` environment using `CC_KEYSTORE_BASE64`, `CC_KEYSTORE_PASSWORD`, `CC_KEY_ALIAS` and `CC_KEY_PASSWORD`, and no Play upload is wired yet (#8/#9).

**Unverified release gates deferred to #7 (not verified by A1):**
- Cross-app URI grant: `AiShareAndPermissionsTest` reads through ClaimConnect's own `ContentResolver`, so it proves the FileProvider mapping only. A real recipient app on a physical device must confirm `ACTION_SEND_MULTIPLE` + `ClipData` + read flag grants to a different UID.
- API 37 `CaptureFlowTest` failure (`InputManager.getInstance`) needs a stack-trace investigation.
- Release authority must decide whether best-effort plaintext retention is acceptable for optional AI sharing in v1, otherwise the feature is disabled.
- Test notes: `locationIsUnavailableWithoutPermissionAndAvailableWithCoarseOnly` uses `assumeFalse` and is reported as skipped if location is already granted; it leaves coarse permission granted because revoking a granted permission kills the instrumented process. Skipped tests are not counted as passes.
