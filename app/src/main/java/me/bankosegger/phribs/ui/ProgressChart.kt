package me.bankosegger.phribs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class ChartPoint(val x: Long, val y: Double)

// Round axis steps (1, 2, 2.5, 5 × 10ⁿ) so tick labels are clean numbers.
internal fun niceTicks(min: Double, max: Double, target: Int = 4): List<Double> {
    var lo = min
    var hi = max
    if (hi - lo < 1e-9) { lo -= 1.0; hi += 1.0 }
    val raw = (hi - lo) / (target - 1)
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
    val start = floor(lo / step) * step
    val end = ceil(hi / step) * step
    return generateSequence(start) { it + step }.takeWhile { it <= end + step / 2 }.toList()
}

@Composable
fun ProgressChart(
    points: List<ChartPoint>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    formatY: (Double) -> String,
    formatX: (Long) -> String,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val ticks = niceTicks(points.minOf { it.y }, points.maxOf { it.y })
    val minX = points.first().x
    val spanX = (points.last().x - minX).coerceAtLeast(1L)

    val yLabels = ticks.map { measurer.measure(formatY(it), labelStyle) }
    val gutter = with(LocalDensity.current) { 8.dp.toPx() }
    val left = yLabels.maxOf { it.size.width } + gutter

    // Shared by drawing and hit-testing so taps land on the same x the dots are drawn at.
    fun xFor(index: Int, width: Float): Float =
        if (points.size == 1) left + width / 2 else left + (points[index].x - minX).toFloat() / spanX * width

    Canvas(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(points, left) {
                fun nearest(x: Float) = points.indices.minBy { abs(xFor(it, size.width - left - gutter) - x) }
                coroutineScope {
                    launch { detectTapGestures { onSelect(nearest(it.x)) } }
                    launch { detectHorizontalDragGestures { change, _ -> onSelect(nearest(change.position.x)) } }
                }
            }
    ) {
        val right = size.width - gutter
        val xLabelHeight = measurer.measure("0", labelStyle).size.height
        val top = 8.dp.toPx()
        val bottom = size.height - xLabelHeight - 6.dp.toPx()
        val width = right - left
        val lo = ticks.first()
        val hi = ticks.last()
        fun yFor(v: Double) = bottom - ((v - lo) / (hi - lo)).toFloat() * (bottom - top)

        ticks.forEachIndexed { i, t ->
            val y = yFor(t)
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            val label = yLabels[i]
            drawText(label, topLeft = Offset(left - 6.dp.toPx() - label.size.width, y - label.size.height / 2))
        }

        val offsets = points.indices.map { Offset(xFor(it, width), yFor(points[it].y)) }
        if (offsets.size > 1) {
            val line = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(offsets.last().x, bottom)
                lineTo(offsets.first().x, bottom)
                close()
            }
            drawPath(area, lineColor.copy(alpha = 0.1f))
            drawPath(line, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        selected?.let { i ->
            val p = offsets[i]
            drawLine(gridColor, Offset(p.x, top), Offset(p.x, bottom), strokeWidth = 1.dp.toPx())
        }
        // Only mark individual sessions when they're far enough apart to read as dots.
        val showDots = offsets.size <= 30
        offsets.forEachIndexed { i, p ->
            if (showDots || i == selected) {
                val r = if (i == selected) 6.dp.toPx() else 4.dp.toPx()
                drawCircle(surface, r + 2.dp.toPx(), p)
                drawCircle(lineColor, r, p)
            }
        }

        val first = measurer.measure(formatX(points.first().x), labelStyle)
        drawText(first, topLeft = Offset(left, bottom + 6.dp.toPx()))
        if (points.size > 1) {
            val last = measurer.measure(formatX(points.last().x), labelStyle)
            drawText(last, topLeft = Offset(right - last.size.width, bottom + 6.dp.toPx()))
        }
    }
}
