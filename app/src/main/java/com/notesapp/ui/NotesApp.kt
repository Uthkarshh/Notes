package com.notesapp.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.navigation.toRoute
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.notesapp.ui.capture.QuickCaptureSheet
import com.notesapp.ui.capture.QuickCaptureViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.notesapp.ui.collections.CollectionEditorScreen
import com.notesapp.ui.collections.CollectionsScreen
import com.notesapp.ui.discover.DiscoverScreen
import com.notesapp.ui.inbox.InboxScreen
import com.notesapp.ui.item.ItemDetailScreen
import com.notesapp.ui.navigation.AppNavigator
import com.notesapp.ui.navigation.CollectionDetailRoute
import com.notesapp.ui.navigation.CollectionEditorRoute
import com.notesapp.ui.navigation.CollectionsRoute
import com.notesapp.ui.navigation.DiscoverRoute
import com.notesapp.ui.navigation.FactEditorRoute
import com.notesapp.ui.navigation.InboxRoute
import com.notesapp.ui.navigation.ItemDetailRoute
import com.notesapp.ui.navigation.NoteEditorRoute
import com.notesapp.ui.navigation.SearchRoute
import com.notesapp.ui.navigation.SettingsRoute
import com.notesapp.ui.navigation.TodayRoute
import com.notesapp.ui.navigation.TopLevelDestination
import com.notesapp.ui.navigation.VaultRoute
import com.notesapp.ui.today.TodayScreen
import com.notesapp.ui.vault.FactEditorScreen
import com.notesapp.ui.vault.NoteEditorScreen

/** Single-activity root: adaptive navigation (bar on phones, rail on tablets/foldables) around a type-safe NavHost. */
@Composable
fun NotesApp(navController: NavHostController = rememberNavController(), launchTarget: Pair<String, Long>? = null, onLaunchHandled: () -> Unit = {}) {
    val modalCoordinator = remember { com.notesapp.ui.motion.ModalCoordinator() }
    androidx.compose.runtime.CompositionLocalProvider(com.notesapp.ui.motion.LocalModalCoordinator provides modalCoordinator) {
        NotesAppContent(navController, launchTarget, onLaunchHandled)
    }
}

@Composable
private fun NotesAppContent(navController: NavHostController, launchTarget: Pair<String, Long>?, onLaunchHandled: () -> Unit) {
    val navigationScope = androidx.compose.runtime.rememberCoroutineScope()
    val navigator = remember(navController, navigationScope) { AppNavigator(navController, navigationScope) }
    val context = LocalContext.current
    val actionSnackbar = remember { SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(navigator) {
        navigator.failures.collect { actionSnackbar.showSnackbar(it) }
    }
    val captureViewModel: QuickCaptureViewModel = hiltViewModel()
    var showCapture by rememberSaveable { mutableStateOf(false) }
    var snackbarHeight by remember { mutableIntStateOf(0) }
    val actionInset = if (actionSnackbar.currentSnackbarData == null) 0.dp else with(LocalDensity.current) { snackbarHeight.toDp() }
    androidx.compose.runtime.LaunchedEffect(launchTarget) {
        launchTarget?.let { (type, id) ->
            when (type) { "item" -> navigator.openItem(id); "fact" -> navigator.openFact(id); "random" -> navigator.openRandom() }
            onLaunchHandled()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val paneParent = com.notesapp.ui.navigation.paneParent(backStackEntry, navController.previousBackStackEntry)
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val wideWindow = with(LocalDensity.current) { windowSize.width.toDp() >= 600.dp }
    var lastTopLevel by rememberSaveable { mutableStateOf(TopLevelDestination.TODAY) }
    val collectionContext = if (destination?.hasRoute<CollectionDetailRoute>() == true) backStackEntry?.toRoute<CollectionDetailRoute>()?.collectionId
        else if (wideWindow && paneParent?.destination?.hasRoute<CollectionDetailRoute>() == true) paneParent.toRoute<CollectionDetailRoute>().collectionId else null
    val topLevel = TopLevelDestination.entries.firstOrNull { top ->
        destination?.hierarchy?.any { it.hasRoute(top.route::class) } == true
    } ?: if (destination?.hasRoute(com.notesapp.ui.navigation.RandomLaunchRoute::class) == true) TopLevelDestination.DISCOVER else null

    androidx.compose.runtime.SideEffect { if (topLevel != null) lastTopLevel = topLevel }
    val visibleTopLevel = topLevel ?: if (wideWindow) when {
        paneParent?.destination?.hasRoute<CollectionDetailRoute>() == true -> TopLevelDestination.COLLECTIONS
        paneParent?.destination?.hasRoute<VaultRoute>() == true -> TopLevelDestination.VAULT
        else -> lastTopLevel
    } else null
    val layoutType = if (visibleTopLevel != null) {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    } else {
        NavigationSuiteType.None
    }

    val captureEmphasis by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showCapture) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(com.notesapp.ui.motion.LocalAppMotion.current.duration(180)),
        label = "Capture handoff",
    )
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { top ->
                item(
                    selected = top == visibleTopLevel,
                    onClick = { navigator.openTopLevel(top) },
                    icon = { Icon(top.icon, contentDescription = null) },
                    label = { Text(top.label) },
                    modifier = Modifier.testTag("nav_${top.name.lowercase()}"),
                )
            }
        },
        layoutType = layoutType,
    ) {
        Box(Modifier.fillMaxSize()) {
            com.notesapp.ui.navigation.AdaptiveAppContent(navController, navigator, backStackEntry, paneParent, Modifier.padding(bottom = actionInset))
            if (topLevel != null || collectionContext != null || (wideWindow && paneParent?.destination?.hasRoute<VaultRoute>() == true)) Box(
                modifier = Modifier.align(if (wideWindow && paneParent != null) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(horizontal = 16.dp).padding(bottom = 16.dp + actionInset).size(56.dp)
                    .then(Modifier.graphicsLayer {
                        alpha = captureEmphasis
                        scaleX = .85f + .15f * captureEmphasis
                        scaleY = scaleX
                    })
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large)
                    .combinedClickable(onClick = { showCapture = true }, onLongClick = {
                        context.startActivity(com.notesapp.launcher.LauncherActions.intent(context, "voice"))
                    }, onLongClickLabel = "Voice capture", role = androidx.compose.ui.semantics.Role.Button)
                    .testTag(if (collectionContext == null) "fab_capture" else "fab_add_item"),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Add, "Quick capture", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            com.notesapp.ui.item.ItemActionFeedback(actionSnackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()
                    .onSizeChanged { snackbarHeight = it.height }.padding(16.dp))
        }
    }
    if (showCapture) QuickCaptureSheet(onDismiss = { showCapture = false }, onSaved = {},
        initialCollectionId = collectionContext, viewModel = captureViewModel)
    com.notesapp.ui.item.CompletionReflection()
}

@Composable
fun AppNavHost(navController: NavHostController, navigator: AppNavigator, modifier: Modifier = Modifier) {
    val motion = com.notesapp.ui.motion.LocalAppMotion.current
    androidx.compose.animation.SharedTransitionLayout(modifier = modifier) {
      androidx.compose.runtime.CompositionLocalProvider(com.notesapp.ui.motion.LocalCoverTransition provides this) {
       NavHost(navController = navController, startDestination = TodayRoute,
        enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(motion.duration(180))) },
        exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(motion.duration(180))) },
        popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(motion.duration(180))) },
        popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(motion.duration(180))) }) {
        composable<TodayRoute> { TodayScreen(navigator) }
        composable<CollectionsRoute> { CollectionsScreen(navigator) }
        composable<VaultRoute> { com.notesapp.ui.navigation.PaneListDestination(it, navigator) }
        composable<DiscoverRoute> { DiscoverScreen(navigator) }
        composable<CollectionDetailRoute> {
            androidx.compose.runtime.CompositionLocalProvider(com.notesapp.ui.motion.LocalCoverVisibility provides this) {
                com.notesapp.ui.navigation.PaneListDestination(it, navigator)
            }
        }
        composable<ItemDetailRoute> {
            androidx.compose.runtime.CompositionLocalProvider(com.notesapp.ui.motion.LocalCoverVisibility provides this) {
                ItemDetailScreen(navigator)
            }
        }
        composable<CollectionEditorRoute> { CollectionEditorScreen(navigator) }
        composable<NoteEditorRoute> { NoteEditorScreen(navigator) }
        composable<FactEditorRoute> { FactEditorScreen(navigator) }
        composable<SearchRoute> { com.notesapp.ui.navigation.PaneListDestination(it, navigator) }
        composable<InboxRoute> { InboxScreen(navigator) }
        composable<SettingsRoute> { com.notesapp.ui.navigation.PaneListDestination(it, navigator) }
        composable<com.notesapp.ui.navigation.DataTransferRoute> { com.notesapp.ui.transfer.DataTransferScreen(navigator) }
        composable<com.notesapp.ui.navigation.HistoryRoute> { com.notesapp.ui.history.HistoryScreen(navigator) }
        composable<com.notesapp.ui.navigation.NotificationSettingsRoute> { com.notesapp.ui.settings.NotificationSettingsScreen(navigator) }
        composable<com.notesapp.ui.navigation.RandomLaunchRoute> { DiscoverScreen(navigator, autoDraw = true) }
        composable<com.notesapp.ui.navigation.AdvancedSettingsRoute> { com.notesapp.ui.settings.AdvancedSettingsScreen(navigator) }
        composable<com.notesapp.ui.navigation.BackupSettingsRoute> { com.notesapp.ui.backup.BackupScreen(navigator) }
        composable<com.notesapp.ui.navigation.InsightsRoute> { com.notesapp.ui.insights.InsightsScreen(navigator) }
    }
      }
    }
}
