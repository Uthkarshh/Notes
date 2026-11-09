# Phase 1 verification

Verified on **November 5, 2026**, using JDK 22.0.1, Gradle 9.6.1, and the NotesApp_API34 Android 14 emulator (1080 × 2400). The app compiles against API 37 and targets API 36.

| Check | Result |
| --- | --- |
| JVM unit tests | **99 passed**, zero failures/errors |
| Full emulator suite | **121 passed**, zero failures/errors/skips |
| Discover and screen-tour recheck after the final UI change | **7 passed** |
| Final tour with retained screenshot artifacts | **1 passed**, all **20 PNGs** exported |
| Debug APK | Built and installed successfully |
| Minified release APK | Built successfully; unsigned |
| Android lint | Passed; zero errors, four warnings |
| Normal app cold launch | Successful; no AndroidRuntime errors for the app process |

The full emulator suite includes 48 repository/persistence tests, 72 screen tests, and the screen tour. The final Discover change was then covered by all six Discover tests and the tour. The [screenshots](screenshots/README.md) include the visible Discover action card, loaded search results, and light/dark layouts.

## Completed fixes

- An editor ignores pending autosaves after an explicit delete, preventing deleted notes or facts from being recreated.
- A newly saved note/fact retains its record ID in saved state, so restoring its editor updates that record without creating a duplicate. Four regression tests cover deletion and restoration for both entry types.
- Discover brings a result and its actions into view after a draw, including when a long tag list pushes it below the screen.
- Debug lint analysis waits for generated Hilt sources to finish compiling. Combined build, lint, and emulator-test invocations now complete successfully.
- The tour waits for actual search hits and exports screenshots through Gradle's additional test output directory, preserving them after test APK cleanup.

## Lint advisories

The remaining warnings are `OldTargetApi` (target API 36), `AndroidGradlePluginVersion` (a newer Gradle distribution is available), `DataExtractionRules` (no explicit Android 12+ extraction-rules file), and `UseKtx` (a `Uri.parse` style suggestion). The manifest currently sets `allowBackup=false`.

## Artifacts

- Installable debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
- Build/test commands and standard report paths: [README](../README.md)
- Retained full-run results: `build/verification-results/full-device-results.xml` and `full-build.log`
- Focused UI results: `build/verification-results/final-ui-results.xml`
- Final build/lint/tour run: `build/verification-results/final-verification.log`
- Normal-launch screenshot: `build/verification-results/normal-launch.png`

Debug APK SHA-256: `55B7F89A95BBFAA6EEDDA2982A5CC67DBF260F3F0CD229C0A922564C88D64AA6`.

Release APK SHA-256: `FE859D92F2BB8D601509F76C3D6F1E7A66F3CC8ADA31BB32D7FB7156BCF1123B`.

Runtime verification used the API 34 emulator and the debug build. Physical devices, other Android API levels, tablet/foldable layouts, and release-build runtime behavior were not exercised. Later roadmap phases remain outside this MVP; see the [scope and future work](../README.md#later-roadmap-phases).
