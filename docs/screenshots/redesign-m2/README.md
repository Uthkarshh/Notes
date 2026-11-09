# Capture and Inbox — redesign milestone 2

Synthetic test data on the API 34 emulator. The [implementation report](../../redesign-milestone2.md) records scope and verification. These captures document this increment; earlier tours remain historical baselines.

| Screen | Capture |
| --- | --- |
| Live tokens, destination/tag/priority preview and keyboard controls | [01_capture_preview.png](01_capture_preview.png) |
| Priority replacement picker | [02_priority_picker.png](02_priority_picker.png) |
| Ranked filing suggestions and Other picker entry | [03_inbox_suggestions.png](03_inbox_suggestions.png) |
| ID-based selection and batch actions | [04_batch_selection.png](04_batch_selection.png) |
| Durable deletion Restore control and snackbar Undo | [05_delete_undo.png](05_delete_undo.png) |

Reproduce with `RedesignMilestoneTwoTour` and instrumentation argument `tour=true`. Captures are written to the target app's external files directory under `redesign-m2`; they contain no personal data. The tour closes capture, selects Inbox items, deletes one and restores it. Emulator images do not verify physical haptics or speech recognition.
