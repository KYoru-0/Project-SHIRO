package com.example.shiro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TrackDetails(
    track: Track?,
    index: Int,
    total: Int,
    loading: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    val colors = LocalPlayerColors.current
    GlassSurface(shape = RectangleShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().drawWithCache {
            val sheen = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                start = Offset.Zero, end = Offset(size.width * 0.65f, size.height))
            val rim = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.15f)))
            onDrawBehind {
                drawRect(sheen)
                drawLine(rim, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                drawLine(colors.accent.copy(alpha = 0.08f), Offset(0f, size.height),
                    Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
        }.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (track == null) "Your library" else "Now playing",
                    fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium,
                    color = colors.secondaryText, modifier = Modifier.weight(1f))
                if (track != null) Text(if (loading) "Loading…" else "Track ${index + 1} of $total",
                    fontSize = 12.sp, color = colors.secondaryText,
                    modifier = Modifier.glassPanel(colors, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(track?.title ?: "No tracks loaded", fontSize = 22.sp, lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold, color = colors.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() })
                if (track != null) FavoriteButton(isFavorite, track.title, colors.accent, onToggleFavorite)
            }
            if (track == null) {
                Spacer(Modifier.height(6.dp))
                Text("Open menu → Inputs to add music folders", fontSize = 14.sp, lineHeight = 20.sp,
                    color = colors.secondaryText)
            } else {
                if (track.artist.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(track.artist, fontSize = 14.sp, lineHeight = 20.sp, color = colors.text,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (track.composer.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text("Composed by ${track.composer}", fontSize = 12.sp, lineHeight = 18.sp,
                        color = colors.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
