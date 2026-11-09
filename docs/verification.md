# Phases 3 and 4 verification

This is the historical feature-phase baseline. Current redesign changes and their separate checks are recorded in the [milestone 1](redesign-milestone1.md), [milestone 2](redesign-milestone2.md), [milestone 3](redesign-milestone3.md) and milestone 4’s [adaptive](redesign-milestone4.md) and [motion/Board quality reports](redesign-milestone4-polish.md).

Verification date: **November 6, 2026**. Version **0.4.0** uses JDK 22.0.1, Gradle 9.6.1, compile API 37, target API 36, and Room schema 2. Device checks use the NotesApp_API34 Android 14 emulator (1080 × 2400). The [Phase 1](phase1-verification.md) and [Phase 2](phase2-verification.md) reports remain historical baselines.

| Check | Result |
| --- | --- |
| JVM unit tests | **144 passed**, zero failures/errors |
| Initial full emulator regression | **165 passed / 166 run**; one rating test checked UI before the Room update arrived |
| Final affected device checks | **26 functional checks passed**, including the corrected rating test |
| Isolated screen-tour recheck | **1 passed**, zero failures/errors; all **15 Phase 3/4 captures** retained |
| Debug and minified release APKs | Built successfully; release is unsigned |
| Android lint | Passed: **zero errors, eight advisories** |
| Normal installed-app smoke | Current working build checked separately; see the [redesign report](redesign-milestone1.md) |

The initial full run includes all new features and the existing regressions. The rating assertion now waits for the displayed value as well as persistence. The final affected run passed all 26 functional cases; its screenshot tour received a null image from Android's capture service, so the tour was rerun successfully in isolation. These runs overlap and cover **166 distinct device cases**; their counts should not be added together. A combined build also had an emulator API-detection timeout under host memory pressure, before any app tests ran. The final build/check invocation succeeded using two workers and a smaller temporary JVM.

Lint advisories concern target API 36, newer Gradle/Coil versions, and Kotlin extension style. The tile's Intent overload is used only below API 34; newer versions use the PendingIntent overload, as specified by [Android's TileService API](https://developer.android.com/reference/android/service/quicksettings/TileService).

## Coverage

- Typed metadata validation, unknown JSON-field preservation, searchable metadata, duration/price sorting with missing values last, constrained discovery filters, and voice-command parsing.
- Provider-response fixtures and opt-in enforcement. Lookup never requires a network connection for manual editing; failed optional cover downloads retain applied metadata and explain the partial result.
- Authenticated archive round trips with randomized salt/nonce, wrong-passphrase and tampering rejection, bounded input/KDF parameters, record/image replacement, and validation before data mutation.
- Android Keystore-wrapped credentials and a real SAF DocumentsProvider: automatic archives decrypt successfully, retention keeps the configured count, and manual/foreign-device files survive cleanup.
- App-lock content guards, share/capture entry points, secure-window flags, and actual Android notification content redaction. Normal-app authentication is checked separately from the instrumentation session guard.
- A real Android AppWidgetHost inflates both Glance widgets. The fact widget displays its saved fact, then removes it when the lock setting is enabled even if the app session remains unlocked. The capture widget exposes text, voice, and pick actions.
- Monthly/year completion cohorts, calendar-day streaks, reading/watching streaks, active tags, future timestamps, timezone boundaries, and forgotten-backlog archive rechecks.
- Existing Room migration/import, notifications/reminders, reflections, history/cooldown, capture, editor persistence, collection, search, and navigation regressions remain part of the full device suite.

## Normal installed-app smoke check

The schema 3 milestone 1 build was installed and its Today/navigation hierarchy checked separately during the [redesign milestone 1 verification](redesign-milestone1.md). This does not retroactively validate the historical APK hashes below.

## Reports and artifacts

Evidence is retained under `build/phase34-verification/`. The first full device XML is preserved under `full-device-results/`; focused reports overlap that run and must not be added as distinct tests. The final build log, unit XML, lint XML, focused device XML, and normal smoke evidence are retained separately.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Debug APK SHA-256: `AA329E95082CDF2EE01DA1C7BF5BCC6E8C1EFF6052C0DE633DBF5094FC914E11`.

Minified unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.

Release APK SHA-256: `6C74A9807A13A544C0E7C9812E02C556640395D87F151C8CAED919E253E6BECB`.

Commands and standard HTML report paths are in the [README](../README.md). The [Phases 3/4 screen tour](screenshots/phase34/README.md) contains fifteen captures, including actual hosted widgets; the [core](screenshots/README.md) and [Phase 2](screenshots/phase2/README.md) tours are also retained. Locked app screenshots are intentionally blocked.

## Practical limits

Runtime verification uses the debug build on API 34. Physical biometric hardware, other API levels, tablets/foldables, release-build runtime, and an overnight scheduled-backup interval are not verified. Immediate scheduled work and direct folder backup are checked without waiting a day. Android battery policies may delay future work.

No Google Drive account/provider or TMDB token is configured in the emulator. SAF provider integration, encrypted restore, and retention are tested; live Google Drive upload and authenticated TMDB calls are not claimed. The app uses a user-selected local/cloud document-provider folder for backups, as specified in the original roadmap, with no automatic two-way record synchronization. Actual speech transcription depends on an installed Android recognizer; parser behavior and typed fallback are verified without recording microphone audio.

The app lock guards app access and external display surfaces; it does not separately encrypt Room files. Encrypted archives include owned covers and omit API/device credentials. JSON/CSV/Markdown exports remain plaintext. Release signing and broader device validation remain release-preparation work. Usage, setup, security boundaries, and format limits are documented in the [Phases 3/4 guide](phase34.md).
