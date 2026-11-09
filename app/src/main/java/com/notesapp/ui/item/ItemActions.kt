package com.notesapp.ui.item

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.notesapp.data.repository.ItemRepository
import com.notesapp.data.repository.ItemUndo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class ItemActionMessage(val text: String, val undo: ItemUndo? = null, val reverse: (suspend () -> Boolean)? = null, val expiresAt: Long? = null)

@Singleton
class ItemActions @Inject constructor(private val items: ItemRepository) {
    private val messages = Channel<ItemActionMessage>(Channel.UNLIMITED)
    val events = messages.receiveAsFlow()
    suspend fun report(message: String) { messages.send(ItemActionMessage(message)) }
    suspend fun reversible(message: String, expiresAt: Long? = null, reverse: suspend () -> Boolean) { messages.send(ItemActionMessage(message, reverse = reverse, expiresAt = expiresAt)) }
    fun remaining(event: ItemActionMessage): Long? = event.expiresAt?.let { (it - items.now()).coerceAtLeast(0) }
    suspend fun reverse(action: suspend () -> Boolean) {
        try { if (!action()) report("Couldn't undo. The items changed or the Undo window expired.") }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { report("Couldn't undo. Please try again.") }
    }
    suspend fun change(id: Long, label: String, action: suspend () -> Unit) {
        val undo = items.undoable(id, action) ?: return
        messages.send(ItemActionMessage(label, undo))
    }
    suspend fun undo(change: ItemUndo) {
        try {
            if (!items.undo(change)) report("Couldn't undo because this item changed again.")
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { report("Couldn't undo. Please try again.") }
    }
}

@HiltViewModel
class ItemActionFeedbackViewModel @Inject constructor(val actions: ItemActions) : ViewModel()

@Composable
fun ItemActionFeedback(host: SnackbarHostState, modifier: Modifier = Modifier, viewModel: ItemActionFeedbackViewModel = hiltViewModel()) {
    LaunchedEffect(viewModel) {
        viewModel.actions.events.collect { event ->
            // Consume in order; evaluate a deletion deadline when its snackbar can actually appear.
            val remaining = viewModel.actions.remaining(event)
            if (remaining == 0L) return@collect
            suspend fun show() = host.showSnackbar(event.text, actionLabel = if (event.undo != null || event.reverse != null) "Undo" else null,
                    withDismissAction = true, duration = SnackbarDuration.Long)
            val result = if (remaining == null) show() else withTimeoutOrNull(remaining) { show() }
            if (result == SnackbarResult.ActionPerformed) event.undo?.let { viewModel.actions.undo(it) }
            if (result == SnackbarResult.ActionPerformed) event.reverse?.let { viewModel.actions.reverse(it) }
        }
    }
    SnackbarHost(host, modifier)
}
