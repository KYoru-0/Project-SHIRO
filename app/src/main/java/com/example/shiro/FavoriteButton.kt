package com.example.shiro

import android.content.Context
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

fun loadFavoriteIds(context: Context): Set<String> = context
    .getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE)
    .getStringSet("favorite_track_uris", emptySet()).orEmpty().toSet()

fun saveFavoriteIds(context: Context, ids: Set<String>) {
    val prefs = context.getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE)
    prefs.edit()
        .remove("favorite_track_uris")
        .apply()
    prefs.edit()
        .putStringSet("favorite_track_uris", HashSet(ids))
        .apply()
}

@Composable
fun FavoriteButton(isFavorite: Boolean, title: String, onToggle: () -> Unit) {
    IconToggleButton(checked = isFavorite, onCheckedChange = { onToggle() }) {
        Icon(
            painter = painterResource(if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline),
            contentDescription = if (isFavorite) "Remove $title from favorites" else "Add $title to favorites",
            tint = if (isFavorite) Color(0xFFC24169) else AccentColor
        )
    }
}
