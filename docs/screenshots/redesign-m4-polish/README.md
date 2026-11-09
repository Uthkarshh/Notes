# Milestone 4 motion, Board and quality tour

Synthetic data on Android 14/API 34, captured November 8, 2026. See the [implementation and verification record](../../redesign-milestone4-polish.md). The [earlier adaptive tour](../redesign-m4/README.md) remains a separate baseline.

| Screen | Window and appearance | Capture |
| --- | --- | --- |
| Today, Light | Original 1080 × 2400 px; static palette; normal text | [01_light.png](01_light.png) |
| Today, Dark | Original window; static palette; normal text | [02_dark.png](02_dark.png) |
| Today, AMOLED | Original window; static dark accents and black roots | [03_amoled.png](03_amoled.png) |
| Today, dynamic color | Original window; current emulator wallpaper palette | [04_dynamic.png](04_dynamic.png) |
| Selected grid item beside its editor | 1200 × 900 dp; 200% system font; Arabic app locale/RTL | [05_rtl_large_text_panes.png](05_rtl_large_text_panes.png) |
| Board after a status drop | 600 × 900 dp; normal text; current dynamic palette | [06_board_move.png](06_board_move.png) |
| Settings beside privacy controls | 1200 × 900 dp; 200% system font; Arabic app locale/RTL | [07_rtl_settings.png](07_rtl_settings.png) |

All windows retain the emulator’s 420 dpi density. The 1200 dp images are 3150 × 2363 px; the 600 dp image is 1575 × 2363 px. English labels remain English; the RTL captures verify layout direction, not an Arabic translation.

`AccessibilityThemeTest` and `MilestoneFourPolishTest` create these files with `polishTour=true`. Fixtures restore size, locale, font and animator settings afterward. Final images are retained after passing verification. Still images do not establish transition smoothness, haptic quality, TalkBack usability or OLED rendering. The dynamic image represents one wallpaper palette.

The immediate RTL pane image retains light status-bar glyphs. The normal minified replay showed readable dark glyphs in both native captures; the observation and emulator recovery are documented in the [quality record](../../redesign-milestone4-polish.md).
