# Notes App UI/UX redesign

Design and engineering proposal · November 6, 2026 · baseline: app 0.4.0, Room schema 2.

Implementation is recorded in [milestone 1](../redesign-milestone1.md), [milestone 2](../redesign-milestone2.md), [milestone 3](../redesign-milestone3.md) and milestone 4’s [adaptive foundation](../redesign-milestone4.md) and [motion/Board quality pass](../redesign-milestone4-polish.md). Those records distinguish completed work from remaining acceptance gates. The audit below describes the retained pre-redesign baseline.

Make the core loop feel immediate: **capture quickly → file when convenient → decide what to do next**. The most valuable change is a dependable, consistent editing model, followed by putting the next useful action in reach. Expressive typography, tonal surfaces, and motion support that hierarchy.

## Deliverables

| Document | Contents |
| --- | --- |
| [1. Prioritized UX audit](01-ux-audit.md) | Ranked findings, screenshot/code evidence, exact remedies, impact and effort |
| [2. Design system and M3 tokens](02-design-system.md) | Color roles, Light/Dark/AMOLED rules, typography, spacing, shape, accessibility, motion and haptics |
| [3. Screen redesign and Compose specifications](03-screen-specs.md) | Layouts and interaction contracts for Today, Capture, Inbox, Collections, Item Detail, Vault, Discover, adaptive panes and privacy surfaces |
| [4. Phased implementation roadmap](04-roadmap.md) | Four incremental milestones, source ownership, dependencies, acceptance criteria and verification |

## Recommended first release

Unify Item Detail saving, move Due Soon above editorial content, add direct reminder/workflow actions, and offer metadata consent in place. Follow with capture assistance and Inbox batch triage. Save shared transitions and tablet panes for the final milestone, after persistence and action semantics are stable.

The four design documents describe the target behavior; current implementation status is tracked in the [milestone 1](../redesign-milestone1.md), [milestone 2](../redesign-milestone2.md), [milestone 3](../redesign-milestone3.md) and milestone 4’s [adaptive](../redesign-milestone4.md) and [motion/Board quality records](../redesign-milestone4-polish.md). The redesign milestones are separate from the original product's Phases 1–4, already described as implemented in the [project README](../../README.md).

## Evidence and limits

The audit combines visual inspection of 18 retained captures with the current Compose screens, ViewModels, repository methods, theme, parser, random-selection logic, metadata model, backup controls and app-lock implementation. Individual findings link to their evidence. Inspected captures:

- Core: Today, Quick Capture, Collections, movie List/Grid/Board, Item Detail, Vault Notes/Facts, Discover filters/result, Inbox, and Today in Dark mode.
- Phase 2: Due reminders and linked reflection.
- Phases 3/4: Book metadata, forgotten backlog and automatic backup controls.

The captures use synthetic data on the API 34 emulator. No new device run, usability study, screen-reader session, physical haptic assessment, or tablet/foldable assessment was performed for this proposal. Risks inferred from code are labeled accordingly. Existing verification remains in [docs/verification.md](../verification.md); its historical test results do not validate this redesign.

Engineering recommendations were checked against official Android/Material documentation. API availability must also be checked against the project's resolved artifacts: the catalog pins Compose BOM `2026.09.00`; the local cache contains Material 3 `1.4.0` and Foundation `1.12.1`. Cached artifacts alone do not establish the resolved dependency graph. The roadmap includes an API compatibility check before adopting Expressive theming, rich input styling or adaptive pane APIs.

## Product decisions

- Keep the four destinations and their labels. Give the global `+` one consistent capture meaning across them.
- Use status **roles**, rather than displayed labels, for Start, Resume, Complete, filters and insights. Custom workflows remain first-class.
- Let people act from Today, Inbox and Discover; opening detail is optional for routine updates.
- Preserve user-authored title, notes, tags and workflow state during metadata enrichment. Show exactly which provider fields will change.
- Keep manual capture, editing, triage, search, covers already on the device and discovery usable offline. Online lookup remains explicit and optional.
- Keep app-lock redaction at least as strict as it is today, including external entry points and the existing always-redacted fact widget when App Lock is enabled.

Success is evaluated through the task scenarios and release gates in the [roadmap](04-roadmap.md), using synthetic data and moderated testing rather than adding telemetry by default.
