# Notes App

An offline-first Android app for collections, notes, facts, and choosing something from your saved backlog. Version **0.4.0** implements Phases 1–4 of the [original roadmap](docs/original-product-spec.txt), including rich metadata, launcher tools, optional online lookup, security, encrypted folder backups, and insights.

## What works

- **Today:** Due Soon and Up Next first, with Complete/Snooze/Resume actions; a dismissible Inbox prompt; a compact daily fact with favorite/share/next and swipe cycling; and a lightweight random pick.
- **Collections:** Movies, Books, Places, and Things to Buy on first launch; additional templates and custom collections; configurable status names and roles, icons, and colors.
- **Collection details:** List, Grid, and Board views with retained per-collection view/sort/filter choices, match totals, metadata summaries, swipe-to-next-status, Board drag and drop with edge scrolling, and status menus. Status changes offer Undo.
- **Item details:** unified debounced autosave for title, notes, link, tags, and rich metadata; private recovery of incomplete drafts; save/error feedback; and immediate workflow controls. Status and Quick notes stay near the top; secondary groups expand on demand. Major changes offer Undo, while deletion still asks for confirmation.
- **Vault:** notes with pinning and tags; facts with sources, topics, commentary, favorites, mastery, filtering, and random draws. Editors save when leaving or when the app stops.
- **Discover:** five presets, an Apply/Cancel advanced filter sheet, currency-aware budgets and known-free filtering, eligible/remaining counts, and serialized random draws without session repeats.
- **Quick Capture:** `Watch Arrival #scifi !high` suggests Movies, attaches a tag, and sets priority. `Read`, `Visit`, `Go to`, `Buy`, `Purchase`, and `Order` also work. Use `@books` to target a collection, or choose one manually. Capture is available from all four tabs and collection contents, with live token previews, selection-aware voice insertion and retained drafts. Unclassified captures go to the Smart Inbox.
- **Inbox:** ranked local filing suggestions, searchable destinations, configurable swipe actions, ID-based selection and transactional batch filing. Filing offers batch Undo; Inbox deletion has a durable 15-second Undo window that preserves related data.
- **Search:** local FTS4 search across items, notes, facts, and tags, with highlighted excerpts.
- **Adaptive workspace:** collection contents, Vault, Search and Settings can keep a list beside detail in large windows. Selection, drafts, query and list position survive narrower windows; changing rows or tabs saves the active item/note/fact first.
- **Appearance:** system, light, or dark theme; AMOLED black; wallpaper colors on supported devices; adaptive bottom navigation or a navigation rail; and Reduce motion with system animation-scale support.
- **Data portability:** full JSON backups with merge or confirmed replacement imports, collection CSV, and Markdown folder import/export through Android's document picker.
- **Morning notifications:** delivery time, active days, topic/source filters, notification preview, and permission controls.
- **Item reminders:** date/time controls, Today due-soon cards, exact alarms when allowed, an approximate fallback, and schedule recovery after reboot/time changes.
- **Android sharing:** receive shared text/links or selected text and save it as an item, note, or fact.
- **Selection history:** persistent draw outcomes and configurable cooldown filtering in Discover.
- **Reflections:** queued optional completion prompts save searchable notes linked to each item, with inherited tags and excerpts in detail/history.
- **Rich details:** movie runtime/year/genre/service, book author/pages/ISBN, place address/coordinates/cost/opening hours, and shopping price/budget/currency/store link. Metadata validation, runtime/price sorting, and context filters are included.
- **Local covers:** select and copy an image into private app storage; view it on the item and cover grid.
- **Optional lookup:** explicit, opt-in Open Library/TMDB searches, full-detail previews with per-field choices and conflict checks, and optional cover downloads. Movie lookup uses a user-configured TMDB token.
- **Launcher tools:** daily-fact and text/voice/random widgets, three app shortcuts, a Quick Settings capture tile, and voice-to-capture heuristics.
- **App lock:** biometric/device-credential authentication across app entry points, secure windows, private notifications, and hidden fact-widget content.
- **Encrypted backups:** portable authenticated archives including cover files, plus scheduled backups to a local/Google Drive document-provider folder with per-device retention and merge/replace restore.
- **Insights:** monthly/year activity, completion rates, tag patterns, reading/watching streaks, and Still interested? backlog review with Roll now, Archive and Let go.

See the [Phase 2 guide](docs/phase2.md) and [Phases 3/4 guide](docs/phase34.md) for the flows, settings, and file formats.

The [UI/UX redesign proposal](docs/design/README.md) contains a screenshot/code audit, Material 3 design tokens, screen-by-screen Compose specifications, and a separate four-milestone engineering roadmap.

The [milestone 1 record](docs/redesign-milestone1.md) covers editing, Today, consent and theme foundations. The [milestone 2 record](docs/redesign-milestone2.md) and [Capture/Inbox screen tour](docs/screenshots/redesign-m2/README.md) track shared capture, token controls and reversible Inbox triage. The [milestone 3 record](docs/redesign-milestone3.md) covers retained collection views, Discover, metadata review and detail/Vault polish. Milestone 4 includes the [adaptive foundation](docs/redesign-milestone4.md) and [motion, Board and quality pass](docs/redesign-milestone4-polish.md), with verification evidence and remaining acceptance gates in those reports.

App data stays in Room and Preferences DataStore by default, with no app account or telemetry. Internet access is used only for user-initiated, opted-in metadata/cover lookup. A chosen document provider handles any cloud-backup account and upload. Manual editing, search, saved covers, and discovery remain available offline.

The latest redesign work is recorded in the [milestone 4 motion, Board and quality pass](docs/redesign-milestone4-polish.md).

## Build and install

The checked-in Gradle wrapper pins Gradle 9.6.1 and verifies its distribution checksum. Dependency versions are in [gradle/libs.versions.toml](gradle/libs.versions.toml).

The local build uses JDK **22.0.1**, Android SDK platform **37**, and Build Tools **36.0.0**. The app supports Android **8.0 / API 26** and above and targets API **36**. Initial development builds need Internet access to obtain Gradle, dependencies, and any missing SDK packages; running the app works offline.

On Windows PowerShell, set your own JDK and SDK locations:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk-22.0.1'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
```

Create a local, untracked `local.properties` containing your SDK path if it is not already present:

```properties
sdk.dir=C:/Users/YOUR_USER/AppData/Local/Android/Sdk
```

Build the debug APK, or install it on a connected device/emulator:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
adb shell am start -n com.notesapp/.MainActivity
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Android Studio can also open the project root; configure its Gradle JDK to a compatible installed JDK. On macOS/Linux, use `./gradlew` and the local SDK path instead.

## Tests and checks

See the [verification report](docs/verification.md), [core screen captures](docs/screenshots/README.md), and the [Phase 1](docs/phase1-verification.md) / [Phase 2](docs/phase2-verification.md) baselines.

```powershell
# Domain logic and text formatting
.\gradlew.bat :app:testDebugUnitTest

# Android lint and a minified, unsigned release build
.\gradlew.bat :app:lintDebug :app:assembleRelease

# Real Room/DataStore integration tests and Compose UI flows; requires a device
adb devices
.\gradlew.bat :app:connectedDebugAndroidTest
```

Unit tests cover capture parsing, collection schemas and templates, tag handling, sorting/filtering, random selection and cooldown, fact rotation, notification schedules, transfer formats, and search highlighting. Instrumented tests use isolated databases/settings and cover migrations, transactional imports, notification delivery/scheduling, share capture, reflections, editor persistence, and navigation through the app.

Reports are written to:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`

The `ScreenTour` test walks through 20 populated light/dark screen states. To run just the tour and save its screenshots:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.notesapp.ui.ScreenTour' `
  '-Pandroid.testInstrumentationRunnerArguments.tour=true'
```

Gradle copies the PNGs to `app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/` under the device's `tour` folder, preserving them when it uninstalls the test APKs. Tour data lives in the test database. It does not add demo records to the normal app database. Release builds are unsigned; distribution signing is not configured.

For the Phase 2 tour, replace the class above with `com.notesapp.ui.Phase2Tour`. Its captures are saved in `phase2-tour` and include reminders, reflections, history, share capture, notification preview, and Android's document picker. It requires Android 13+ for the notification-permission checks.

The new screen tour is `com.notesapp.ui.Phase34Tour`; `com.notesapp.ui.Phase34WidgetTest` also captures real hosted widget views when `tour=true`. Their images are written under `phase34-tour`. Locked app screenshots are intentionally blocked and are not captured by the tour.

## Source layout

- `app/src/main/java/com/notesapp/data`: Room entities/DAOs, transactional repositories, FTS indexing, and DataStore settings.
- `app/src/main/java/com/notesapp/domain`: status schemas, templates, capture parsing, random selection, fact rotation, and use cases.
- `app/src/main/java/com/notesapp/ui`: Compose screens, ViewModels, reusable controls, theme, and typed navigation.
- `app/src/main/java/com/notesapp/di`: Hilt database and application dependencies.
- `app/src/main/java/com/notesapp/automation`: WorkManager jobs, notification delivery, AlarmManager scheduling, and recovery receivers.
- `app/src/main/java/com/notesapp/domain/transfer` and `data/transfer`: backup/CSV/Markdown codecs and Android document access.
- `data/metadata`, `data/images`, and `domain/metadata`: explicit online lookup, private cover files, and validated rich details.
- `data/backup`, `data/security`, and `domain/security`: portable encrypted archives, Keystore-wrapped credentials, and authenticated encryption.
- `security` and `launcher`: app authentication/session guards, widgets, shortcuts, and the capture tile.
- `ui/insights` and `domain/insights`: local calendar activity and backlog cleanup.
- `app/schemas`: exported Room schemas 1–4. Additive migrations introduce selection history/knowledge links, private item recovery drafts and the pending-deletion ledger. Drafts and pending deletions are excluded from search and portable record exports.
- `app/src/test` and `app/src/androidTest`: JVM and device tests.

Repositories update searchable content and its FTS entries in the same Room transaction. Collection behavior uses status roles (not started, in progress, completed, dropped), so renamed and custom statuses retain their meaning. Daily fact rotation persists one selection per calendar day and prioritizes unseen facts; learned facts resurface less often and hidden facts are excluded.

<a id="later-roadmap-phases"></a>

## Scope and remaining work

All four phases in the original spec are implemented. Its Phase 4 cloud feature is encrypted automatic folder backup through SAF; automatic two-way record synchronization is not specified or implemented. A separate sync service, rich Markdown rendering, and image-card sharing remain possible future additions.

The manifest disables Android system backups and transfers. Use an encrypted archive to preserve records **and local cover files** before uninstalling or clearing storage. JSON preserves records and stored image URI references, without copying the image files. JSON/CSV/Markdown exports are plaintext; `.notesenc` archives are passphrase-encrypted. Release signing and broader device/API validation remain release-preparation work.
