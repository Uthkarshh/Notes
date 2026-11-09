# Redesign milestone 3 — collections, discovery and detail

Implementation record for [milestone 3 of the redesign roadmap](design/04-roadmap.md). The original product phases and the historical screenshot audit remain separate from these redesign increments.

## Delivered behavior

- Collections retain List/Grid/Board, sort direction and status/tag/favorite filters per collection in DataStore. Match totals and a visible Clear filters action explain filtered empty states. List rows support swipe-to-next-status and an accessible status menu, using the collection's own workflow. Status changes offer Undo. Board lanes snap horizontally and load their cards lazily; drag and drop remains milestone 4.
- Shared item presentation supplies metadata across List, Grid, Board, Today and drawn results. Runtime/page counts include units; prices show their currency or explicitly say it is unspecified. Movie/book covers use portrait proportions, other categories use square covers, and deterministic icon/accent fallbacks remain when images are absent or fail. Grid titles and details sit below artwork so text can grow without covering actions.
- Discover offers Anything, Quick watch, Favorites, Free and Forgotten presets. Its advanced sheet edits a draft with an eligible count; Apply commits it and Cancel preserves the active filters. Status, collection, favorite, priority, tags-any, cooldown, runtime, price/currency and age constraints use the same candidate function as the draw. Positive budgets require an explicit matching currency; Free includes known zero prices only. Archived items are always excluded. Pending deletions are excluded by the data layer. Forgotten means strictly older than 90 days against the injected clock.
- Draw sessions report live unpicked and eligible totals, including changes from archiving, moving or pending deletion elsewhere, and suppress previous picks. They serialize Draw/Roll again/Accept/Complete commands. Eligibility and the new history row commit in one Room transaction. A session's own history writes do not invalidate its cooldown baseline. Status, completion history and reminder consumption commit together; repeated completion does not emit duplicate reflections.
- Metadata lookup fetches supported details before showing the review sheet. Each field shows Current and Provider values. Only missing values start selected; replacing existing values is an explicit choice. Personal title, notes, tags, rating and workflow are outside the provider whitelist. Comparison and selected-field patching are atomic. A changed field rebases the review and clears that field's selection, requiring a new choice. Incomplete local numeric drafts must be corrected before provider replacement. Undo preserves later edits to unrelated metadata fields. Cover download is optional and follows successful text application; failure retains the saved cover and the text changes. A locally changed cover is also preserved.
- Item Detail keeps status and Quick notes near the top. Collection, rating/priority, structured details, cover/lookup and links/tags expand on demand. A restored invalid metadata draft opens its group. Linked reflections show excerpts and link to the note editor. Vault notes use a quieter editorial layout; facts expose a direct mastery menu.
- Completion prompts queue by item instead of replacing one another. Saving or skipping advances to the next prompt; status Undo removes matching requests. Reflection excerpts also appear in selection history. Still interested? in Insights offers Roll now, Archive and Let go. Every backlog decision rechecks the item's status and strict age cutoff at commit time, preserving items that have since started. Let go uses the workflow's dropped state, or archives when none exists.

## Data and compatibility

Room remains at schema 4; this increment adds no tables or destructive migrations. Per-collection view preferences are device settings and are cleared on a replacement import to prevent reused IDs inheriting unrelated filters. No dependency versions were changed. Metadata lookup is injected through a small provider interface, allowing deterministic fixture coverage without public requests or credentials.

Director is not added in this increment: the existing movie/book metadata schema remains the supported provider whitelist. Board drag, shared transitions, adaptive detail panes and broader theme/motion work remain milestone 4.

## Verification

Verification date: **November 7, 2026**. JDK 22.0.1, Gradle 9.6.1, compile API 37, target API 36; Android 14/API 34 emulator at 1080 × 2400. Shared item components were also checked at 200% fonts within the test composition.

| Check | Result |
| --- | --- |
| Debug app and test APKs | Built successfully |
| JVM suite | **172 passed**, zero failures/errors/skips |
| Complete functional emulator suite | **212 passed**, zero failures |
| Live-count/recovery focused check | **11 passed**, included in the functional total |
| Screen/capture checks | **8 passed after targeted recheck**: six tours plus two functional cases recaptured |
| Android lint | **Zero errors, eight existing advisories** |
| Normal installed-app smoke | **Passed**: cold launch, four destinations, global capture, Discover presets, advanced-sheet cancellation and return to Today |

The six new UI cases cover preset/draft behavior and recreation, retained collection views, swipe/status Undo, restored invalid drafts, direct mastery and queued reflections. Ten new repository cases cover selected patches and conflict re-review, field-preserving Undo, cover conflicts, independent preferences, exact backlog rechecks, command serialization/history, live session counts, completion idempotency, reflection excerpts and archived/pending exclusions. Nine new JVM cases cover combined Discover constraints, currencies, free/unknown prices, strict age boundaries and shared presentation. Three provider-fixture UI cases verify full detail review, concurrent local changes and cover failure; the large-font case verifies List/Grid/Board actions.

Earlier focused UI checks exposed six test assumptions about scrolling, duplicate button text and the label on an empty input; the corrected cases passed. An interim broad run was intentionally force-stopped before adding live session-count observation. Its terminal “Process crashed” line is from that stop, and it is not counted as a completed regression. The historical reflection tour also needed its heading updated to “Reflections & linked notes”. Its external DocumentsUI cold launch exceeded an eight-second tour wait; the system launch was confirmed in logcat, and that external-window wait was extended to 30 seconds. These changes affect only the tour. The final **212-test** run is the authoritative functional result.

The final capture batch passed seven of eight cases; its one external-picker timeout passed on the targeted recheck. The eight capture cases include two already covered by the functional suite, so the union is **218 distinct emulator cases**, not 220. All ten retained milestone screenshots were inspected; the large-text example uses composition-local 200% font scale and leaves the emulator font setting alone. The normal app cold-launched in 4.566 seconds with its locale override still empty, then passed navigation, Discover preset checks, and advanced-sheet entry/cancellation.

Local evidence:

- [Validated build, JVM and lint log](../build/redesign-m3-validated-build.log)
- [Full functional regression](../build/redesign-m3-functional-regression.log)
- [Live count and recreation checks](../build/redesign-m3-count-refresh-checks.log)
- [Corrected focused checks and first captures](../build/redesign-m3-focused-recheck.log)
- [Screen/capture batch](../build/redesign-m3-screen-tours.log)
- [Targeted external-picker tour recheck](../build/redesign-m3-phase2-tour-recheck.log)
- [Normal installed-app smoke](../build/redesign-m3-normal-smoke.log)
- [Machine-checked case totals, image sizes and APK hash](../build/redesign-m3-summary.json)
- [Milestone 3 screenshots](screenshots/redesign-m3/README.md)

Debug APK: [app-debug.apk](../app/build/outputs/apk/debug/app-debug.apk). SHA-256:

```text
7ABC04F01CF6973817DED35AC9E7DA32A0A7D501E3DAD851F8E67EFEFD304A27
```

## Limits

Checks use synthetic data on the API 34 emulator. Provider review tests use an injected local fixture, including full-detail enrichment, a concurrent edit and a failing cover download; they do not establish live provider availability. Physical-device haptics, a full TalkBack pass, tablet/foldable layouts, other Android API levels and live voice/provider checks remain separate release checks. No telemetry is added.
