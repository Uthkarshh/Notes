package com.notesapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Icons a collection can use. The stored `iconName` is one of the keys below. */
object CollectionIcons {
    private val icons: Map<String, ImageVector> = linkedMapOf(
        "movie" to Icons.Filled.Movie,
        "tv" to Icons.Filled.Tv,
        "book" to Icons.AutoMirrored.Filled.MenuBook,
        "article" to Icons.AutoMirrored.Filled.Article,
        "games" to Icons.Filled.SportsEsports,
        "school" to Icons.Filled.School,
        "place" to Icons.Filled.Place,
        "restaurant" to Icons.Filled.Restaurant,
        "shopping" to Icons.Filled.ShoppingCart,
        "idea" to Icons.Filled.Lightbulb,
        "folder" to Icons.Filled.Folder,
        "star" to Icons.Filled.Star,
        "favorite" to Icons.Filled.Favorite,
        "music" to Icons.Filled.MusicNote,
        "travel" to Icons.Filled.Flight,
        "fitness" to Icons.Filled.FitnessCenter,
        "pets" to Icons.Filled.Pets,
        "home" to Icons.Filled.Home,
        "build" to Icons.Filled.Build,
        "brush" to Icons.Filled.Brush,
        "code" to Icons.Filled.Code,
        "cafe" to Icons.Filled.LocalCafe,
        "camera" to Icons.Filled.CameraAlt,
        "science" to Icons.Filled.Science,
        "park" to Icons.Filled.Park,
        "car" to Icons.Filled.DirectionsCar,
        "gift" to Icons.Filled.CardGiftcard,
        "bookmark" to Icons.Filled.Bookmark,
        "headphones" to Icons.Filled.Headphones,
        "theater" to Icons.Filled.Theaters,
    )

    val names: List<String> = icons.keys.toList()

    fun get(name: String): ImageVector = icons[name] ?: Icons.Filled.Folder
}

/** Accent colors offered in the collection editor. */
val CollectionColorChoices: List<String> = listOf(
    "#E57373", "#F06292", "#BA68C8", "#9575CD", "#7986CB",
    "#64B5F6", "#4FC3F7", "#4DB6AC", "#81C784", "#AED581",
    "#FFD54F", "#FFB74D", "#FF8A65", "#A1887F", "#90A4AE",
)

/** Parses `#RRGGBB`; malformed values fall back to a neutral grey so bad data never crashes the UI. */
fun parseHexColor(hex: String): Color {
    val clean = hex.trim().removePrefix("#")
    val rgb = clean.takeIf { it.length == 6 }?.toLongOrNull(16) ?: return Color(0xFF90A4AE)
    return Color(0xFF000000 or rgb)
}

/** Round badge with the collection's icon on its accent color. */
@Composable
fun CollectionBadge(
    iconName: String,
    colorHex: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val accent = parseHexColor(colorHex)
    val background = accent.copy(alpha = .22f).compositeOver(androidx.compose.material3.MaterialTheme.colorScheme.surface)
    val ink = com.notesapp.ui.theme.accessibleAccent(accent, background)
    Box(
        modifier = modifier
            .size(size)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CollectionIcons.get(iconName),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
