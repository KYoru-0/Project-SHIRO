package com.example.shiro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun VisualizerModeControl(mode: VisualizerMode, layout: VisualizerLayout,
    onSelected: (VisualizerMode, VisualizerLayout) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var draftMode by remember { mutableStateOf(mode) }
    var draftLayout by remember { mutableStateOf(layout) }
    val colors = LocalPlayerColors.current
    GlassTextButton(onClick = { draftMode = mode; draftLayout = layout; open = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text("Visualizer", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.text)
            Text("${mode.title} · ${layout.title}", fontSize = 12.sp, color = colors.secondaryText)
        }
    }
    if (open) GlassAlertDialog(
        onDismissRequest = { open = false }, title = { Text("Visualizer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mode", fontWeight = FontWeight.SemiBold)
                VisualizerMode.available.forEach { choice ->
                    GlassTextButton(onClick = { draftMode = choice },
                        modifier = Modifier.fillMaxWidth().semantics { selected = draftMode == choice }
                            .glassPanel(colors, selected = draftMode == choice), contentPadding = PaddingValues(16.dp)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${choice.title}${if (draftMode == choice) "  ✓" else ""}",
                                fontWeight = FontWeight.SemiBold, color = colors.text)
                            Text(choice.description, fontSize = 13.sp, color = colors.secondaryText)
                        }
                    }
                }
                Text("Layout", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VisualizerLayout.entries.forEach { choice ->
                        GlassTextButton(onClick = { draftLayout = choice },
                            modifier = Modifier.weight(1f).semantics { selected = draftLayout == choice }
                                .glassPanel(colors, selected = draftLayout == choice)) {
                            Text("${choice.title}${if (draftLayout == choice) "  ✓" else ""}")
                        }
                    }
                }
            }
        },
        confirmButton = { GlassTextButton(onClick = { open = false; onSelected(draftMode, draftLayout) }) { Text("Apply") } },
        dismissButton = { GlassTextButton(onClick = { open = false }) { Text("Cancel") } }
    )
}
