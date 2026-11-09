package com.notesapp.ui.collections

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.domain.model.ItemWithTags
import com.notesapp.domain.model.StatusRole
import com.notesapp.ui.components.*
import kotlin.math.roundToInt

private data class BoardColumn(val key: String?, val label: String, val role: StatusRole)
private data class DragSource(val entry: ItemWithTags, val card: Rect, val handle: Rect)
private data class BoardDrag(val source: DragSource, val pointer: Offset, val grab: Offset)

/** Proportional edge speed, capped at 800 dp/s. The center never scrolls. */
internal fun boardEdgeVelocity(position: Float, start: Float, end: Float, edge: Float, maximum: Float): Float {
    if (end <= start || edge <= 0 || position !in start..end) return 0f
    val band = minOf(edge, (end - start) / 2)
    return when {
        position < start + band -> -maximum * ((start + band - position) / band).coerceIn(0f, 1f)
        position > end - band -> maximum * ((position - end + band) / band).coerceIn(0f, 1f)
        else -> 0f
    }
}

/** No reorder state: a valid drop issues the same single status command as the chip menu. */
@Composable
internal fun StatusBoard(
    collection: CollectionEntity,
    items: List<ItemWithTags>,
    bottomPadding: Dp,
    onOpen: (Long) -> Unit,
    onMove: (Long, String, String) -> Unit,
) {
    val schema = collection.schema
    val known = schema.statuses.map { it.key }.toSet()
    val columns = buildList {
        schema.statuses.forEach { add(BoardColumn(it.key, it.label, it.role)) }
        if (items.any { it.item.status !in known }) add(BoardColumn(null, "Other", StatusRole.BACKLOG))
    }
    val sources = remember(collection.id) { mutableMapOf<Long, DragSource>() }
    val lanes = remember(collection.id) { mutableMapOf<String, Rect>() }
    val scrollRegions = remember(collection.id) { mutableMapOf<String, Rect>() }
    val laneStates = remember(collection.id) { mutableMapOf<String, LazyListState>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<BoardDrag?>(null) }
    val currentItems by rememberUpdatedState(items)
    val currentSchema by rememberUpdatedState(schema)
    val move by rememberUpdatedState(onMove)
    val boardState = rememberLazyListState()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val view = LocalView.current
    val edge = with(density) { 48.dp.toPx() }
    val maximum = with(density) { 800.dp.toPx() }
    fun target(value: BoardDrag?): String? {
        if (value == null || !viewport.contains(value.pointer)) return null
        return lanes.entries.firstOrNull { (key, bounds) -> bounds.contains(value.pointer) &&
            key != value.source.entry.item.status && currentSchema.statuses.any { it.key == key } }?.key
    }
    val target = target(drag)
    LaunchedEffect(target) {
        if (target != null) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    // External deletion/status/schema changes invalidate a pickup rather than overwriting newer data.
    LaunchedEffect(items, schema) {
        drag?.let { active ->
            val latest = items.firstOrNull { it.item.id == active.source.entry.item.id }?.item
            if (latest == null || latest.status != active.source.entry.item.status) drag = null
        }
    }
    BackHandler(enabled = drag != null) { drag = null }
    LaunchedEffect(drag != null, rtl, edge, maximum) {
        if (drag == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (drag != null) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceIn(0f, .032f)
            previous = now
            val active = drag ?: break
            val vx = boardEdgeVelocity(active.pointer.x, viewport.left, viewport.right, edge, maximum)
            if (vx != 0f) boardState.scrollBy(vx * seconds * if (rtl) -1 else 1)
            val lane = lanes.entries.firstOrNull { it.value.contains(active.pointer) }
            if (lane != null) {
                // Header and padding are valid drop areas too. Clamp their pointer to the list's
                // edge so holding over a column's padding still scrolls its cards.
                val region = scrollRegions[lane.key] ?: lane.value
                val bottom = minOf(region.bottom, viewport.bottom)
                val vy = if (bottom > region.top) boardEdgeVelocity(active.pointer.y.coerceIn(region.top, bottom), region.top, bottom, edge, maximum) else 0f
                if (vy != 0f) laneStates[lane.key]?.scrollBy(vy * seconds)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().onGloballyPositioned { viewport = it.boundsInWindow() }
        .pointerInput(collection.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    val window = local + viewport.topLeft
                    val source = sources.values.firstOrNull { it.handle.contains(window) && viewport.contains(window) }
                    if (source != null) {
                        drag = BoardDrag(source, window, window - source.card.topLeft)
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                },
                onDrag = { change, amount ->
                    drag?.let { change.consume(); drag = it.copy(pointer = it.pointer + amount) }
                },
                onDragCancel = { drag = null },
                onDragEnd = {
                    val active = drag
                    val destination = target(active)
                    drag = null // Clear before submitting; another release cannot issue the command again.
                    if (active != null && destination != null && currentItems.any {
                        it.item.id == active.source.entry.item.id && it.item.status == active.source.entry.item.status
                    }) {
                        move(active.source.entry.item.id, active.source.entry.item.status, destination)
                        view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30)
                            HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
                    }
                },
            )
        }) {
        val width = (maxWidth * .8f).coerceIn(260.dp, 340.dp)
        LazyRow(state = boardState, userScrollEnabled = drag == null,
            flingBehavior = if (drag == null) rememberSnapFlingBehavior(boardState) else ScrollableDefaults.flingBehavior(),
            modifier = Modifier.fillMaxSize().testTag("board"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(columns, key = { it.key ?: "other" }) { column ->
                val key = column.key ?: "other"
                val listState = rememberLazyListState()
                DisposableEffect(key) { laneStates[key] = listState; onDispose { laneStates.remove(key); lanes.remove(key); scrollRegions.remove(key) } }
                val cards = items.filter { if (column.key != null) it.item.status == column.key else it.item.status !in known }
                val valid = drag != null && column.key != null && column.key != drag?.source?.entry?.item?.status
                Surface(color = if (target == column.key && target != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(if (target == column.key && target != null) 2.dp else 1.dp,
                        if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.width(width).fillMaxHeight().onGloballyPositioned { if (column.key != null) lanes[key] = it.boundsInWindow() }
                        .testTag("board_column_$key")) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusLabel(column.label, column.role, Modifier.weight(1f, fill = false))
                            Text(cards.size.toString(), style = MaterialTheme.typography.labelMedium)
                        }
                        LazyColumn(state = listState, userScrollEnabled = drag == null,
                            contentPadding = PaddingValues(bottom = bottomPadding),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize().onGloballyPositioned { if (column.key != null) scrollRegions[key] = it.boundsInWindow() }
                                .testTag("board_lane_$key")) {
                            items(cards, key = { it.item.id }) { entry ->
                                var cardBounds by remember { mutableStateOf(Rect.Zero) }
                                DisposableEffect(entry.item.id) { onDispose { sources.remove(entry.item.id) } }
                                KanbanCard(entry, schema, onClick = { onOpen(entry.item.id) }, onMoveTo = { move(entry.item.id, entry.item.status, it) },
                                    collection = collection,
                                    modifier = Modifier.onGloballyPositioned { cardBounds = it.boundsInWindow() }
                                        .graphicsLayer { alpha = if (drag?.source?.entry?.item?.id == entry.item.id) .3f else 1f },
                                    dragHandle = { openMenu ->
                                        IconButton(onClick = openMenu, modifier = Modifier.size(48.dp).onGloballyPositioned {
                                            sources[entry.item.id] = DragSource(entry, cardBounds, it.boundsInWindow())
                                        }.testTag("board_drag_${entry.item.id}").semantics {
                                            contentDescription = "Move ${entry.item.title}. Tap for statuses or long press and drag."
                                        }) {
                                            Icon(Icons.Default.DragIndicator, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    })
                            }
                        }
                    }
                }
            }
        }
        drag?.let { active ->
            val position = active.pointer - viewport.topLeft - active.grab
            Surface(Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .width(with(density) { active.source.card.width.toDp() }).graphicsLayer { scaleX = 1.02f; scaleY = 1.02f }
                .testTag("board_drag_preview").clearAndSetSemantics { },
                shape = MaterialTheme.shapes.medium, shadowElevation = 8.dp, border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(active.source.entry.item.title, style = MaterialTheme.typography.titleSmall)
                    ItemMetadataLine(active.source.entry, collection)
                    Text(target?.let { "Move to ${schema.labelOf(it)}" } ?: "Choose a status column", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
