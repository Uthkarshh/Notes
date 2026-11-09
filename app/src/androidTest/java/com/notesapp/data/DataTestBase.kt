package com.notesapp.data

import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.domain.template.CollectionTemplates
import org.junit.After
import org.junit.Before

/** Creates a fresh in-memory environment per test and offers a few helpers. */
abstract class DataTestBase {
    protected lateinit var env: TestEnv

    @Before
    fun createEnv() {
        env = TestEnv()
    }

    @After
    fun closeEnv() {
        env.close()
    }

    protected suspend fun collection(templateId: String = "movies"): CollectionEntity {
        val id = env.collections.createFromTemplate(checkNotNull(CollectionTemplates.byId(templateId)))
        return checkNotNull(env.collections.get(id))
    }
}
