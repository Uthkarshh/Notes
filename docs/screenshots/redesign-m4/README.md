# Milestone 4 adaptive screen tour

Synthetic data on the Android 14/API 34 emulator, captured November 8, 2026. These captures show the first adaptive increment of milestone 4; [the implementation record](../../redesign-milestone4.md) preserves that baseline. The newer [motion/Board quality tour](../redesign-m4-polish/README.md) records the subsequent increment.

The emulator was resized to a **1200 × 900 dp** window at its existing density (420 dpi), producing **3150 × 2363 px** screenshots. Captures use normal font scale, light appearance and the emulator's current dynamic palette. No physical tablet/foldable or hinge rendering is represented by these images.

| Screen | Capture |
| --- | --- |
| Collection list beside the selected item | [01_collection_panes.png](01_collection_panes.png) |
| Vault notes beside the selected editor | [02_vault_panes.png](02_vault_panes.png) |
| Search query/results beside a selected note | [03_search_panes.png](03_search_panes.png) |
| Settings beside Privacy & metadata | [04_settings_panes.png](04_settings_panes.png) |

`AdaptiveNavigationTest` creates these files when the instrumentation argument `paneTour=true` is supplied. It restores the emulator's original size after each case. The retained screenshots do not validate motion smoothness, haptics, screen-reader use or other themes.
