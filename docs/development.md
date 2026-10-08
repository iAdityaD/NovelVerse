# Development guide

## Toolchain

- JDK 17
- Android SDK platform 36 and build tools 36.0.0
- Gradle 8.13 via the committed wrapper
- Android Gradle Plugin 8.11.1, Kotlin/Compose compiler 2.1.20
- Minimum Android API 26; target API 36

Versions are pinned in `gradle/libs.versions.toml`. The AGP/Gradle pairing follows [Android's AGP 8.11 compatibility table](https://developer.android.com/build/releases/agp-8-11-0-release-notes). This is a compatible baseline, not a claim to use every latest library release.

## Build and run

Open the repository in an Android Studio version supporting AGP 8.11, install SDK 36, and let Gradle sync. Select `app` and run on an API 26+ device. Alternatively set `ANDROID_HOME` or `sdk.dir` in untracked `local.properties`, then run:

```sh
./gradlew :core:domain:test :app:lintDebug :app:assembleDebug
./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Connected tests require a running emulator/device. CI uses API 35. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. This is an unsigned-for-release development build; no release key or production publishing is configured.

## Implemented scope

- Native Compose navigation: Library, Explore, Downloads, Settings and contextual add/details screens.
- Manual novel creation with bounded input validation and UUID identity independent of title or website.
- Room-backed library, title/author search, grid/list presentation, reading status and reversible removal.
- Removed novels remain accessible through Settings for restoration.
- DataStore-backed theme, library layout, reader mode and font-size defaults; sample text previews size.
- Hilt dependency injection, pure JVM model/domain modules, repository ports and observable UI state.
- Source-adapter contract, typed failures, bounded structural definition validation. **No HTTP adapter is implemented or enabled.**
- Initial Room v1 canonical/source/chapter/mapping/progress tables with composite foreign keys against cross-novel mappings.
- Unit, persistence and UI journey tests; CI build/lint/device-test jobs.

## Not implemented yet

Website configuration UI, source search/extraction, chapter import, live/offline reading, pagination, continuous reader, source matching, download engine, scheduler, notifications, annotations, content storage, backup/restore and analytics. Explore and Downloads explicitly disclose their unavailable preview status. Reader controls in Settings persist defaults only; they do not claim a working reading engine.

Phase 1 does not claim a completed storage/reader feasibility gate. Those experiments and real-device performance evidence remain required before selecting the final rendering/content format. Source-definition validation is structural only: it is not a substitute for CSS compilation, DNS/IP restrictions, redirect validation, authorization checks or live parser tests.

## Data and privacy

No network permission, telemetry, account, seeded stories or fake releases. System backup and device transfer are excluded deliberately until a reviewed backup format exists. Uninstalling removes local data. Removing from Library only archives the entry; no purge UI exists in this milestone. Same-title novels are deliberately permitted because title similarity is not identity.

Library reads are bounded: load 60 at a time up to 1,000 matching rows; search narrows the selection. Removed entries are bounded to 1,000. This foundation does not yet meet the large-catalog performance acceptance criterion; Paging/keyset navigation will replace this early query window before scale validation. Reader content and source entity write APIs are intentionally not exposed yet.

## Schema discipline

Room schema v1 is exported under `core/data/schemas`. There is no destructive fallback. Future schema versions must include tested migrations and retain canonical IDs. The full blueprint describes future tables; only milestone tables are present now. There is no v0-to-v1 migration because this is the first schema. Foreign keys use RESTRICT for durable entities. Search treats `%`, `_`, and backslash literally, not as user-injected LIKE patterns.

## Test responsibilities

- Domain tests: reject invalid titles/metadata before persistence; same-title novels retain distinct IDs; validate source contract inputs.
- Device persistence tests: close/reopen Room, archive/restore, literal search, cross-novel progress rejection, DataStore recreation.
- Compose journey: add manual novel, recreate activity, change status, remove and restore.

Activity recreation is not full process-death validation. Device tests and profiling across the supported Android range remain release gates. Validation results are recorded separately in `docs/verification.md`; adding a test does not mean it passed.
