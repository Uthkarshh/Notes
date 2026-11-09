# UI/UX redesign — milestone 2

Implementation date: **November 7, 2026**. This increment implements Capture and Inbox from the [redesign roadmap](design/04-roadmap.md). It builds on [milestone 1](redesign-milestone1.md); the original feature phases remain separate.

## Capture

- One app-shell capture host serves Today, Collections, Vault, Discover and collection contents. Collection contents supply the default destination. Labeled New collection/New note/New fact actions remain available locally. Long-press capture still opens the protected voice entry point.
- The parser returns original UTF-16 token ranges. Highlighting, interactive previews and saving use the same result. URL fragments do not become tags, incomplete markers remain literal, and a backslash escapes `#`, `@`, `!` or a backslash.
- An unmatched `@collection` stays in the title, displays a warning and does not silently fall back to a verb's collection. A manual destination wins over parsed inference; choosing Auto-detect restores inference. Explicit Inbox stays Inbox.
- Collection, Tag, Priority and Voice controls sit above the keyboard. The collection picker supports search and multiword names. Tag chips remove their source occurrences; the priority picker replaces every existing recognized priority token. Voice inserts into the selection instead of replacing the draft.
- Raw text and selection/composition live in one `TextFieldValue`. Same-length `VisualTransformation` styling uses identity offsets with the installed Material 3 1.4.0, avoiding a dependency upgrade. SavedStateHandle retains text/selection/destination through sheet dismissal and activity recreation; Clear draft is explicit. Successful saves clear the draft; failures preserve it.
- Save and IME Done use one guarded command. The repository rechecks the current destination/schema inside the transaction. Undo of a new capture refuses to delete it after a later edit.
- Android Share, launcher, tile and widget entry points keep their authentication and Item/Note/Fact choice. Item capture shares the parser, destination precedence, preview styling and save use case.

## Inbox

- Each row shows at most two destinations and Other, which opens a searchable picker. On-device ranking uses stored capture hints, recent filing destinations, then collection order. Suggested, Recent and Collection labels state the reason. Without an inference, the filing swipe opens the picker.
- Default start-to-end swipe files the inference; end-to-start deletes. Settings → Interactions → Inbox gestures configures File suggestion, Choose collection, Delete or None independently. Directions mirror in RTL. A 35% threshold and one haptic per threshold crossing support deliberate gestures. Buttons and accessibility custom actions offer alternatives.
- Select or long-press enters selection mode. SavedStateHandle stores IDs, Select all targets the current Inbox, and count-labeled File/Delete actions operate on a committed selection. Operations survive navigation, reject duplicate submissions and expose failures with Retry.
- Filing rechecks IDs, Inbox membership and the current destination in one Room transaction. Stale IDs are skipped and reported. One Undo restores the batch, preserves unrelated later text edits and refuses conflicting workflow changes without partially restoring rows.

## Deletion and storage

Room schema **4** adds `pending_item_deletions`, with a cascading item foreign key, operation IDs and expiry indexes. Additive migrations from schemas 1–3 are registered; the exported schema accompanies the migration.

Inbox deletion has a **15-second Undo window**. The item, tags, cover, links, selection history, reminders and editor recovery journal remain intact in storage during that window. Pending rows disappear from active lists, counts, search, history, reminder scheduling/delivery, insights, random pools and portable exports. Late editor/action writes cannot modify them. A persisted Restore control in Inbox remains available after recreation while the deadline is valid; a queued snackbar is not the only recovery path. Snackbar deadlines are checked when each queued action can appear, and an expired deletion never offers a stale Undo. Closing an older editor cannot clear a pending item's recovery journal.

After expiry, Undo is refused even if cleanup has not run. WorkManager schedules each batch and a periodic recovery sweep; startup also finalizes expired rows. Physical deletion may occur later when Android allows background work. Finalization removes the item graph, keeps standalone reflection notes, and removes only an unreferenced cover owned by the app. External/shared cover files are retained. Existing Detail deletion still uses its confirmation flow.

Pending operations and recovery drafts are not portable records. Exports omit hidden items and their dependent references; replacement import clears the ledger through foreign-key cascading and clears recent filing destinations. Gesture preferences remain device-local and survive ordinary restarts.

## Verification

| Check | Result |
| --- | --- |
| JVM suite | **163 passed**, zero failures/errors |
| Debug app and test APKs | Built successfully from the final source |
| Android lint | **Zero errors, eight existing advisories** |
| API 34 functional regression | **192 passed**, zero failures, on the final APK |
| API 34 screenshot tours | **5 passed**, zero failures, on the final APK |
| Pending-draft guard and screenshot tour | **9 passed** on the final APK |
| Normal installed-app smoke | Cold launch succeeded; RTL left-to-file/right-to-delete and both Undo paths checked with one synthetic record, then cleaned up; original locale restored |

The [five screen captures](screenshots/redesign-m2/README.md) use synthetic data and were visually reviewed. Explicit keyboard/focus dismissal and the sheet hide animation prevent stale IME insets from lifting later snackbars. Earlier screen tours remain historical baselines. The normal installed-app RTL check used a single clearly named synthetic capture: it was filed, undone, deleted, restored and finally removed. The app locale was restored to its original empty override. After cold launch, a retried fresh hierarchy confirmed LTR Today and an empty Inbox without a Restore action; background finalization also reported success.

Local logs are under `build/redesign-m2-*.log`; `redesign-m2-validated-build.log` records the final build, unit tests and lint. An earlier integration run was deliberately stopped to add the pending-draft and snackbar-deadline guards; its `Process crashed` ending in `redesign-m2-integration-before-hardening.log` is the result of that explicit test-app stop. A completed pre-update run is retained in `redesign-m2-before-tour-updates.log`; its two failures were a legacy tour clicking an off-screen Today action and a wait helper requiring a Compose window during a launcher-activity transition. The tours now scroll explicitly and poll through that transition. A subsequent run exposed native keyboard-resize and Back-navigation timing in two older checks; the editor check now hides and settles the IME before inspecting lower validation text, and the Phase 2 tour waits for the returning toolbar. The final batches passed **192 functional cases plus five tours: 197 distinct instrumented checks**, with no failures or overlap between those batches. Their complete status streams are in `redesign-m2-functional-regression.log` (`OK (192 tests)`) and `redesign-m2-screen-tours.log` (`OK (5 tests)`). The earlier overlapping runs are not added to this count. `redesign-m2-stable-checks.log` records the final test APK/lint checks, and `redesign-m2-summary.json` verifies the unique-case counts, unit/lint results and APK hash.

The final normal-app cold launch also succeeded after the tours; its fresh Today/four-destination hierarchy and restored locale are in `redesign-m2-final-normal-smoke.log`.

Final debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `7602D016D75BA18A38DFB10A79316231CEAF279057C8A6523B95FD5DC952BA30`.

## Follow-up work

Milestone 3 covers collection-view polish, Discover presets/count semantics, metadata field diffs and further Item Detail/Vault/reflection disclosure. Milestone 4 covers shared motion, Board drag, broader theme/accessibility assessment and adaptive content panes. Physical haptics, a live installed speech recognizer, full TalkBack assessment and additional API/large-screen devices require separate checks.
