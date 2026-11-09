package com.notesapp.data.metadata

import com.notesapp.domain.model.CategoryType

interface MetadataSource {
    suspend fun search(query: String, category: CategoryType): List<MetadataResult>
    suspend fun details(result: MetadataResult, category: CategoryType): MetadataResult
    suspend fun downloadCover(url: String): String
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
abstract class MetadataModule {
    @dagger.Binds abstract fun source(lookup: MetadataLookup): MetadataSource
}
