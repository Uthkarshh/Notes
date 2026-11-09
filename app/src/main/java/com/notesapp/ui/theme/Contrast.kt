package com.notesapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

fun contrastRatio(foreground: Color, background: Color): Float {
    val a = foreground.luminance()
    val b = background.luminance()
    return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
}

/** Collection colors are user data; choose readable ink rather than assuming white works. */
fun contrastInk(background: Color): Color =
    if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) Color.Black else Color.White

fun accessibleAccent(accent: Color, background: Color): Color {
    if (contrastRatio(accent, background) >= 3f) return accent
    val ink = contrastInk(background)
    return (1..20).asSequence().map { lerp(accent, ink, it / 20f) }
        .firstOrNull { contrastRatio(it, background) >= 3f } ?: ink
}
