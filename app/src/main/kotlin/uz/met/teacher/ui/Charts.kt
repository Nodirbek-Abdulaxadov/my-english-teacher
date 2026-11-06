package uz.met.teacher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A chart point. [label] is the point's name in words, shown with its value when it is tapped.
 * Each chart holds one measure in one color, so its title is all the legend it needs.
 */
data class ChartPoint(val label: String, val value: Float)

private fun format(value: Float, unit: String): String =
    (if (value >= 10f || value == value.toInt().toFloat()) value.roundToInt().toString() else "%.1f".format(value)) + " " + unit

/** The value line above a chart: the latest point, or the one the learner tapped. */
@Composable
private fun ChartHeader(title: String, point: ChartPoint?, unit: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (point != null) {
            Text("${point.label}: ${format(point.value, unit)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun describe(title: String, points: List<ChartPoint>, unit: String) =
    "$title. " + points.joinToString("; ") { "${it.label}: ${format(it.value, unit)}" }

@Composable
fun BarChart(title: String, points: List<ChartPoint>, unit: String) {
    val mark = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val peak = max(points.maxOfOrNull { it.value } ?: 0f, 1f)
    MetCard {
        ChartHeader(title, points.getOrNull(selected), unit)
        Canvas(
            Modifier.fillMaxWidth().height(96.dp)
                .semantics { contentDescription = describe(title, points, unit) }
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        if (points.isNotEmpty()) selected = (tap.x / size.width * points.size).toInt().coerceIn(points.indices)
                    }
                },
        ) {
            if (points.isEmpty()) return@Canvas
            val slot = size.width / points.size
            val gap = 2.dp.toPx()
            val barWidth = max(slot - gap, 1f)
            val radius = minOf(4.dp.toPx(), barWidth / 2)
            points.forEachIndexed { index, point ->
                val height = point.value / peak * (size.height - 2.dp.toPx())
                if (height > 0f) {
                    drawRoundRect(
                        color = if (index == selected) mark else mark.copy(alpha = 0.55f),
                        topLeft = Offset(index * slot + gap / 2, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
            }
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
    }
}

@Composable
fun LineChart(title: String, points: List<ChartPoint>, unit: String) {
    val mark = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val grid = MaterialTheme.colorScheme.outline
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    MetCard {
        ChartHeader(title, points.getOrNull(selected), unit)
        if (points.size < 2) {
            Text("Grafik uchun kamida 2 ta sessiya kerak.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@MetCard
        }
        val low = points.minOf { it.value }
        val high = points.maxOf { it.value }
        Canvas(
            Modifier.fillMaxWidth().height(96.dp)
                .semantics { contentDescription = describe(title, points, unit) }
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        selected = (tap.x / size.width * (points.size - 1) + 0.5f).toInt().coerceIn(points.indices)
                    }
                },
        ) {
            val inset = 6.dp.toPx()
            val range = high - low
            fun at(index: Int) = Offset(
                inset + index * (size.width - 2 * inset) / (points.size - 1),
                // With nothing to compare, the line sits mid-chart rather than looking like a floor.
                if (range == 0f) size.height / 2 else size.height - inset - (points[index].value - low) / range * (size.height - 2 * inset),
            )
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            val path = Path().apply {
                moveTo(at(0).x, at(0).y)
                for (i in 1..points.lastIndex) lineTo(at(i).x, at(i).y)
            }
            drawPath(path, mark, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(surface, radius = 6.dp.toPx(), center = at(selected))
            drawCircle(mark, radius = 4.dp.toPx(), center = at(selected))
        }
        Text(
            "Eng past ${format(low, unit)} · eng yuqori ${format(high, unit)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Five weeks of practice, one square a day, oldest first. Monday starts each row. */
@Composable
fun StreakCalendar(goalDays: Set<LocalDate>, practiceDays: Set<LocalDate>, today: LocalDate = LocalDate.now()) {
    val colors = MaterialTheme.colorScheme
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val firstDay = weekStart.minusWeeks(4)
    MetCard {
        Text("So'nggi 5 hafta", style = MaterialTheme.typography.titleSmall)
        for (week in 0 until 5) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                for (dayIndex in 0 until 7) {
                    val day = firstDay.plusDays((week * 7 + dayIndex).toLong())
                    val met = day in goalDays
                    val practiced = day in practiceDays
                    val fill = when {
                        day.isAfter(today) -> Color.Transparent
                        met -> colors.primary
                        practiced -> colors.primary.copy(alpha = 0.35f)
                        else -> colors.surfaceVariant
                    }
                    Box(
                        Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(5.dp)).background(fill)
                            .then(if (day == today) Modifier.border(2.dp, Ember, RoundedCornerShape(5.dp)) else Modifier),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendDot(colors.primary)
            Text("Maqsad bajarildi", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(6.dp))
            LegendDot(colors.primary.copy(alpha = 0.35f))
            Text("Mashq qilindi", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}
