# 2. Design system and Material 3 tokens

[Design index](README.md) · [Audit](01-ux-audit.md) · [Screen specifications](03-screen-specs.md) · [Roadmap](04-roadmap.md)

Use a quiet canvas, compact operational controls and a small number of expressive reading moments. Today greetings, facts and reflections carry personality; workflow state, numbers and inputs stay crisp. Values below are product tokens proposed for Notes App, not a claim that every value is a Material default.

## Color roles

Keep the existing teal fallback palette and Android 12+ dynamic color support. Always pair a container with its matching `on…` role. Dynamic color supplies the complete scheme; apply the AMOLED neutral override afterward only when dark mode and AMOLED are both effective. A Light selection must never become black because the AMOLED preference is remembered.

The semantic role model follows [Material 3 color roles](https://m3.material.io/styles/color/roles). Android's [M3 theming guide](https://developer.android.com/develop/ui/compose/designsystems/material3) describes color schemes, typography and shapes in Compose.

| Role | Light fallback | Dark fallback | AMOLED override | Usage |
| --- | --- | --- | --- | --- |
| `primary` / `onPrimary` | `#006A6A` / `#FFFFFF` | `#4CDADA` / `#003737` | Preserve effective dark palette | Main action, focus, active controls |
| `primaryContainer` / `onPrimaryContainer` | `#6FF7F6` / `#002020` | `#004F4F` / `#6FF7F6` | Preserve | Capture FAB, selected state, active workflow |
| `secondaryContainer` / `onSecondaryContainer` | `#CCE8E7` / `#051F1F` | `#324B4B` / `#CCE8E7` | Preserve | Backlog labels, quiet selection |
| `tertiaryContainer` / `onTertiaryContainer` | `#D3E4FF` / `#041C35` | `#334863` / `#D3E4FF` | Preserve | Completion, editorial accent |
| `error` / `onError` | `#BA1A1A` / `#FFFFFF` | `#FFB4AB` / `#690005` | Preserve | Invalid input, overdue icon/text, destructive labels |
| `errorContainer` / `onErrorContainer` | `#FFDAD6` / `#410002` | `#93000A` / `#FFDAD6` | Preserve | Revealed delete background, actionable failure |
| `surface`, `background` | `#F4FBFA` | `#0E1514` | **`#000000`** | App canvas, root scaffold |
| `surfaceContainerLowest` | `#FFFFFF` | `#090F0F` | **`#000000`** | Reading area, inset canvas |
| `surfaceContainerLow` | `#EEF5F4` | `#161D1D` | `#0A0A0A` | Ordinary card, collection lane |
| `surfaceContainer` | `#E9EFEF` | `#1A2121` | `#111111` | Navigation bar/rail, input group |
| `surfaceContainerHigh` | `#E3EAE9` | `#252B2B` | `#181818` | Modal sheet, expanded controls |
| `surfaceContainerHighest` | `#DDE4E3` | `#303636` | `#202020` | Dragged card, nested highlighted region |
| `onSurface` | `#161D1D` | `#DDE4E3` | `#E6E6E6` | Titles, body, metadata values |
| `onSurfaceVariant` | `#3F4948` | `#BEC9C8` | `#C2C2C2` | Supporting text, labels, timestamps |
| `outline` | `#6F7979` | `#899392` | `#8C8C8C` | Control boundaries, unselected required icons |
| `outlineVariant` | `#BEC9C8` | `#3F4948` | `#383838` | Decorative card border, separators |
| `surfaceDim` / `surfaceBright` | Existing Light values | Existing Dark values | `#000000` / `#242424` | Explicit use only; avoid a tinted black root |
| `surfaceVariant` | Existing Light value | Existing Dark value | `#242424` | Legacy component fallback; new containers use the roles above |

Preserve `secondary/onSecondary`, `tertiary/onTertiary`, inverse roles and scrim from the selected scheme. Use inverse surface/on-surface for snackbars, so Undo stays distinguishable from the canvas. Scrim is black at a proposed 32% opacity; retain the component's accessible dismissal behavior.

### Surface and border contract

| Component | Surface | Boundary / elevation |
| --- | --- | --- |
| Canvas and full-screen editor | `surface` | 0 dp shadow |
| Dense Today/list rows | Canvas or `surfaceContainerLowest` | Divider where grouping requires it; avoid boxing every row |
| Metadata/reflection/fact card | `surfaceContainerLow` | 1 dp `outlineVariant`; 16/24 dp shape by purpose |
| Selected preset/status | Matched accent container pair | Selection icon plus state label; do not rely on color alone |
| Sheet | `surfaceContainerHigh` | 1 dp boundary in AMOLED; modal scrim |
| Dragged card | `surfaceContainerHighest` | 2 dp primary outline; 6 dp shadow in Light/Dark, boundary carries separation in AMOLED |
| Capture FAB | `primaryContainer` | Standard M3 interaction/ripple; 3 dp shadow outside AMOLED |

Set explicit container colors and `tonalElevation = 0.dp` on roles already chosen for their tone. Otherwise an elevation overlay can tint an intended black surface. Nested surfaces should step once, rather than each card becoming a bright island. In AMOLED, use tonal steps and borders for depth; do not depend on a shadow to separate black regions.

`outlineVariant` is a subtle decorative boundary. It is insufficient as the sole visual cue for an actionable input or focus state. Use `outline` for required control outlines and a 2 dp `primary` focus treatment. Audit actual dynamic-color combinations too.

### Workflow and collection colors

| Meaning | Container / content | Additional cue |
| --- | --- | --- |
| `BACKLOG` / Fact New | Secondary container pair | Hollow circle, state label |
| `ACTIVE` / Fact Reviewing | Primary container pair | Play/review icon, state label |
| `DONE` / Fact Learned | Tertiary container pair | Check icon, state label |
| `DROPPED` / Fact Hidden | Neutral container + on-surface variant | Dash/visibility-off icon, state label |
| High priority | Neutral surface + `tertiary` | Flag plus High label when space permits |
| Urgent / overdue | Neutral surface + `error` | Flag/clock plus explicit text |

Status names remain the user's custom labels. Role icons are derived from the schema. A renamed “Planned” status may represent ACTIVE; do not infer behavior from the English name.

Custom collection colors identify collections through cover accents and badges. Keep text on theme surfaces, or derive a contrasting foreground for custom fills. Do not use a user-selected hex color as small text on an arbitrary background. Give missing-art covers a stable variation from a small palette based on item ID; choose an accessible text/container pair rather than random hues.

## Typography

Use local Android font families so the interface renders fully offline. `FontFamily.SansSerif` carries UI and note titles; `FontFamily.Serif` adds editorial character to quotations and reflection excerpts. A bundled brand font is a later optional asset, not a network dependency.

| M3 slot / app alias | Size / line height (sp) | Weight | Use |
| --- | --- | --- | --- |
| `displayLarge` | 57 / 64 | 400 | Rare expanded empty/celebration state only |
| `displayMedium` | 45 / 52 | 400 | Expanded editorial hero only |
| `displaySmall` | 36 / 44 | 400 | Tablet editorial statement |
| `headlineLarge` | 32 / 40 | 500 | Expanded Today greeting |
| `headlineMedium` | 28 / 36 | 500 | Phone Today greeting, item title |
| `headlineSmall` | 24 / 32 | 500 | Discover question, editor/sheet heading |
| `titleLarge` | 22 / 28 | 500 | App-bar title, note title |
| `titleMedium` | 16 / 24 | 600 | Item row headline, section title |
| `titleSmall` | 14 / 20 | 600 | Card subtitle, column header |
| `bodyLarge` | 16 / 24 | 400 | Editable notes, primary body copy |
| `bodyMedium` | 14 / 20 | 400 | Card excerpts and help text |
| `bodySmall` | 12 / 16 | 400 | Secondary timestamps/provenance |
| `labelLarge` | 14 / 20 | 500 | Button, action chip |
| `labelMedium` | 12 / 16 | 500 | Metadata pill and state caption |
| `labelSmall` | 11 / 16 | 500 | Nonessential eyebrow only |
| `editorialFact` (app token) | 20 / 28 | 400, serif | Compact fact quotation |
| `editorialReflection` (app token) | 18 / 26 | 400, serif | Reflection excerpt |
| `metadata` (app token) | 12 / 16 | 500, sans + `fontFeatureSettings = "tnum"` | `116 min`, `496 pp`, `USD 24.00`, `4.5 / 5` |

Use default M3 letter spacing except compact metadata at 0 sp and large headlines at −0.25 sp. Avoid all-caps sentences. Metadata values include their unit/currency, use localized formatting and remain on one line within a pill; move whole pills to the next line instead of truncating their values. Long titles can wrap; essential values should not be ellipsized to make a decorative icon fit.

An ordinal mastery indicator has accessible text such as “Reviewing, stage 2 of 3.” It is not a numerical estimate of how much a person knows. Hidden is excluded from the indicator's learning progression.

## Spacing, dimensions and shapes

| Token | Value | Application |
| --- | --- | --- |
| Spacing scale | 4, 8, 12, 16, 20, 24, 32, 48 dp | Use 4 dp increments; 2 dp allowed for optical alignment |
| Phone gutter | 16 dp | Shared left/right alignment across screen content |
| Medium/expanded gutter | 24 / 32 dp | Per-pane, measured from safe area |
| Row internal gap | 8 dp | Label/value and action grouping |
| Card padding | 16 dp; 20 dp editorial | Compact operational / reading contexts |
| Section gap | 24 dp | Between task, reading and discovery groups |
| List row | Minimum 80 dp | Wrap to 96/112+ dp as content/font size needs |
| Touch target | Minimum 48 × 48 dp | Includes 18/24 dp icons and visually smaller chips |
| Primary button | Minimum 48 dp high | Grow with text scale; 56 dp draw CTA at normal scale |
| Capture FAB | 56 × 56 dp | 24 dp plus; 16 dp inset from safe content edge |
| Max readable body width | 640 dp | Detail/note body within a wide pane |
| Corner scale | 4 / 8 / 12 / 16 / 28 dp | M3 extraSmall / small / medium / large / extraLarge |
| Reading/result shape | 24 dp app token | Fact and drawn-result card |
| Sheet shape | 28 dp top corners | Respect system edge/inset behavior |
| Chip shape | Capsule | Pill visuals; minimum touch target retained |
| Cover shape | 12 dp | Clip cover only; text lives outside image bounds |

Apply safe drawing, IME and navigation insets once at their owning scaffold. Reserve bottom content space for nav, FAB and snackbar. `Scaffold` padding must not be added again in a nested pane. At large font sizes, use wrapping `FlowRow` or vertical action groups rather than fixed-height rows. The global FAB never covers the final actionable row.

## Interaction, motion and haptics

Use motion to show causality and preserve position. These timing ranges are proposed product defaults. Android documents the [shared-element APIs](https://developer.android.com/develop/ui/compose/animation/shared-elements), including a shared parent scope and matching content keys; the [Material MotionScheme API](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme) describes standard and expressive schemes. Check public API availability in the resolved Material artifact before using `MaterialExpressiveTheme` or `MotionScheme.expressive()`. Start with explicit app animation specs if those APIs are unavailable; a library upgrade is not required for the early milestones.

| Token / event | Visual behavior | Proposed haptic mapping |
| --- | --- | --- |
| `press` | M3 ripple; no extra scale for ordinary controls | None |
| `selection` | 120–160 ms state/icon transition | `CLOCK_TICK` for meaningful preset/status selection only |
| `disclosure` | 180–220 ms size + fade; keep focused field in view | None |
| `file` / `undo` | 180–240 ms row removal/restoration, stable neighbors | One light confirmation on commit |
| `dragPickup` | 120 ms elevation/outline change, scale to 1.02 | `LONG_PRESS` |
| `dragTarget` | Highlight target lane; no repeated animation while hovering | At most one light tick on entering a new valid target |
| `dragDrop` | Spring to settled bounds, 200–280 ms target | `CONFIRM` on API 30+; one `CLOCK_TICK` fallback |
| `drawReveal` | 240–320 ms fade + scale 0.96→1; settle once | One confirmation when the result appears |
| `completion` | 180–240 ms check/tonal pulse; then optional reflection | `CONFIRM` with API fallback; once per user action |
| `coverExpand` | 280–360 ms matching-cover bounds transition | None |
| `captureExpand` | 240–320 ms shape/bounds expansion toward capture surface | Long press only when invoking voice |
| `saveFeedback` | Crossfade text/icon after acknowledgement | None; typing/autosave never vibrates |

Route haptics through one `AppHaptics` adapter using `LocalView.current.performHapticFeedback(...)`, with guarded API-specific constants and respect for system/user settings. Do not trigger a manual haptic in addition to an equivalent default gesture haptic. Android's [haptic feedback guidance](https://developer.android.com/develop/ui/views/haptics/haptic-feedback) recommends using platform feedback constants for interaction semantics.

Honor system animation settings and an app Reduce motion preference. At zero animation scale, apply the final state immediately; with Reduce motion, use a short opacity change and omit translation, scale, confetti and morphing. Completion must persist and become actionable without waiting for the celebration. Avoid an unending dice animation or artificial network-style delay for a local draw.

Shared cover keys are based on item ID, for example `item/42/cover`. Provide both shared-transition and destination animation scopes through the navigation/pane host. Fallback artwork can share bounds too. If there is no visible source element, use a normal fade. A standard `ModalBottomSheet` lives in a separate modal window; do not assume a root `sharedElement` can automatically morph a FAB into it. Phase 4 must either host a coordinated transition in one layout/window or use a matched shape/fade handoff while keeping the standard sheet's focus and dismissal behavior.

## Accessibility and contrast gates

Use 48 dp targets and explicit semantics for custom interactions; Compose's [accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) explain minimum target sizing and semantics. Offer ordinary buttons/menu actions for every swipe and drag operation. TalkBack must be able to file, delete, move, rate and draw without a precision gesture.

Use a 4.5:1 target for normal text and 3:1 for meaningful component/state indicators, following [W3C text contrast](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) and [non-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html). Treat the numbers as design acceptance targets and verify rendered combinations, including dynamic wallpaper palettes and image overlays.

Calculated from the specified sRGB hex pairs using relative luminance:

| Pair | Contrast ratio |
| --- | --- |
| Light primary button: white / `#006A6A` | 6.42:1 |
| Light body: `#161D1D` / `#F4FBFA` | 16.31:1 |
| Light supporting text: `#3F4948` / `#F4FBFA` | 8.87:1 |
| Dark body: `#DDE4E3` / `#0E1514` | 14.33:1 |
| AMOLED supporting text: `#C2C2C2` / `#181818` | 9.97:1 |
| AMOLED control outline: `#8C8C8C` / `#202020` | 4.85:1 |
| Current gold star `#F5B301` / Light canvas `#F4FBFA` | **1.77:1** |

The current gold needs a theme-aware replacement wherever it communicates rating/favorite state. Use `primary` for the meaningful icon and `onSurface` for `4.5 / 5`, or place decorative gold beside an independently legible value/state. Never use `outline` as supporting text merely to make it quieter; use `onSurfaceVariant`.

Announce completion and errors once. Do not announce every parser keystroke, interim count or Saved transition. Parsed tokens have readable names in the preview, save errors use a polite live region, rating exposes a value and adjustment actions, and Undo remains reachable for the accessibility-adjusted snackbar timeout. Verify at 200% font scale, RTL, keyboard/switch access and with long localized status labels. Fixed above-the-fold targets apply only at normal font size; scrolling is preferable to clipped controls.
