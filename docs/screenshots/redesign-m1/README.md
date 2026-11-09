# Redesign milestone 1 screen tour

Synthetic data captured on the API 34 Android emulator at 1080 × 2400. These captures show the first implementation increment; the [core](../README.md), [Phase 2](../phase2/README.md) and [Phases 3/4](../phase34/README.md) folders retain the earlier baselines.

| Screen | Capture |
| --- | --- |
| Today: urgent tasks and inline actions | [01_today.png](01_today.png) |
| Item Detail: save state and quick notes | [02_item_detail.png](02_item_detail.png) |
| Structured details: automatic persistence | [03_autosave_details.png](03_autosave_details.png) |
| Contextual online lookup consent and movie credentials | [04_contextual_consent.png](04_contextual_consent.png) |
| Today in AMOLED Black | [05_today_amoled.png](05_today_amoled.png) |

The tour class is `com.notesapp.ui.RedesignMilestoneOneTour`. Pass `tour=true` to save PNGs under the additional test output's `redesign-m1` directory. With direct adb instrumentation, they are saved to the target app's external files directory under `redesign-m1`.

See the [implementation and verification record](../../redesign-milestone1.md) for behavior, checks and remaining scope. No private user data or lock-protected screen is included.
