package com.notesapp.ui.motion

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** App choice and the live system setting both suppress decorative motion. */
data class MotionPolicy(val reduceMotion: Boolean = false, val animatorScale: Float = 1f) {
    val enabled: Boolean get() = !reduceMotion && animatorScale > 0f
    fun duration(normal: Int): Int = if (enabled) normal else 0
}

val LocalAppMotion = staticCompositionLocalOf { MotionPolicy() }
internal val LocalCoverTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }
internal val LocalCoverVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun rememberMotionPolicy(reduceMotion: Boolean): MotionPolicy {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    var scale by remember(resolver) { mutableFloatStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scale = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        // Close the read/register race, including settings changed while this composition was absent.
        scale = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return MotionPolicy(reduceMotion, scale)
}

@Composable
internal fun Modifier.sharedItemCover(id: Long): Modifier {
    val scope = LocalCoverTransition.current
    val visibility = LocalCoverVisibility.current
    if (!LocalAppMotion.current.enabled || scope == null || visibility == null) return this
    return with(scope) {
        sharedBounds(rememberSharedContentState("item-cover-$id"), visibility,
            boundsTransform = { _, _ -> androidx.compose.animation.core.tween(320) },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds())
    }
}
