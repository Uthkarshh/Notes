# Phase 2 guide

## Back up or move your data

Open **Settings → Import & export**. Android's document picker lets you choose the destination/provider; the app does not request broad storage access.

- **JSON:** exports all collections, including empty ones; all retained items, including archived and Inbox items; notes; facts; tags and tag colors; selection history; knowledge links; appearance; and notification settings. Reserved metadata/image URI fields are preserved as stored. Actual image files are not copied. Private editor drafts and items awaiting deletion finalization are excluded, together with their dependent references.
- **CSV:** choose one collection or all items. Exported columns preserve item fields and collection status definitions. Generic CSV imports require a `title` column; optional columns include `description`, `collection`, `status`, `priority`, `rating`, `favorite`, `tags`, and `url`. Rows without a collection go to Inbox. A new generic collection uses To Do / In Progress / Done / Dropped unless the CSV includes `statusSchemaJson`. Unknown statuses and invalid values fail validation before import.
- **Markdown:** exports a new timestamped folder with Notes and Facts subfolders. Files are readable Markdown with a metadata block preserving tags, pinning, sources, topics, mastery, and counters. Edit the visible heading/body before importing to update the title/topic and text while retaining those details. Ordinary Markdown files import as notes. JSON is the complete backup format for collection items and relationships.

Imports validate the entire record graph before writing. **Merge** adds copies, remaps relationships, reuses matching tag names, and keeps existing settings. **Replace all** requires a second confirmation, replaces the data in one Room transaction, and restores any settings included in the file. Imports rebuild searchable content in the same transaction. JSON backup format version 1 is independent of the current Room database schema version 4.

Input files and combined Markdown text are limited to 20 MiB of UTF-8 data. Markdown import traverses at most five folder levels and 10,000 files. Keep a backup before replacing data. Exported files are plaintext; encryption is planned for Phase 4. Android notification permissions must be granted on the destination device even when notification preferences are restored.

## Morning facts and item reminders

Open **Settings → Notifications & reminders** to enable daily facts. Choose a time, days of the week, a topic, and optionally a phrase to match in the source. Only your saved facts are used. Preview notification sends a sample without changing the daily notification's deduplication state.

Daily delivery uses WorkManager and can be delayed by Android. Work delayed more than two hours beyond the chosen time is skipped. A day receives at most one scheduled fact notification during normal execution; preview is separate. Disabled days, hidden facts, nonmatching filters, and denied notification access prevent delivery. Starting or resuming the app keeps matching pending work, including an occurrence already due. Recovery within the two-hour window can catch up. Scheduled work is rebuilt after time/timezone changes, reboot, and app upgrades; the app's clock follows timezone changes in a running process.

Set a reminder on an item detail screen. Reminders for unfinished items within 48 hours, including overdue ones, appear on Today with Complete and Snooze actions. Completing or dropping an item clears its existing reminder; a new reminder can be set afterward. Archived items do not deliver reminders. A delivered reminder is cleared from the item, and stale or duplicate alarms are ignored.

On Android 12+, exact timing requires the system's Alarms & reminders access. When unavailable, the app uses a window of at least ten minutes; power-saving modes can delay it further. The app offers the system access screen and checks again after returning. Notification access is requested separately on Android 13+.

## Capture and file quickly

Tap **+** on Today, Collections, Vault or Discover. Inside a collection, the same capture sheet defaults to that collection. Use the labeled New collection/New note/New fact actions for those record types.

Type `Watch Arrival #scifi !high`. Highlighted tokens, destination chips and the title preview show what will be saved. Choose a collection, add/remove tags, replace priority or insert voice text at the selection. Cancel retains the draft; Clear draft removes it. An unknown `@name` remains literal and prompts you to choose a destination.

Open **Inbox** to file captures. The row shows up to two local suggestions plus **Other…** for all collections. Swipe toward the end to accept an inferred suggestion (or choose a destination when none is inferred); swipe toward the start to delete. Configure these actions in **Settings → Interactions → Inbox gestures**. Select or long-press a row for batch filing/deletion; Select all selects the current Inbox.

Filing offers one batch Undo. Inbox deletion provides a 15-second Undo window, including a Restore control that survives recreation. It retains related records until expiry, then finalizes when background execution is available. Item Detail still confirms permanent deletion. See the [milestone 2 implementation record](redesign-milestone2.md) for persistence and validation details.

## Capture from another app

Choose **Save to Notes App** in Android's share sheet for text or a link. The same action is available for selected text where Android exposes text processing. Review/edit the text, choose Item / Note / Fact, and save. Items use the same verb/tag/priority parser as Quick Capture and can be directed to a collection or Inbox. The supplied subject and URL are preserved; no metadata is fetched from the network.

## Draw history and completion reflections

Discover's **Avoid recent picks** filter supports 1, 3, 7, 14, or 30 days. Every draw updates the item's last-selected time and selection count. Accepting, skipping, or completing updates that draw's outcome instead of double-counting it. **Selection history** shows saved draws chronologically and opens the original item. Finalizing an item deletion also deletes its history; an Inbox deletion hides it during the Undo window.

Moving an item into a completed status offers **What stood out?**. Choose Not now or save a reflection. Saved reflections become regular searchable notes, inherit the item's tags, and appear as excerpts under Reflections & linked notes on the item and in selection history. Prompts queue when several individual completions arrive; Save or Not now advances to the next item. Batch triage does not force reflection prompts. Deleting the item keeps the note and removes its link. Deleting the note removes the link as well.

## Android references

Platform behavior follows the official documentation for [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files), [WorkManager](https://developer.android.com/jetpack/androidx/releases/work), [alarms and exact-alarm access](https://developer.android.com/develop/background-work/services/alarms), and [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
