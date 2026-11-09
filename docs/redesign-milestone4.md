# Redesign milestone 4 — adaptive foundation

This is the first integrated increment of [redesign milestone 4](design/04-roadmap.md), following the [milestone 3 collection/discovery work](redesign-milestone3.md). This report preserves the adaptive increment’s evidence. The subsequent [motion, Board and quality pass](redesign-milestone4-polish.md) records newer implementation and verification; milestone 4’s remaining release acceptance gates are tracked there.

## Delivered behavior

Large windows keep a useful list beside the current detail, while narrower or shorter windows show one screen:

| List | Detail |
| --- | --- |
| Collection contents | Selected item |
| Vault | Selected note or fact, including creation |
| Search results | Selected item, note or fact |
| Settings | Notifications, import/export, privacy/metadata, backups or insights |

The navigation rail remains available in wide detail contexts. Choosing another row replaces the current detail within that list; Back returns to the list once instead of walking through every prior selection. A notification opens its item directly, without inventing a collection parent. Opening an unrelated screen, such as a linked note from an item, keeps the normal Back history.

Two panes require a window at least 840 dp wide, a content area at least 480 dp tall, and room after the rail and insets for a 280 dp list, 360 dp editor and 24 dp gap. Medium windows retain a rail with one content pane. The layout reads the current posture and passes excluded hinge bounds to the adaptive scaffold. The width policy checks both hinge-separated regions, exchanges list/detail minimums in RTL, and falls back to one pane for cramped or tabletop layouts.

Collection, Vault and Search rows outline the selected entity and expose selected semantics. Lists retain scroll position, collection view/filter choices and search text when the detail takes the whole window, then restore them on Back. Item, note and fact editors remain tied to their navigation entry and stable entity ID across resize and recreation.

Selecting another entity or using the rail first awaits the active item/note/fact save. A failed save keeps the current editor available with feedback; rapid departures are ignored while that save is in progress. This extends the existing item journal and note/fact leave/stop saving paths. Collection schema editing retains its explicit Save behavior. Contextual capture remains available beside collection and Vault editors and keeps the collection destination.

## Implementation and compatibility

`AdaptiveAppContent` uses the Material adaptive layout scaffold around the existing typed Navigation Compose host. The same parent back-stack entry owns the list in both placements. Movable content handles an in-frame resize; a saveable state holder handles the compact detail/Back interval. A visible parent list observes the activity lifecycle so it continues to receive repository changes while its navigation entry is covered by the editor.

Screen wrappers obtain their navigation-entry-scoped ViewModels and pass them to route-free content. Each detail entry owns its editor state. `AppNavigator` is the single navigation adapter and awaits the registered editor guard before controlled departures. There is no parallel pane selection history to reconcile with Back.

The added adaptive layout/navigation artifacts resolve to **1.3.0** through the existing Compose BOM **2026.09.00**. Room remains at schema **4**. Existing backup formats and record schemas are unchanged. The implementation uses the layout scaffold directly so the existing NavController remains authoritative. This follows the supported separation between [adaptive layouts and their optional navigation wrapper](https://developer.android.com/develop/ui/compose/layouts/adaptive/list-detail).

## Verification

Verification date: **November 8, 2026**. JDK 22.0.1, Gradle 9.6.1, compile API 37, target API 36; Android 14/API 34 emulator. Its original window is 1080 × 2400 px at 420 dpi. The adaptive cases resize the real activity to 360, 600, 840 and 1200 dp widths, with 900 dp height and a separate 400 dp short-landscape case, restoring the original size after each case.

| Check | Result |
| --- | --- |
| JVM suite | **179 passed**, zero failures/errors/skips |
| Debug app and instrumentation APKs | Built successfully |
| Complete functional emulator suite | **220 passed**, zero failures; includes all eight new adaptive cases |
| Android lint | **Zero errors, eight existing advisories** |
| Minified release APK | Built successfully; unsigned, runtime not tested |
| Normal installed-app smoke | **Passed**: cold launch, navigation, collection/Vault panes, window restoration |
| Retained screenshots | **Four captured and inspected**, each 3150 × 2363 px |

Seven new JVM cases cover the width boundary after insets/rail, minimum heights, hinge regions, RTL side assignment, tabletop/multiple obstructions and out-of-content hinges. Eight new device cases cover selected-item replacement and contextual capture; raw invalid-draft recovery across widths and recreation; note/fact saves before selection or tab changes; mixed-type search with a retained query; settings Back behavior; short landscape; retained list position; and notification entry without an invented list parent.

Interim checks exposed two navigation risks that are corrected here. Reusing a destination with single-top navigation can retain an editor for different route arguments, so entity replacement now follows the full route and parent relationship independently of pane size. After a Room save, the instrumentation dispatcher could resume the navigation continuation on a worker thread; the adapter now explicitly runs on Android's main dispatcher. Temporary product diagnostics were removed. The notification resize test also restores ActivityScenario's original launch intent before recreation, matching the existing notification test's cleanup contract.

An intermediate release job was deliberately stopped while correcting navigation. Its daemon-disappeared message is not the final build result. The final [validated build log](../build/redesign-m4-validated-build.log) records successful JVM, debug, lint and minified-release checks. Runtime checks use the debug APK.

The authoritative functional run completed all 220 cases in 908.152 seconds. Focused reruns and the four capture cases are included in that total, not additional distinct cases. The normal installed app cold-launched in 3.382 seconds, retained collection context at 600/840/1200 dp, showed the expected collection and Vault panes, and returned to Today at the original 1080 × 2400 size. Its app-locale override remains empty. These are emulator observations rather than a device-performance claim.

Local evidence:

- [Full functional regression](../build/redesign-m4-regression.log)
- [Main-thread save/navigation recheck](../build/redesign-m4-main-thread-recheck.log)
- [Normal installed-app smoke](../build/redesign-m4-normal-smoke.log)
- [Machine-checked totals, image dimensions and APK hashes](../build/redesign-m4-summary.json)
- [Adaptive screen tour](screenshots/redesign-m4/README.md)

Debug APK: [app-debug.apk](../app/build/outputs/apk/debug/app-debug.apk). SHA-256:

```text
F2FBA32A6FF96559654FB6105FC0453A903436AA955C53B49BF158DC5512F728
```

The [minified unsigned APK](../app/build/outputs/apk/release/app-release-unsigned.apk) is also available for signing/release preparation; it was built but not installed for runtime checks.

## Follow-up implementation and release acceptance

Shared cover/capture motion, reduced motion, tactile Board movement, rating input, contrast and transition privacy changes are implemented in the [motion, Board and quality pass](redesign-milestone4-polish.md). That record contains the latest test/build evidence and APK hashes; the totals and hashes above remain the historical adaptive baseline.

Physical tablet/foldable/OLED assessment, complete assistive-input validation, representative-device profiling and production release signing remain acceptance work. Emulator resize, hinge-policy assertions and targeted RTL/keyboard checks do not establish that those broader assessments have passed.
