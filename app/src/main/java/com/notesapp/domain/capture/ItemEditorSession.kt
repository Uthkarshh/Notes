package com.notesapp.domain.capture

import com.notesapp.domain.metadata.ItemMetadata
import com.notesapp.domain.metadata.MetadataFields
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

interface ItemEditorStore {
    suspend fun read(): Map<String, String>?
    suspend fun draft(): Map<String, String>
    suspend fun journal(fields: Map<String, String>)
    suspend fun commit(fields: Map<String, String>)
    fun changes(): Flow<Unit>
}

data class ItemEditorState(
    val ready: Boolean = false,
    val missing: Boolean = false,
    val fields: Map<String, String> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    val pending: Boolean = false,
    val failure: String? = null,
    val restored: Boolean = false,
)

object ItemEditFields {
    const val TITLE = "title"
    const val NOTES = "description"
    const val LINK = "externalUrl"
    const val TAGS = "tags"
    fun tags(raw: String): List<String> = Json.decodeFromString(raw)
    fun error(key: String, raw: String): String? = runCatching {
        when (key) {
            TITLE -> require(raw.isNotBlank()) { "A title is required. Your saved title is kept." }
            NOTES, LINK -> Unit
            TAGS -> tags(raw)
            else -> MetadataFields.apply(ItemMetadata(), key, raw)
        }
    }.exceptionOrNull()?.message
}

/**
 * Called on one UI dispatcher. The supplied scope outlives the screen; only debounce timers are
 * cancelled. Journal, commit and mutations execute serially, including the final close/flush.
 */
class ItemEditorSession(private val store: ItemEditorStore, private val scope: CoroutineScope) {
    private data class Edit(val raw: String, val revision: Long)
    private data class Work(val flush: Boolean = false, val action: (suspend () -> Unit)? = null,
                            val reply: CompletableDeferred<Boolean>? = null, val close: Boolean = false,
                            val refresh: Boolean = false)
    private val work = Channel<Work>(Channel.UNLIMITED)
    private val _state = MutableStateFlow(ItemEditorState())
    val state = _state.asStateFlow()
    private var dirty = emptyMap<String, Edit>()
    private var revision = 0L
    private var timer: Job? = null
    private var closing = false
    private var observer: Job? = null

    init {
        scope.launch {
            try {
                val saved = store.read()
                if (saved == null) {
                    _state.value = ItemEditorState(ready = true, missing = true)
                } else {
                    val recovered = store.draft()
                    dirty = recovered.mapValues { Edit(it.value, ++revision) }
                    _state.value = ItemEditorState(ready = true, fields = saved + recovered,
                        errors = errors(), pending = recovered.isNotEmpty(), restored = recovered.isNotEmpty())
                    observer = scope.launch { store.changes().collect { work.send(Work(refresh = true)) } }
                    if (recovered.isNotEmpty()) work.send(Work(flush = true))
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = ItemEditorState(failure = "Couldn't load this editor. Reopen to retry.") }
            for (next in work) {
                var success = false
                try {
                    if (next.refresh) refresh()
                    else {
                        check(_state.value.ready) { "Editor is not loaded." }
                        journal()
                        if (next.flush) save()
                        next.action?.invoke()
                        if (next.action != null) refresh()
                    }
                    success = true
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    _state.value = _state.value.copy(failure = "Couldn't save. Your edits are kept here. Try again.")
                } finally {
                    next.reply?.complete(success)
                }
                if (next.close) {
                    observer?.cancel()
                    work.close()
                    break
                }
            }
        }
    }

    fun edit(key: String, raw: String) = edit(mapOf(key to raw))
    fun edit(fields: Map<String, String>) {
        if (!_state.value.ready || _state.value.missing || closing) return
        fields.forEach { (key, raw) -> dirty = dirty + (key to Edit(raw, ++revision)) }
        _state.value = _state.value.copy(fields = _state.value.fields + fields, errors = errors(), pending = true, failure = null)
        work.trySend(Work()) // Durable raw journal; no text is discarded because it is temporarily invalid.
        timer?.cancel()
        timer = scope.launch { delay(500); work.send(Work(flush = true)) }
    }

    suspend fun flush(): Boolean {
        if (closing) return false
        timer?.cancel()
        val reply = CompletableDeferred<Boolean>()
        work.send(Work(flush = true, reply = reply))
        return reply.await()
    }

    suspend fun mutate(action: suspend () -> Unit): Boolean {
        if (closing) return false
        timer?.cancel()
        val reply = CompletableDeferred<Boolean>()
        work.send(Work(flush = true, action = action, reply = reply))
        return reply.await()
    }

    fun close() {
        if (closing) return
        closing = true
        timer?.cancel()
        work.trySend(Work(flush = true, close = true))
    }

    private fun errors() = dirty.mapNotNull { (key, edit) -> ItemEditFields.error(key, edit.raw)?.let { key to it } }.toMap()
    private suspend fun journal() { store.journal(dirty.mapValues { it.value.raw }) }
    private suspend fun refresh() {
        val saved = store.read()
        if (saved == null) {
            dirty = emptyMap()
            _state.value = _state.value.copy(missing = true, pending = false)
        } else _state.value = _state.value.copy(fields = saved + dirty.mapValues { it.value.raw })
    }
    private suspend fun save() {
        val submitted = dirty.filter { ItemEditFields.error(it.key, it.value.raw) == null }
        if (submitted.isNotEmpty()) {
            store.commit(submitted.mapValues { it.value.raw })
            // An acknowledgement for an older revision never clears a newer edit.
            dirty = dirty.filter { (key, value) -> submitted[key]?.revision != value.revision }
        }
        journal()
        refresh()
        _state.value = _state.value.copy(errors = errors(), pending = dirty.isNotEmpty(), failure = null)
    }
}
