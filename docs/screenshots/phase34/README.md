# Phases 3 and 4 screen tour

API 34 emulator captures use synthetic records and a generated local cover fixture. Widget captures come from real inflated Android AppWidgetHost views. Locked app screenshots are deliberately blocked; the widget privacy capture contains only its public unlock message.

| Flow | Capture |
| --- | --- |
| Book cover grid | [01_book_cover_grid.png](01_book_cover_grid.png) |
| Locally stored cover | [02_local_cover.png](02_local_cover.png) |
| Typed book metadata | [03_book_metadata.png](03_book_metadata.png) |
| Context filters | [04_context_filters.png](04_context_filters.png) |
| Context-aware pick | [05_context_pick.png](05_context_pick.png) |
| Privacy and metadata settings | [06_privacy_metadata_settings.png](06_privacy_metadata_settings.png) |
| Encrypted archive controls | [07_encrypted_archive.png](07_encrypted_archive.png) |
| Automatic backup controls | [08_automatic_backup_controls.png](08_automatic_backup_controls.png) |
| Personal insights | [09_personal_insights.png](09_personal_insights.png) |
| Forgotten backlog | [10_forgotten_backlog.png](10_forgotten_backlog.png) |
| Dark insights | [11_insights_dark.png](11_insights_dark.png) |
| Launcher capture | [12_launcher_capture.png](12_launcher_capture.png) |
| Daily fact widget | [13_daily_fact_widget.png](13_daily_fact_widget.png) |
| Widget while app lock is enabled | [14_widget_privacy.png](14_widget_privacy.png) |
| Text/voice/pick widget | [15_capture_widget.png](15_capture_widget.png) |

Run `com.notesapp.ui.Phase34Tour,com.notesapp.ui.Phase34WidgetTest` with `tour=true` using the [README command](../../../README.md). Retained images are copied from Gradle's additional-test-output directory.
