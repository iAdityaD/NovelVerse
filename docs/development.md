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

- Compose Library, Explore, Downloads, Settings, details/catalog, reader, updates and backup routes.
- Persistent manual library entries, search, grid/list layout, status, archive and restore.
- Declarative JSON CSS adapters: import/export with Android's document picker, validation, parser testing, enable/disable, real HTTP search and catalog import.
- Canonical novels with explicitly linked alternatives; exact descriptive-title matching and user confirmation for unresolved chapters.
- Lazy paragraph scrolling and measured horizontal pagination, adjustable font size, theme, persisted paragraph/page anchors, previous/next navigation, selection and bookmarks with notes.
- Manual source selection for one chapter, from a boundary onward, or as the novel's primary source. Optional fallback tries confirmed alternatives after a failure.
- Immutable chapter versions and transactional text blocks. Deliberate offline pins are separate from a bounded 50 MiB temporary cache. Bookmarked versions survive cache cleanup.
- Durable WorkManager download queue with pause/resume/cancel/retry and three-attempt retry limit. Current worker concurrency is one.
- Manual catalog refresh, deduplicated release history, per-novel schedule storage and staggered background checks. Basic Android notifications open the corresponding chapter.
- Metadata-only local backup/restore, including preferences, source definitions, notes, relationships and progress. Website text is excluded.
- Local-only mode blocks new transport requests; downloaded content remains available. No mandatory account, telemetry or cloud backend.

## Source configuration

Explore → Add source accepts a version-1 JSON definition. The form includes a template using the reserved `fiction.example` domain; it is not a live integration. Replace its URL and selectors with rules for an authorized site, test a novel URL, save, then verify search separately. Only public HTTPS HTML on one origin is currently supported. CSS selectors must be site-specific.

Available fields: `schemaVersion`, `id`, `name`, `baseUrl`, `searchPath`, `queryParameter`, `searchItem`, `searchTitle`, `searchLink`, `novelTitle`, `novelAuthor`, `catalogItem`, `catalogLink`, `chapterContent`, `catalogNext`, `chapterNext`. Empty continuation selectors mean a single page; an incorrect selector can therefore omit continuation content. Test the complete chapter before relying on a source.

Requests use bounded bodies/timeouts, same-origin redirects, private-address checks, conservative robots checks, two-second per-source spacing and bounded retries. JavaScript rendering, authentication, custom cookies/headers, POST searches and cross-origin chapter hosts are unsupported. Restrictions are reported rather than bypassed. Changing a domain requires a new source ID, then linking it to the existing novel.

## Limits and remaining work

This preview does **not** fulfill the entire master specification. Outstanding work includes:

- Seamless multi-chapter vertical append, adjacent prefetch, rich text preservation, quote-based anchors, comprehensive typography/gestures/accessibility controls, highlights, TTS and reader search.
- Source removal/priority/health management, complete capability-driven adapters, robust renumbering/split-chapter reconciliation and multi-source release consolidation. Current release discovery adds new trailing entries from the first linked catalog only; uncertain alternatives remain unresolved.
- Collections/tags, covers, analytics, more library filters and large-library/catalog paging. Library windows currently stop at 1,000 matching novels; catalogs at 10,000 chapters.
- Configurable download concurrency/network/storage policies, grouped storage statistics and automatic downloads. Long-running download foreground execution requires further platform work.
- Quiet hours, digest/action notifications, richer per-novel schedule UI and background failure history.
- Encrypted backup, restore preview/conflict review, automatic backup and process-death recovery journaling. Database restore is transactional, but preferences are restored separately. Existing rows win on restore; conflicting source relationships are rejected. Backups are plaintext and must be protected by their owner.
- Real-device performance, battery, accessibility and security validation across supported Android versions. Pagination currently materializes one bounded chapter, not a streaming book. Chapters are limited to one million characters, ten continuation pages and two MiB per HTTP response; catalogs to thirty pages.

No live website integration has been certified. Fixture tests establish controlled parser/repository behavior, not permission or compatibility for arbitrary sites. Source configuration is an advanced JSON workflow, not yet a polished selector wizard.

## Schema discipline

Room schema 1 contains canonical library/source/chapter/progress metadata. Schema 2 adds immutable content versions and text blocks, bookmarks, transfer state, source policies, refresh targets and release events. The explicit `MIGRATION_1_2` retains existing IDs and progress; there is no destructive fallback. Exported schemas are checked in and validated in CI. Development snapshots of unreleased schema 2 are not a supported upgrade path; only schema 1 → final schema 2 is tested.

Foreign keys restrict deletion of durable user entities. Text blocks cascade only when their unpinned, unbookmarked content version is explicitly evicted. Removing a novel from Library archives it without deleting content or notes. Uninstalling removes local data; export a backup first. System backup/device transfer remain disabled.

## Verification

Run the build command above plus `:core:data:testDebugUnitTest`. Connected tests use permitted in-memory HTML fixtures, Room and DataStore persistence, migration validation and a Compose library journey. See the verification record for completed runs. Activity recreation is not full process-death testing, and a green build does not establish smooth 60 FPS reading or production readiness.
