package com.neeraj.fin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class DonutSlice(val label: String, val value: Long, val color: Color)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerTop: String,
    centerBottom: String,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val stroke = Stroke(width = 34f)
            val inset = 20f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            var startAngle = -90f
            if (slices.isEmpty()) {
                drawArc(Color.Gray.copy(alpha = 0.2f), 0f, 360f, false, topLeft, arcSize, style = stroke)
            } else {
                slices.forEach { slice ->
                    val sweep = 360f * slice.value / total
                    drawArc(slice.color, startAngle, (sweep - 1.5f).coerceAtLeast(0.5f), false, topLeft, arcSize, style = stroke)
                    startAngle += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(centerBottom, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class BarEntry(val label: String, val value: Long, val secondary: Long? = null)

/**
 * Simple vertical bar chart. When [secondary] values are present, bars are drawn
 * as grouped pairs (e.g. expense vs income, or current vs previous period).
 */
@Composable
fun BarChart(
    entries: List<BarEntry>,
    primaryColor: Color,
    secondaryColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier,
    valueFormatter: (Long) -> String = { it.toString() }
) {
    val maxValue = entries.maxOfOrNull { maxOf(it.value, it.secondary ?: 0) }?.coerceAtLeast(1) ?: 1
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            entries.forEach { entry ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f)
                ) {
                    if (entry.value > 0 || (entry.secondary ?: 0) > 0) {
                        Text(
                            valueFormatter(entry.value),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    fun barHeight(v: Long) = (110 * v / maxValue).toInt().coerceAtLeast(if (v > 0) 3 else 1).dp
                    val barWidth = if (entry.secondary != null) 12.dp else 18.dp
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Canvas(Modifier.size(width = barWidth, height = barHeight(entry.value))) {
                            drawRoundRect(primaryColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                        }
                        if (entry.secondary != null) {
                            Canvas(Modifier.size(width = barWidth, height = barHeight(entry.secondary))) {
                                drawRoundRect(secondaryColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                            }
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            entries.forEach {
                Text(
                    it.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Horizontal progress bar used for budgets and category breakdowns. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier.height(8.dp).fillMaxWidth()) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(
            color,
            size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
            cornerRadius = r
        )
    }
}
