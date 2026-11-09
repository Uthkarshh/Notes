# Phase 2 verification

Verified on **November 6, 2026**, using JDK 22.0.1, Gradle 9.6.1, and the NotesApp_API34 Android 14 emulator (1080 × 2400). Version **0.2.0** compiles against API 37, targets API 36, and uses Room schema version 2. The [Phase 1 baseline](phase1-verification.md) is retained separately.

| Check | Result |
| --- | --- |
| Final JVM unit tests | **122 passed**, zero failures/errors |
| Full emulator regression suite | **144 passed**, zero failures/errors/skips |
| Phase 2 checks after the scheduling and Markdown fixes | **26 passed**, zero failures/errors/skips |
| Final notification checks and screen-tour recheck | **7 passed**, zero failures/errors/skips; all **9 tour captures** retained |
| Debug and minified release APKs | Built successfully; release is unsigned |
| Android lint | Passed; zero errors, four existing warnings |
| Normal installed app smoke check | Cold launch, share capture, reminder scheduling, JSON export/import preview, and notification tap succeeded |

The full suite includes the original repository/UI coverage and new migration, transfer, notification, history, reflection, reminder, and share-capture tests. The focused run covers seven Phase 2 test classes; its cases overlap the full run and should not be added to that run as a count of distinct tests.

## Coverage and reliability checks

- A database built from the exported version 1 schema migrates to version 2 without losing its note or searchable content; Room validates the result.
- JSON replacement restores records, settings, history, tags, and links. Merge remaps IDs and relationships while preserving existing records. Invalid imports leave existing data intact. Search indexes are rebuilt transactionally.
- CSV handles quoted commas, multiline cells, BOMs, invalid input, archived items, and separate collections with the same name. Markdown preserves unchanged content exactly and imports edits to its visible heading/body while retaining other metadata.
- Document streams preserve UTF-8 text and reject oversized input. Markdown folders enforce a combined UTF-8 byte limit.
- Notification tests post through Android's notification manager, filter facts, deduplicate scheduled delivery, and avoid impressions when permission is denied. Stale, duplicate, and early reminder alarms cannot consume a different reminder.
- WorkManager retains a matching already-due occurrence when the app starts or resumes. Disabling notifications cancels pending work. The system clock follows timezone changes in the same process; calendar tests cover selected days and DST.
- UI tests exercise shared links, selected-text facts, reflection creation/skipping, linked-note navigation, reminder clearing, persistent selection history/cooldown, and notification intents opening both items and facts.
- Outside instrumentation, a real JSON document was exported through Android's picker, parsed, and reopened for an import preview. A reminder appeared in AlarmManager with a ten-minute window while exact-alarm access was off. A fact notification appeared in the system shade and opened its fact when tapped. Temporary records and the Downloads export were removed afterward.

## Lint advisories

The remaining warnings are `OldTargetApi` (target API 36), `AndroidGradlePluginVersion` (newer Gradle available), `DataExtractionRules` (no explicit Android 12+ extraction-rules file), and `UseKtx` (a URI style suggestion). These advisories also existed in Phase 1. The manifest sets `allowBackup=false`.

## Reports and artifacts

Retained evidence is under `build/phase2-verification/`:

- `full-build.log` and `full-device-results.xml`: full 144-test emulator run.
- `full-unit-results/`: unit reports from the full run, before the final regression additions.
- `phase2-build.log` and `phase2-device-results.xml`: successful 26-test Phase 2 review run.
- `final-build.log`: successful final APK, lint, and unit-test checks.
- `final-device.log`: final notification/tour recheck.
- `final-device-results.xml`, `final-unit-results/`, and `final-lint.xml`: retained final machine-readable reports.
- `normal-smoke.txt`, `normal-backup.json`, `normal-reminder-alarm.txt`, and `normal-*.png`: normal-app smoke-check evidence.

Installable debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Debug APK SHA-256: `D25D4AF26539ED98CC8D9D0B9F13E6F61D2342AC4A09B1A1D187D59FE215E80B`.

Unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.

Release APK SHA-256: `EA54F0C2575F31713EEA00F94B04042F1E5C42C2A519573AA372A169CD68401E`.

Standard HTML report locations and build commands are in the [README](../README.md). The [core tour](screenshots/README.md) contains 20 populated light/dark captures, and the [Phase 2 tour](screenshots/phase2/README.md) covers the new flows, including a separate normal-app notification capture. Usage and platform behavior are documented in the [Phase 2 guide](phase2.md).

## Practical limits

Runtime checks use the API 34 emulator and debug build. Physical devices, other API levels, tablet/foldable layouts, release-build runtime behavior, a real overnight notification, and a complete reboot cycle were not exercised. OEM power management can delay notifications and approximate reminders. Release signing is not configured. Later roadmap phases remain listed in the [README](../README.md#later-roadmap-phases).
