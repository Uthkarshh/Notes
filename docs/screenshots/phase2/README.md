# Phase 2 screen tour

Captured on the API 34 emulator with synthetic data. These flows cover reminders, linked reflections, notification controls, data transfer, history, and capture from another app.

| Screen | Capture |
| --- | --- |
| Today with a due reminder | [01_due_reminders.png](01_due_reminders.png) |
| Completion reflection | [02_completion_reflection.png](02_completion_reflection.png) |
| Saved reflection linked to its item | [03_linked_reflection.png](03_linked_reflection.png) |
| Notification settings | [04_notification_settings.png](04_notification_settings.png) |
| Notification preview posted to Android | [05_notification_preview.png](05_notification_preview.png) |
| Import and export | [06_import_export.png](06_import_export.png) |
| Android document picker | [07_android_document_picker.png](07_android_document_picker.png) |
| Persistent selection history | [08_selection_history.png](08_selection_history.png) |
| Android share capture | [09_share_capture.png](09_share_capture.png) |
| Notification shade in the normal installed app | [10_android_notification.png](10_android_notification.png) |

Run `com.notesapp.ui.Phase2Tour` with `tour=true` using the command in the [README](../../../README.md) to regenerate captures 01–09. Capture 10 comes from a manual smoke check of the normal installed app: its notification appeared in Android's shade, and tapping it opened the saved fact. The [core tour](../README.md) covers the other app screens and light/dark themes.
