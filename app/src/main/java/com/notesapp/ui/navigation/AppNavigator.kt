package com.notesapp.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** One history for compact screens and expanded panes. Controlled departures first save the editor. */
class AppNavigator(private val navController: NavHostController, private val scope: CoroutineScope) {
    private var beforeNavigation: (suspend () -> Boolean)? = null
    private var navigating = false
    private val _failures = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val failures = _failures.asSharedFlow()

    internal fun beforeLeaving(save: suspend () -> Boolean): DisposableHandle {
        beforeNavigation = save
        return DisposableHandle { if (beforeNavigation === save) beforeNavigation = null }
    }

    private fun navigate(action: () -> Unit) {
        scope.launch(Dispatchers.Main.immediate) {
            if (navigating) return@launch
            navigating = true
            try {
                val canLeave = try { beforeNavigation?.invoke() != false }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    _failures.emit("Couldn’t save changes. Please try again before leaving.")
                    false
                }
                if (canLeave) action()
            }
            finally { navigating = false }
        }
    }

    private fun open(route: Any) = navigate {
        val current = navController.currentBackStackEntry
        if (current?.matchesRoute(route) == true) return@navigate
        val parent = navController.previousBackStackEntry
        // The parent/content relationship survives IME resize and compact/expanded changes.
        // Single-top by destination would reuse an editor for different route arguments.
        val replace = current != null && parent?.destination?.canShowBeside(current.destination) == true &&
            parent.destination.canShowRouteBeside(route)
        navController.navigate(route) {
            if (replace) popUpTo(checkNotNull(current).destination.id) { inclusive = true }
        }
    }

    fun openTopLevel(top: TopLevelDestination) = navigate {
        navController.navigate(top.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun back() = navigate { navController.navigateUp() }
    fun openSearch() = open(SearchRoute)
    fun openInbox() = open(InboxRoute)
    fun openSettings() = open(SettingsRoute)
    fun openDataTransfer() = open(DataTransferRoute)
    fun openHistory() = open(HistoryRoute)
    fun openNotificationSettings() = open(NotificationSettingsRoute)
    fun openAdvancedSettings() = open(AdvancedSettingsRoute)
    fun openBackupSettings() = open(BackupSettingsRoute)
    fun openInsights() = open(InsightsRoute)
    fun openRandom() = open(RandomLaunchRoute)
    fun openCollection(collectionId: Long) = open(CollectionDetailRoute(collectionId))
    fun openItem(itemId: Long) = open(ItemDetailRoute(itemId))
    fun newCollection(templateId: String? = null) = open(CollectionEditorRoute(templateId = templateId))
    fun editCollection(collectionId: Long) = open(CollectionEditorRoute(collectionId = collectionId))
    fun openNote(noteId: Long?) = open(NoteEditorRoute(noteId))
    fun openFact(factId: Long?) = open(FactEditorRoute(factId))
    fun backToCollections() = navigate { navController.popBackStack(CollectionsRoute, inclusive = false) }
}
