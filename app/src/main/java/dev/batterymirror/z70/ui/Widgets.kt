package dev.batterymirror.z70.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.batterymirror.z70.data.ChartPoint

/** 【仪表】电量环。没有读数时只画空环。 */
@Composable
internal fun BatteryGauge(percent: Int?, accent: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            val track = Color(0x338A938C)
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = stroke,
            )
            val fraction = ((percent ?: 0).coerceIn(0, 100)) / 100f
            if (fraction > 0f) {
                drawArc(
                    color = accent,
                    startAngle = 135f,
                    sweepAngle = 270f * fraction,
                    useCenter = false,
                    style = stroke,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = percent?.toString() ?: "—",
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
            )
            Text("%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TwoMetrics(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    }
}

@Composable
internal fun MetricTile(
    title: String,
    value: String,
    caption: String? = null,
    valueColor: Color = Color.Unspecified,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
        if (!caption.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun InstrumentCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    var modifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (onClick != null) modifier = modifier.clickable(onClick = onClick)
    Column(modifier.padding(16.dp), content = content)
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun NoteList(notes: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        notes.forEach { note ->
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LoadingPane() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("正在读取这台手机的电池", style = MaterialTheme.typography.titleMedium)
    }
}

/** 【容量条】上面是比较基准，下面是现在的满充容量。高于基准时第二条顶满。 */
@Composable
internal fun CapacityBars(designMah: Double, fullMah: Double?) {
    val fraction = if (fullMah == null || designMah <= 0.0) 0f else (fullMah / designMah).toFloat().coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        BarLine("比较基准", 1f, MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
        BarLine("现在充满", fraction, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun BarLine(label: String, fraction: Float, color: Color) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val radius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        drawRoundRect(color = Color(0x338A938C), cornerRadius = radius)
        drawRoundRect(
            color = color,
            size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
            cornerRadius = radius,
        )
    }
}

/**
 * 【曲线】电量用固定 0 到 100，容量趋势按数据自己缩放。
 * 点少于两个时只显示说明，不画一条没有时间跨度的线。
 */
@Composable
internal fun SeriesChart(
    points: List<ChartPoint>,
    accent: Color,
    emptyText: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float>? = null,
    axisPattern: String = "HH:mm",
) {
    if (points.size < 2) {
        Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val minValue = valueRange?.start ?: points.minOf { it.value }
    val maxValue = valueRange?.endInclusive ?: points.maxOf { it.value }
    val span = (maxValue - minValue).let { if (it < 0.5f) 1f else it }
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) 0f else size.width * index / (points.size - 1)
                val y = size.height - ((point.value - minValue) / span).coerceIn(0f, 1f) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            val last = points.last()
            val lastX = size.width
            val lastY = size.height - ((last.value - minValue) / span).coerceIn(0f, 1f) * size.height
            drawCircle(color = accent, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(points.first().epochMillis, axisPattern), style = MaterialTheme.typography.labelMedium)
            Text(formatClock(points.last().epochMillis, axisPattern), style = MaterialTheme.typography.labelMedium)
        }
    }
}
