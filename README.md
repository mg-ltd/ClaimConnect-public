# ClaimConnect Android

ClaimConnect 0.1.0 records a vehicle incident, retains a draft on the device and produces a user-controlled PDF evidence pack. It does not submit insurance claims or assess liability.

## Implemented journey

- Safety acknowledgement before creating an incident.
- Vehicle, driver, company, date/time, location, driver account and other-party details.
- Immediate queued autosave, draft history and reopening after app restart.
- System camera capture and Android photo picker; up to 10 original photographs, 15 MB each.
- Optional map snapshot of the current location with a car marker and 50 m accident-radius ring, stored as evidence (offline location diagram when tiles are unreachable; see [map snapshots](docs/MAP_SNAPSHOT.md)).
- Captions, original SHA-256 hashes, review/edit and incident deletion.
- Paginated PDF with the complete manifest and a display copy of every photograph (aligned tables and boxed text; see [PDF layout](docs/PDF_LAYOUT.md)).
- PDF preview, explicit confirmation before the Android Sharesheet and Save PDF copy through the system document picker.
- Driver defaults and local privacy/help information. Change something

## Build

Open this directory in Android Studio. Use JDK 17 and the checked-in Gradle wrapper (Gradle 9.6.0, AGP 9.4.1); dependency versions are pinned through `gradle/libs.versions.toml`.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest bundleRelease
./gradlew connectedDebugAndroidTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The normal CI release AAB is **unsigned**. Android 11 (API 30) is the minimum supported version; the project compiles and targets API 37.

## Architecture and storage

Kotlin, Jetpack Compose, ViewModel, Room and a repository. There is no backend; the only network use is downloading map tiles when the user explicitly taps "Add map snapshot" (see [map snapshots](docs/MAP_SNAPSHOT.md)), plus optional AI photo checks that send display copies to the Meta API only after the user saves their own API key in Settings and confirms a check. Room stores opaque IDs, timestamps and AES-GCM encrypted JSON payloads. Original photographs and retained PDFs are separately encrypted under a non-exportable Android Keystore AES-256 key; authenticated associated data binds every record/file to its ID. Room itself is not an encrypted SQLite implementation. Dates and IDs remain visible in database metadata; registration, names, location, narrative, captions, hashes and filenames are encrypted.

Original photograph bytes are retained unchanged. PDF images are scaled and EXIF-oriented display copies. Their manifest hashes refer to the originals, not the PDF image bytes. Hashes and app-added times do not verify authenticity, capture time, location or fault. Packs fail generation if any original cannot be decoded or its hash differs. An edit invalidates the current pack until regeneration. Cancelling the Sharesheet does not mark a pack as delivered; only a successful document save records `Saved copy`.

Filesystem writes use a temporary encrypted file, fsync and rename before a Room transaction. Startup reconciliation removes unreferenced encrypted files after interrupted imports/exports. A database delete removes the record first; its encrypted files are removed immediately or reconciled on the next launch. Device backup and transfer are excluded to avoid restoring encrypted data without its Keystore key.

Preview/share creates a plaintext PDF in the app-private cache with a scoped FileProvider URI and read grant. Temporary copies older than 24 hours are removed on a later app start. They are also removed when an incident is edited or deleted. External copies remain under the user's and recipient's control. A system camera app receives a temporary camera URI; its plaintext capture file remains in private cache until successful encrypted import, cancellation or discard. Pending captures are associated with their incident and can be retried after restart. A stable capture ID prevents duplication if a process ends after import but before cache cleanup. No routine logs or analytics collect incident data. Storage is capped at 200 MB plus transient capture/share cache.

## Verification

Automated tests cover validation, stale packs, original hashes, ciphertext substitution/tampering, reopening encrypted records, ten-photo PDF rendering, saving, deletion and the Compose journey. Physical-device camera, process-kill, reboot, document provider and low-storage checks remain release gates.

## Licence

Copyright (c) 2026 Melosome Group Ltd. All rights reserved. This hackathon submission is proprietary to Melosome Group Ltd — see [LICENSE](LICENSE). Access to this repository does not grant any reuse rights.
