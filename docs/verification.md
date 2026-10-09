# Verification record

## Completed baseline verification

[Run 37865000482](https://github.com/iAdityaD/NovelVerse/actions/runs/37865000482), commit `f33b5cde9ef64cc906dad36ed9e4d0c47ac80580`, passed both CI jobs:

- Domain/parser unit tests, Android lint, debug APK and test APK compilation.
- API 35 emulator tests for Room/DataStore persistence, the Compose manual-library journey and the controlled search → import → live read → offline read → refresh → metadata restore journey.

These results do not certify a live third-party website. Fixtures are explicitly controlled test HTML; no fabricated source results are shipped in the app.

## Completed hardening verification

[Run 37897765213](https://github.com/iAdityaD/NovelVerse/actions/runs/37897765213), code commit `b4fba2b8306be547281b94090a9307b3c874270f`, passed both build and API 35 instrumented jobs on 9 October 2026.

- Unit tests, lint, debug/test APK compilation and strict exported-schema check passed.
- Instrumented regression tests validated schema 1 → 2 preservation, offline fallback after a primary-source change, retained bookmark versions, pause/cancel race protection and transactional rejection of inconsistent backups.
- Local SQLite inspection independently matched the migration's columns, foreign keys and indexes to Room's exported schema for all 14 tables.

The development APK is in the run's `android-build-and-reports` artifact. Reader anchor/bookmark UI changes compile and pass lint, but targeted reader UI interaction tests remain an open gate below. The verification-record update after this code commit changes documentation only.

## Release gates still open

- Real-device reader screenshots, TalkBack/navigation review, text-selection/gesture interactions and font/layout stress cases.
- Reader UI journeys for paging, bookmarks, rotation and source switching, beyond repository-level tests.
- Full process termination/relaunch and worker interruption, low storage and partial network responses.
- Authorized real-site integration certification, including continuation pages and source changes.
- Large-library/catalog benchmark, long-chapter memory/60 FPS profiling and background battery measurements.
- Notifications under denied permission, quiet hours/digest implementation, Android-version coverage and foreground download behavior.
- Restore conflict UX, encryption, source configuration security review and release signing.

The app remains a development preview. Green automated checks cover the tested behavior only; they are not a statement that all master-specification acceptance criteria are complete.
