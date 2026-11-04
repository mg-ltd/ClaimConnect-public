# ClaimConnect roadmap

This tracks known gaps and follow-ups from porting ClaimConnect's app logic (model, security,
data, evidence, export, UI) from `codex/claimconnect-v0.1` onto `main`'s bleeding-edge toolchain
(Gradle 9.6.0, AGP 9.4.1, Kotlin 2.2.10, compileSdk/targetSdk 37, minSdk 30). It is not a release
checklist — see `docs/PILOT_RELEASE.md` for Play Store signing and listing steps, and
`docs/DEVICE_QA.md` for the physical-device test script.

## Near-term follow-ups

- **CI workflows.** `codex/claimconnect-v0.1` has `.github/workflows/android.yml` (build + test on
  push/PR) and `pilot-release.yml` (signed bundle). Neither has landed on `main` yet. Both are
  version-agnostic (pinned action SHAs, Java 17 via `setup-java`, no hardcoded Gradle/AGP/SDK
  versions), so porting them should be a drop-in addition once prioritised.
- **Run instrumented tests on a device or emulator/CI.** `RepositoryTest.kt` and
  `CaptureFlowTest.kt` currently only compile (`assembleDebugAndroidTest`); they have not been
  executed, because no emulator/device was available while porting. Run them on a real device or
  an emulator-backed CI job before relying on them as a safety net.
- **Play Console Data safety declaration.** The location permission, the AI share-intent
  handoff, and now the `INTERNET` permission with map tile fetches (approximate location
  transmitted to OpenFreeMap on explicit "Add map snapshot" only) are new since the last pilot
  prep; `docs/PILOT_RELEASE.md` step 5 (Data safety / app content declarations) needs to be
  redone against the current shipped binary before another pilot upload.
- **Optional bring-your-own-key (BYOK) AI mode.** Today "Ask an AI app" only hands
  data to an installed assistant app via Android's share sheet (no API keys, no network code, no
  cost to the project). A future option could let a user paste their own OpenAI/Anthropic-compatible
  API key in Settings for in-app analysis — deferred because it adds network permission, key
  storage, and cost/liability surface that the share-intent approach avoids.
- **Voice narration (transcription).** Spoken incident account planned in
  `docs/hackathon/07-voice-narration.md`: native `SpeechRecognizer` dictation
  in the base app first, Muse Voice Transcribe via the production proxy as a
  metered Phase 2 upgrade. Blocked on the two feasibility checks logged in
  that file (device check, `curl` probe); folds into the Data safety redo above.

## Open technical debt

- **KSP vs. AGP 9 built-in Kotlin.** `gradle.properties` carries
  `android.disallowKotlinSourceSets=false` as a temporary escape hatch because KSP's Gradle plugin
  still registers sources via the legacy `kotlin.sourceSets` DSL, which AGP 9's built-in-Kotlin
  model otherwise rejects. Remove this flag once KSP ships native built-in-Kotlin support upstream.
- **Room schema churn.** `app/schemas/app.melosome.claimconnect.data.ClaimDatabase/1.json` is the
  only exported schema version so far; any future entity change needs a schema bump and a Room
  migration, not just a version bump.

## Not yet decided

- Settings (top bar) and Privacy (nested under Settings) are intentionally not bottom-nav tabs.
  Incidents, Emergency and Reports are now all real tabs — every bottom-nav slot has content;
  Emergency's standard numbers are per-market with a locale default (see the note at
  the end of this list); Brazil, LatAm Spanish and further countries are future
  considerations once UI strings are externalised.
- Whether additional AI assistant integrations (e.g. a dedicated "copy incident summary" action
  for assistants that don't accept image attachments via share) are worth the UI surface.
- Emergency numbers are per-market (GB, US, DE, FR, ES, IT, CA, IE, AU, NZ, MX, IN
  plus a 112 fallback) with an automatic device-locale default and a manual override.
  Labels stay in English for v1; localising them is a follow-up.
- Local backup/restore: shipped (same-device only). Settings has "Back up to a file" / "Restore
  from a file", backed by an encrypted zip export that merges on restore (skips records already on
  the device, by id). Android's OS-level Auto Backup (`allowBackup`) stays off deliberately, because
  the signing/Keystore key behind record encryption is hardware-bound and would not survive a
  device swap. A portable, passphrase-based backup (usable across devices) is a future option.
- App icon and brand colour: shipped. The adaptive launcher icon's `foreground`/`background` now
  use the user-supplied artwork (`docs/Claim Connect App Icon 1024px.png`, resized losslessly to
  the standard mipmap densities, no redraw — the "CLAIM CONNECT" wordmark is kept as-is); the old
  shield+checkmark vector moves to the `monochrome` slot only. The icon's true brand teal
  (`#49969D`) is now the seed for a full Material 3 tonal-spot colour scheme (`ui/Theme.kt`,
  light + dark), superseding the old `#006A60` placeholder.
