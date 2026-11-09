# UI/UX redesign — milestone 1

Implementation and verification record · November 6, 2026 · development version 0.4.0, Room schema **3**.

The first implementation increment addresses editing trust, urgent Today actions and contextual lookup setup. It follows the [redesign proposal](design/README.md). The original product's feature phases and this redesign's milestones are separate roadmaps.

## Implemented behavior

- **Unified Item Detail autosave.** Title, notes, link, tags and category fields use a shared draft/session. Valid text edits commit after 500 ms; workflow, rating, priority, favorite, reminder, cover and enrichment actions serialize after pending valid edits. The separate Save details button is removed.
- **Recoverable incomplete edits.** A private `item_drafts` table journals raw values. An invalid/incomplete number retains its last valid canonical value while other valid fields save. Reopening restores the unfinished input and its field error. A blank title preserves the prior saved title. Drafts are excluded from search and portable record exports and cascade away with a deleted item.
- **Save feedback and failure recovery.** Saved on device, Saving, field errors and Retry distinguish committed data from unfinished edits. Older acknowledgements cannot clear newer edits. Back flushes eligible changes; the application scope can finish queued local writes after an editor closes. Back during initial loading/failure remains available because no input has yet been accepted.
- **Undo for major changes.** Status, collection moves, archive, reminder, cover and enrichment changes expose Undo through a shared feedback host. Restoration checks changed values before reverting them, retains unrelated newer edits, and restores completion/reminder state. Confirmed hard deletion remains the existing behavior until the reversible deletion boundary in milestone 2.
- **Today hierarchy.** A dismissible Inbox prompt precedes Due Soon and Up Next. Due items expose Complete/Snooze; active items expose Resume/Complete. The groups initially show three entries, support View all, and avoid duplicate items. Terminal/archived reminders are excluded. A foreground clock refresh and resume refresh update the dashboard.
- **Compact reading and discovery.** Morning Fact uses a compact editorial card, Next and swipe cycling. One lightweight Pick something for me action replaces the four-button Today block.
- **Contextual metadata consent.** Find book/movie details stays available when lookup is off. The in-place sheet explains the provider and data sent, offers Enable & search or Keep editing offline, and accepts a missing TMDB token without a settings detour. Token storage and persisted consent finish before the first search request. Cover download remains explicit.
- **Theme foundation.** Explicit typography/shapes, AMOLED text/outline roles and theme-aware rating/favorite icons replace the fixed gold meaning-bearing tint.

## Persistence and compatibility

Schema 2 → 3 adds only the draft table and its cascading item foreign key. The schema 1 → 2 migration remains registered, allowing existing schema 1 and 2 databases to upgrade. Canonical item patches read the latest row and preserve unrelated workflow fields and unknown metadata JSON keys. Item, tags and FTS writes remain transactional.

Draft journaling is asynchronous; Saved is shown only after eligible edits acknowledge. A forced termination before a journal write acknowledges can still lose those most recent keystrokes. Canonical backups contain committed records and owned covers; unfinished raw drafts are local recovery state. The App Lock and existing external-surface redaction rules continue to apply.

No dependency versions or global compiler settings were changed. This environment required an approved sandbox exception for compiler/SDK artifact access; checks use JDK 22.0.1 with in-process Kotlin compilation. The initial offline attempts stopped on uncached plugin/aapt2 metadata; a sandboxed attempt also encountered filesystem access denials. Those attempts are not counted as validation passes.

## Verification

| Check | Result |
| --- | --- |
| JVM suite | **151 passed**, zero failures/errors, including seven new editor session/validation cases |
| Debug app and test APKs | Built successfully |
| Focused API 34 device suite | **57 passed**, zero failures, on the final APK |
| Android lint | **Zero errors, eight existing advisories** |
| Normal installed app | Final APK cold launch succeeded; Today/four-destination hierarchy also confirmed during this increment |

The normal app's initial startup wait/UI dump timed out while compilation was active. A subsequent dump succeeded and showed the new Today hierarchy and global actions. The final APK's cold launch returned `Status: ok` (4.4 seconds on this emulator); its immediate accessibility dump needed a retry, which then confirmed Today, the global actions and all four destinations on the final build. This is a normal-installed-app navigation smoke check, not a substitute for the isolated feature tests or a startup-performance benchmark.

The first focused device run passed 55 of 57 cases. One assertion still expected the old Inbox prompt text; the other exposed the Undo snackbar covering a linked-reflection card. The assertion was updated and the app now reserves the snackbar's measured height, including keyboard/navigation inset handling. Visual review also moved Next fact clear of the FAB and made contextual-consent actions stay visible with a scrollable body. **All 57 cases passed on the final rerun**, including the regression that opens a saved reflection. These overlapping runs cover 57 distinct cases and must not be summed.

Device checks cover schema 1/2 upgrades, field patches/FTS/extensions, deletion during pending edits, journal ownership when an older editor closes, completion/reminder Undo and conflict protection, mixed editor persistence, incomplete draft recovery, in-place consent decline/credential prerequisites, dashboard actions and existing capture/reflection/privacy regressions.

Validation logs are retained locally under `build/redesign-*.log`; standard JVM reports are in `app/build/reports/tests/testDebugUnitTest/`. The final device run uses the built test APK through `adb shell am instrument`, with its complete status stream in `build/redesign-final-instrumentation.log` (`OK (57 tests)`). Lint's HTML report is `app/build/reports/lint-results-debug.html`.

The [five new screen captures](screenshots/redesign-m1/README.md) use synthetic data in the isolated test database and were visually reviewed. Existing core/Phase 2/Phase 3–4 screenshot folders remain historical baselines.

Final debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `E5F9F5FFDEA5A83B29906497E0047C4050B0D1EBCE3BCBF1EDB8102862A07A4C`.

## Following increments

Milestone 2 adds consistent global capture hosting, live token styling, keyboard-adjacent suggestions and swipe/batch Inbox triage. Milestone 3 adds the full metadata diff, Discover presets/count semantics, collection-view craft and further Item Detail/Vault/reflection disclosure. Shared transitions, Board drag, broader AMOLED refinement and content panes follow in milestone 4. This increment does not claim those later interactions are implemented.

Broader physical-device/API coverage, large-text/TalkBack assessment and live authenticated TMDB calls remain separate validation work. The emulator has no TMDB token configured; the consent/prerequisite paths are tested without sending a live movie query.
