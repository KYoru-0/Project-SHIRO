package com.example.shiro

import android.content.Context
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

fun loadFavoriteIds(context: Context): Set<String> = context
    .getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE)
    .getStringSet("favorite_track_uris", emptySet()).orEmpty().toSet()

fun saveFavoriteIds(context: Context, ids: Set<String>) {
    context.getSharedPreferences("shiro_prefs", Context.MODE_PRIVATE).edit()
        .putStringSet("favorite_track_uris", ids.toSet()).apply()
}

@Composable
fun FavoriteButton(isFavorite: Boolean, title: String, tint: Color? = null, onToggle: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    IconToggleButton(checked = isFavorite, onCheckedChange = { onToggle() }, interactionSource = interactions,
        modifier = Modifier.glassPressFeedback(interactions, shape = CircleShape)
            .glassPanel(LocalPlayerColors.current, CircleShape, selected = isFavorite)) {
        Icon(
            painter = painterResource(if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline),
            contentDescription = if (isFavorite) "Remove $title from favorites" else "Add $title to favorites",
            tint = tint ?: LocalPlayerColors.current.accent
        )
    }
}
