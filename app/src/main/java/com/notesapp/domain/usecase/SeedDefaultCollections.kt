package com.notesapp.domain.usecase

import com.notesapp.data.prefs.SettingsRepository
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.domain.template.CollectionTemplates
import javax.inject.Inject

/** Creates the default collections (Movies, Books, Places, Things to Buy) on first launch only. */
class SeedDefaultCollections @Inject constructor(
    private val collections: CollectionRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke() {
        if (settings.isSeeded()) return
        // Never seed on top of existing data, e.g. if the process died between seeding and setting the flag.
        if (collections.getAll().isEmpty()) {
            collections.createAll(CollectionTemplates.defaults)
        }
        settings.markSeeded()
    }
}
