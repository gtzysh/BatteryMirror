package dev.batterymirror.z70.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.widget.RemoteViews
import android.os.Build
import dev.batterymirror.core.CapacityInput
import dev.batterymirror.core.CapacityMath
import dev.batterymirror.core.DeviceProfiles
import dev.batterymirror.z70.MainActivity
import dev.batterymirror.z70.R
import dev.batterymirror.z70.data.MirrorUiState
import dev.batterymirror.z70.data.SettingsStore
import dev.batterymirror.z70.probe.BatteryProbe
import dev.batterymirror.z70.probe.ProbeSnapshot
import kotlinx.coroutines.runBlocking
import dev.batterymirror.z70.ui.formatCurrent
import dev.batterymirror.z70.ui.formatVolt
import dev.batterymirror.z70.ui.formatWatt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** 【小组件读数】桌面只显示此刻的电量，不在这里重算一整天的曲线。 */
internal data class WidgetReading(
    val levelText: String,
    val statusText: String,
    val currentText: String,
    val powerText: String,
    val voltageText: String,
    val verdictText: String,
)

internal fun readingFromState(state: MirrorUiState): WidgetReading {
    return WidgetReading(
        levelText = state.levelPercent?.let { "$it%" } ?: "—",
        statusText = state.statusLabel.ifBlank { "正在读取" },
        currentText = shortCurrent(state.currentMa),
        powerText = formatWatt(state.powerMw),
        voltageText = formatVolt(state.voltageMv),
        verdictText = state.assessment?.verdictTitle ?: "容量还在算",
    )
}

internal fun readingFromProbe(context: Context): WidgetReading {
    val snapshot = BatteryProbe(context).read()
    val currentMa = snapshot.currentMicroamps?.div(1000.0)
    val powerMw = if (currentMa != null && snapshot.voltageMv != null) {
        abs(currentMa) * snapshot.voltageMv / 1000.0
    } else {
        null
    }
    return WidgetReading(
        levelText = snapshot.levelPercent?.let { "$it%" } ?: "—",
        statusText = snapshot.statusLabel,
        currentText = shortCurrent(currentMa),
        powerText = formatWatt(powerMw),
        voltageText = formatVolt(snapshot.voltageMv),
        verdictText = verdictTitle(context, snapshot),
    )
}

private fun verdictTitle(context: Context, snapshot: ProbeSnapshot): String {
    val settings = runBlocking { SettingsStore(context).current() }
    val identity = listOf(
        Build.MANUFACTURER,
        Build.MODEL,
        Build.DISPLAY,
        Build.VERSION.INCREMENTAL,
        Build.VERSION.RELEASE,
    ).joinToString(" ")
    val profile = DeviceProfiles.resolve(settings.profileChoice, identity)
    return CapacityMath.assess(
        CapacityInput(
            gaugeFullUah = snapshot.gaugeFullUah,
            gaugeDesignUah = snapshot.gaugeDesignUah,
            gaugeSohPercent = snapshot.gaugeSohPercent,
            frameworkSohPercent = snapshot.frameworkSohPercent,
            remainingUah = snapshot.remainingUah,
            levelPercent = snapshot.levelPercent,
            status = snapshot.status,
            currentMicroamps = snapshot.currentMicroamps,
            cycleCount = snapshot.cycleCount,
            profileTypicalMah = profile?.typicalMah,
            profileAmbiguous = profile?.ambiguous == true,
            baseline = settings.baseline,
            customDesignMah = settings.customDesignMah,
            learnedFullUah = settings.learnedFullUah,
        ),
    ).verdictTitle
}

internal fun bindWidget(
    context: Context,
    widgetId: Int,
    options: Bundle,
    style: WidgetStyle,
    reading: WidgetReading,
): RemoteViews {
    val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
    val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
    val layout = layoutForCells(widthDp, heightDp)
    val views = RemoteViews(context.packageName, layout)
    val density = context.resources.displayMetrics.density
    val widthPx = (widthDp * density).roundToInt().coerceIn(64, 900)
    val heightPx = (heightDp * density).roundToInt().coerceIn(64, 900)
    views.setImageViewBitmap(R.id.widget_bg, backgroundBitmap(style, widthPx, heightPx, density))
    val text = style.textColor()
    val muted = style.mutedTextColor()
    views.setTextViewText(R.id.widget_level, reading.levelText)
    views.setTextColor(R.id.widget_level, text)
    views.setTextViewText(R.id.widget_status, reading.statusText)
    views.setTextColor(R.id.widget_status, muted)
    if (layout != R.layout.widget_1x1) {
        views.setTextViewText(R.id.widget_current, reading.currentText)
        views.setTextColor(R.id.widget_current, text)
    }
    if (layout == R.layout.widget_2x1 || layout == R.layout.widget_2x2) {
        views.setTextViewText(R.id.widget_power, reading.powerText)
        views.setTextColor(R.id.widget_power, text)
    }
    if (layout == R.layout.widget_2x2) {
        views.setTextViewText(R.id.widget_voltage, reading.voltageText)
        views.setTextColor(R.id.widget_voltage, muted)
        views.setTextViewText(R.id.widget_verdict, reading.verdictText)
        views.setTextColor(R.id.widget_verdict, text)
    }
    val open = PendingIntent.getActivity(
        context,
        widgetId,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    views.setOnClickPendingIntent(R.id.widget_root, open)
    return views
}

/** 格子按 Android 的 70dp 一格估算。1×2 是竖条，拉宽则用横条。 */
internal fun layoutForCells(widthDp: Int, heightDp: Int): Int {
    val columns = ((widthDp + 30) / 70).coerceAtLeast(1)
    val rows = ((heightDp + 30) / 70).coerceAtLeast(1)
    return when {
        columns >= 2 && rows >= 2 -> R.layout.widget_2x2
        rows >= 2 -> R.layout.widget_1x2
        columns >= 2 -> R.layout.widget_2x1
        else -> R.layout.widget_1x1
    }
}

private fun shortCurrent(currentMa: Double?): String {
    val full = formatCurrent(currentMa)
    return when {
        full.startsWith("充电") -> "充 " + full.removePrefix("充电 ").trim()
        full.startsWith("放电") -> "放 " + full.removePrefix("放电 ").trim()
        else -> full
    }
}

private fun backgroundBitmap(style: WidgetStyle, width: Int, height: Int, density: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    val radius = 20f * density
    val photo = style.imagePath?.let { path ->
        BitmapFactory.decodeFile(path)
    }
    if (photo != null) {
        val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = max(width.toFloat() / photo.width, height.toFloat() / photo.height)
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate((width - photo.width * scale) / 2f, (height - photo.height * scale) / 2f)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
        paint.color = 0x66000000
        canvas.drawRoundRect(rect, radius, radius, paint)
    } else {
        paint.color = style.colorArgb
        canvas.drawRoundRect(rect, radius, radius, paint)
    }
    return bitmap
}
