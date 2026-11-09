# Redesign milestone 3 screen tour

API 34 emulator captures with synthetic data. The provider-review image uses an injected local fixture; it is not a live provider response. The final image renders shared item components at 200% font scale within the test composition, without changing the emulator's global font setting.

| State | Capture |
| --- | --- |
| Collection List with shared metadata and status actions | [01_collection_list.png](01_collection_list.png) |
| Grid with cover fallbacks and text below the artwork | [02_collection_grid.png](02_collection_grid.png) |
| Compact Item Detail with expandable secondary groups | [03_compact_detail.png](03_compact_detail.png) |
| Discover presets, summary and eligible count | [04_discover_presets.png](04_discover_presets.png) |
| Draft advanced filters with Apply/Cancel | [05_filter_draft.png](05_filter_draft.png) |
| Current/provider field review before application | [06_metadata_review.png](06_metadata_review.png) |
| Editorial Vault notes | [07_vault_notes.png](07_vault_notes.png) |
| Fact mastery action | [08_vault_facts.png](08_vault_facts.png) |
| Long title and metadata at 200% fonts | [09_grid_large_font.png](09_grid_large_font.png) |
| Still interested? backlog review | [10_backlog_review.png](10_backlog_review.png) |

See the [implementation and verification record](../../redesign-milestone3.md). `RedesignMilestoneThreeTour`, `MetadataReviewTest#fullDetailsAreReviewedAndOnlySelectedFieldsAreApplied` and `ItemPresentationLargeFontTest` produce these images when instrumentation receives `-e tour true`. The first nine files are saved under the app's external-files `redesign-m3` directory. The backlog image is copied from the final `Phase34Tour` output in `phase34-tour/10_forgotten_backlog.png`; the historical documentation image remains unchanged.
