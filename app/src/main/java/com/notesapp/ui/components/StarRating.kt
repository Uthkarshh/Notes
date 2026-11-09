package com.notesapp.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val MAX_STARS = 5

/** Snaps any value to the closest valid rating (0.5 steps between 0.5 and 5.0). */
fun snapRating(value: Float): Float = ((value * 2).roundToInt() / 2f).coerceIn(0.5f, MAX_STARS.toFloat())

fun formatRating(rating: Float): String = if (rating % 1f == 0f) rating.toInt().toString() else rating.toString()

private fun starIcon(index: Int, rating: Float): ImageVector = when {
    rating >= index -> Icons.Filled.Star
    rating >= index - 0.5f -> Icons.AutoMirrored.Filled.StarHalf
    else -> Icons.Filled.StarBorder
}

/** Read-only stars for lists. */
@Composable
fun RatingStars(rating: Float, modifier: Modifier = Modifier, starSize: Dp = 14.dp) {
    Row(
        modifier = modifier.semantics { contentDescription = "Rated ${formatRating(rating)} out of 5" },
    ) {
        for (i in 1..MAX_STARS) {
            Icon(
                imageVector = starIcon(i, rating),
                contentDescription = null,
                tint = RatingColor,
                modifier = Modifier.size(starSize),
            )
        }
    }
}

/**
 * Star scrubber with half-star precision: tap a star half or drag across the row. Exposes
 * range semantics so assistive tech and tests can set the value too. `null` means unrated.
 */
@Composable
fun StarRatingBar(
    rating: Float?,
    onRatingChange: (Float?) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 40.dp,
) {
    val current = rating ?: 0f
    var widthPx by remember { mutableIntStateOf(0) }
    val latestOnChange by rememberUpdatedState(onRatingChange)

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestValueAt by rememberUpdatedState<(Float) -> Float> { x -> ratingAtPosition(x, widthPx, rtl) }
    fun valueAt(x: Float): Float {
        return latestValueAt(x)
    }

    Row(
        modifier = modifier
            .onSizeChanged { widthPx = it.width }
            .semantics {
                contentDescription = "Rating"
                stateDescription = rating?.let { "${formatRating(it)} out of 5" } ?: "Not rated"
                progressBarRangeInfo = ProgressBarRangeInfo(current, 0f..MAX_STARS.toFloat(), steps = MAX_STARS * 2 - 1)
                customActions = listOf(
                    CustomAccessibilityAction("Increase rating") { latestOnChange(snapRating(current + .5f)); true },
                    CustomAccessibilityAction("Decrease rating") { latestOnChange(if (current <= .5f) null else snapRating(current - .5f)); true },
                    CustomAccessibilityAction("Clear rating") { latestOnChange(null); true },
                )
                setProgress { target ->
                    latestOnChange(if (target <= 0f) null else snapRating(target))
                    true
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else {
                    val value = when (event.key) {
                        Key.DirectionRight -> current + if (rtl) -.5f else .5f
                        Key.DirectionLeft -> current + if (rtl) .5f else -.5f
                        Key.DirectionUp -> current + .5f
                        Key.DirectionDown -> current - .5f
                        Key.MoveHome, Key.Delete, Key.Backspace -> 0f
                        Key.MoveEnd -> 5f
                        else -> return@onKeyEvent false
                    }.coerceIn(0f, 5f)
                    latestOnChange(if (value == 0f) null else snapRating(value))
                    true
                }
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { offset -> latestOnChange(valueAt(offset.x)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    latestOnChange(valueAt(change.position.x))
                }
            },
    ) {
        for (i in 1..MAX_STARS) {
            Icon(
                imageVector = starIcon(i, current),
                contentDescription = null,
                tint = if (current >= i - 0.5f) RatingColor else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(starSize),
            )
        }
    }
}

val RatingColor: Color
    @Composable get() = MaterialTheme.colorScheme.primary

/** Physical position follows the visual star order in both layout directions. */
internal fun ratingAtPosition(x: Float, width: Int, rtl: Boolean): Float {
    if (width <= 0) return .5f
    val fraction = (x / width).coerceIn(0f, 1f)
    return snapRating(ceil((if (rtl) 1f - fraction else fraction) * MAX_STARS * 2) / 2f)
}
