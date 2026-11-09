# Phases 3 and 4 guide

Version 0.4.0 adds rich item details, launcher tools, optional online lookup, app lock, encrypted folder backups, and personal insights. The [original product spec](original-product-spec.txt) defines Phase 4 cloud support as automatic encrypted backups through Android's Storage Access Framework. A document provider such as Google Drive handles its account and upload; this app does not run an account server or automatically reconcile records between devices.

## Metadata and cover images

Open an item and scroll to **Details**:

- Movies: runtime, release year, genre, streaming service, and rewatch count.
- Books: author, page count, and ISBN.
- Places: address, cost tier, opening-hours note, latitude, and longitude.
- Shopping: price, target budget, currency, and store link.
- Custom collections: time needed, price, and target budget.

Item Detail now saves valid edits automatically after a short pause, including core fields and category metadata. The save indicator distinguishes saving, saved data and fields needing attention. An incomplete/invalid number keeps its last saved value and recovers as a private draft when you reopen the item. Author, ISBN, genre, streaming service, address, and opening-hours text are included in local search. See the [redesign milestone 1 record](redesign-milestone1.md) for the persistence model and [milestone 3](redesign-milestone3.md) for expandable detail groups, shared metadata summaries and retained collection view/filter/sort choices.

**Choose cover** opens Android's document picker. A supported image is copied into private app storage, rotated for its orientation using [AndroidX ExifInterface](https://developer.android.com/jetpack/androidx/releases/exifinterface), and resized to at most 1600 pixels per dimension. Selecting a cover does not require broad photo/storage access. Input images are limited to 15 MiB and 100 megapixels. The cover appears on the item and collection grid. Remove cover detaches it from the item.

Collection sorting now includes runtime and price, with missing values last in either direction. Discover supports time, maximum-price, and age filters alongside the existing status/tag/favorite/cooldown controls. Items with unknown runtime or price are excluded when that limit is active. Choose **Adjust filters** to edit these constraints, then **Apply filters** or **Cancel**. Positive price limits require selecting a currency and only match saved values in that currency. **Free** matches known zero prices; missing prices never qualify. Archived and pending-deletion items are always excluded. There is no exchange-rate service. Presets provide Anything, Quick watch (movies up to 90 minutes), Favorites, Free and Forgotten (unstarted, strictly older than 90 days).

## Optional online lookup

Lookup remains off by default. Tap **Find book details** or **Find movie details** on an item to review the provider disclosure and choose **Enable & search** in place. **Keep editing offline** leaves lookup disabled. You can also control lookup through **Settings → Privacy & metadata → Enable title lookup**. Searches remain user-initiated, with no background library scanning.

- Book searches use Open Library and need no API token.
- Movie searches use TMDB. Add your own **API Read Access Token** in the contextual sheet or this settings screen. It is stored using an Android Keystore-wrapped credential and is excluded from exports and backups.
- Choose a returned title to fetch and review its details. Each supported field shows **Current** and **Provider** values. Missing values start selected; replacing an existing value requires selecting that field. **Apply selected details** patches only those choices and keeps personal fields. If a chosen local value changed during review, it is shown again and must be reselected. Incomplete local numeric drafts must be corrected first. Downloading a cover is optional; a failed download keeps the saved cover and already-applied text. A newer locally chosen cover is also kept.

Queries and selected cover requests go to the relevant provider over HTTPS. Failed requests leave saved data available for manual editing offline. No remote image-loading dependency is installed: saved covers load locally through Coil, and optional cover downloads go through the explicit lookup flow.

## Voice and launcher tools

Quick Capture has a **Use voice** button. Long-pressing Today's capture button also opens voice capture. Spoken commands are converted to the same local parser used by typed captures; for example, “Add The Pragmatic Programmer to Books, want to read” becomes a book capture, and “Add oat milk to shopping high priority” becomes a high-priority shopping item. Unclassified thoughts go to Inbox.

Android's installed speech recognizer handles recording/transcription. The app requests offline recognition, but the provider may use online processing; its availability and privacy settings are controlled by Android. If no recognizer is available, typed capture remains usable. The app does not request microphone permission itself.

Long-press the app icon for **Quick capture**, **Voice capture**, or **Surprise me**. The random shortcut opens Discover and performs one draw using its normal defaults and persistent history.

In the launcher's widget picker, add **Daily fact** or **Capture and discover**. Daily fact shows the current saved fact and a Next fact action; the capture bar opens text, voice, or a random pick. In Android's Quick Settings editor, add the app's Quick capture tile to open capture over another app. These entry points respect app lock.

## App lock

Open **Settings → Privacy & metadata** and enable **Require biometrics or device PIN**. A configured Android biometric or device credential is required, and Android authentication is requested before enabling or disabling the setting.

Cold starts require unlock. Leaving the app relocks it after the brief activity-transition allowance; moving between the app's own activities keeps the same session. Lock now is also available. Share capture, shortcuts, and widget actions pass through the same guard.

While app lock is enabled, screenshots/recents captures are blocked, existing app notifications are dismissed when enabling it, new notifications hide saved content, and the fact widget displays an unlock message instead of facts. This guards app access; the Room database is not separately encrypted by this feature. Portable encrypted backups provide encryption for exported archives. JSON, CSV, and Markdown exports remain plaintext.

## Encrypted exports and automatic folder backups

Open **Settings → Encrypted backups**. Enter a passphrase of at least ten characters and confirm it before exporting or enabling automatic backups. Keep the passphrase separately: it is required to restore on another device and cannot be recovered by the app.

An archive contains all records, tags, history, links, appearance, notification preferences, and owned cover-image files. It excludes device-lock settings, API tokens, backup credentials, and provider-folder grants. External image URI references without an owned local file are omitted from the portable archive.

Archives use AES-256-GCM with a random nonce/salt and PBKDF2-HMAC-SHA256 with 600,000 iterations. Version, derivation parameters, salt, and nonce are authenticated with the content. Wrong passphrases, modified ciphertext, unsupported versions, broken references, and invalid images are rejected before any records are imported. Archives are limited to 80 MiB, including at most 30 MiB of cover-image data before encoding.

For automatic backups:

1. Choose a dedicated folder using Android's folder picker. Local storage works; Google Drive appears if an installed, configured document provider offers it. Some Android storage roots cannot be selected directly, so create/select a subfolder.
2. Enter and confirm the passphrase, then enable automatic backups. The passphrase is stored locally under a non-exportable Android Keystore key so background work can run.
3. Choose daily, weekly, or every-30-days scheduling and retention of 3, 7, 14, or 30 archives. Android may delay scheduled work, particularly while the battery is low. **Back up now** runs a folder backup immediately.

After a successful folder backup, retention removes only older automatic archives from this device's backup instance. Manual exports, other files, and archives from another device are kept. Provider access/upload failures are shown in backup settings and retried by scheduled work. The provider controls when an uploaded file becomes available elsewhere.

To restore, enter the archive passphrase, choose **Restore archive**, and review the validated preview. **Merge** adds copies and remaps relationships while keeping current settings. **Replace all** requires another confirmation and restores the archive records/settings. Neither mode enables app lock or copies another device's credentials. Configure folder access and automatic-backup credentials separately on each device.

## Insights and forgotten backlog

Open **Settings → Personal insights**. Choose a year and optionally a month to see additions, completions, completion rate, monthly activity, completed-item tags, and completion-day streaks. Reading and watching streaks are shown separately.

Completed count includes older backlog finished in the selected period. Completion rate uses the items added during that period as its cohort, so it stays between 0 and 100 percent. Streaks count local-calendar days with completions; multiple completions on the same day count once. Archived records still contribute. Deleted records and reopened completions no longer contribute, because these metrics use saved item completion state rather than an immutable activity ledger.

**Still interested?** lists unstarted, unarchived items saved more than 90 days ago. **Roll now** draws from that backlog. Open an item to review it, **Archive** it individually, **Let go** using its dropped state (or archive if the workflow has none), or confirm archiving the whole list. Archiving preserves records and allows later unarchiving; items that have since started or finished are checked again and kept active.

## References

The implementation follows official documentation for [Glance](https://developer.android.com/develop/ui/compose/glance/create-app-widget), [Android biometric authentication](https://developer.android.com/identity/sign-in/biometric-auth), [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [document-provider access](https://developer.android.com/training/data-storage/shared/documents-files), [speech recognition intents](https://developer.android.com/reference/android/speech/RecognizerIntent), [Open Library search](https://openlibrary.org/dev/docs/api/search), and [TMDB authentication](https://developer.themoviedb.org/docs/authentication-application). [Jetpack Security Crypto's deprecation](https://developer.android.com/jetpack/androidx/releases/security#1.1.0-beta01) is why archives and credential wrapping use platform cryptography/Keystore directly.
