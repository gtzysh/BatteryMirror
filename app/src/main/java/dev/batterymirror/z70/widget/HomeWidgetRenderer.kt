package dev.batterymirror.z70.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
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
import dev.batterymirror.z70.ui.formatTemp
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
    val tempText: String,
    val healthText: String,
    val verdictText: String,
)

internal fun readingFromState(state: MirrorUiState): WidgetReading {
    return WidgetReading(
        levelText = state.levelPercent?.let { "$it%" } ?: "—",
        statusText = state.statusLabel.ifBlank { "正在读取" },
        currentText = shortCurrent(state.currentMa),
        powerText = formatWatt(state.powerMw),
        voltageText = formatVolt(state.voltageMv),
        tempText = formatTemp(state.temperatureC),
        healthText = healthLabel(state.assessment?.reportedSohPercent, state.assessment?.retentionPercent),
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
        tempText = formatTemp(snapshot.temperatureC),
        healthText = "健康 —",
        verdictText = "容量还在算",
    ).let { base ->
        val judged = judgeCapacity(context, snapshot)
        base.copy(
            healthText = healthLabel(judged.reportedSoh, judged.retention),
            verdictText = judged.verdict,
        )
    }
}

/** 系统没报健康度时，用算出来的保持率，避免小组件一直显示「健康 —」。 */
private fun healthLabel(soh: Double?, retention: Double?): String {
    val percent = soh ?: retention ?: return "健康 —"
    return "健康 ${percent.roundToInt()}%"
}

private data class JudgedCapacity(val verdict: String, val reportedSoh: Double?, val retention: Double?)

private fun judgeCapacity(context: Context, snapshot: ProbeSnapshot): JudgedCapacity {
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
    ).let { JudgedCapacity(it.verdictTitle, it.reportedSohPercent, it.retentionPercent) }
}

internal fun bindWidget(
    context: Context,
    widgetId: Int,
    options: Bundle,
    style: WidgetStyle,
    reading: WidgetReading,
): RemoteViews {
    val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)?.provider?.className
    val (defaultW, defaultH) = standardDpFor(provider)
    val (widthDp, heightDp) = placedSizeDp(context, options, defaultW, defaultH)
    // 【按图例排版】每种尺寸固定自己的内容。按上报宽高换布局时，2×2 会被收成 2×1 或 1×2。
    val layout = layoutForProvider(provider)
    val views = RemoteViews(context.packageName, layout)
    val density = context.resources.displayMetrics.density
    val widthPx = (widthDp * density).roundToInt().coerceIn(48, 1200)
    val heightPx = (heightDp * density).roundToInt().coerceIn(48, 1200)
    views.setInt(R.id.widget_root, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
    views.setImageViewBitmap(R.id.widget_bg, backgroundBitmap(style, widthPx, heightPx))
    val text = style.textColor()
    val muted = style.mutedTextColor()
    views.setTextViewText(R.id.widget_level, reading.levelText)
    views.setTextColor(R.id.widget_level, text)
    views.setTextViewText(R.id.widget_status, reading.statusText)
    views.setTextColor(R.id.widget_status, muted)
    setText(views, R.id.widget_health, reading.healthText, style.accentColor())
    if (layout != R.layout.widget_1x1) {
        setText(views, R.id.widget_current, reading.currentText, text)
    }
    if (layout == R.layout.widget_2x1 || layout == R.layout.widget_2x2) {
        setText(views, R.id.widget_power, reading.powerText, text)
    }
    if (layout == R.layout.widget_1x2 || layout == R.layout.widget_2x2) {
        setText(views, R.id.widget_verdict, reading.verdictText, text)
    }
    if (layout == R.layout.widget_2x2) {
        setText(views, R.id.widget_voltage, reading.voltageText, muted)
        setText(views, R.id.widget_temp, reading.tempText, muted)
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

private fun standardDpFor(provider: String?): Pair<Int, Int> {
    return when (provider) {
        BatteryWidgetOneByOne::class.java.name -> 40 to 40
        BatteryWidgetOneByTwo::class.java.name -> 40 to 110
        BatteryWidgetTwoByOne::class.java.name -> 110 to 40
        else -> 110 to 110
    }
}

/** 每种尺寸对应自己的图例，不跟另外三种共用同一套字段。 */
internal fun layoutForProvider(provider: String?): Int {
    return when (provider) {
        BatteryWidgetOneByOne::class.java.name -> R.layout.widget_1x1
        BatteryWidgetOneByTwo::class.java.name -> R.layout.widget_1x2
        BatteryWidgetTwoByOne::class.java.name -> R.layout.widget_2x1
        else -> R.layout.widget_2x2
    }
}

/**
 * 竖屏当前高度在 maxHeight，不在 minHeight。
 * Android 12 起的尺寸列表里，第一项是竖屏。
 */
internal fun placedSizeDp(context: Context, options: Bundle, defaultW: Int, defaultH: Int): Pair<Int, Int> {
    val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    if (Build.VERSION.SDK_INT >= 31) {
        @Suppress("DEPRECATION")
        val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        if (!sizes.isNullOrEmpty()) {
            val picked = if (!portrait && sizes.size > 1) sizes[1] else sizes[0]
            val width = picked.width.roundToInt()
            val height = picked.height.roundToInt()
            if (width > 0 && height > 0) return width to height
        }
    }
    val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
    val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
    val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
    val width = (if (portrait) firstPositive(minW, maxW) else firstPositive(maxW, minW)) ?: defaultW
    val height = (if (portrait) firstPositive(maxH, minH) else firstPositive(minH, maxH)) ?: defaultH
    return width to height
}

private fun firstPositive(primary: Int, secondary: Int): Int? {
    return primary.takeIf { it > 0 } ?: secondary.takeIf { it > 0 }
}

private fun setText(views: RemoteViews, id: Int, value: String, color: Int) {
    views.setTextViewText(id, value)
    views.setTextColor(id, color)
}

private fun shortCurrent(currentMa: Double?): String {
    val full = formatCurrent(currentMa)
    return when {
        full.startsWith("充电") -> "充 " + full.removePrefix("充电 ").trim()
        full.startsWith("放电") -> "放 " + full.removePrefix("放电 ").trim()
        else -> full
    }
}

/**
 * 背景铺满整个格子，不再自己切圆角。
 * 星云会把小组件四角裁成圆角，再切一次就会露出被裁过的缺口。
 */
private fun backgroundBitmap(style: WidgetStyle, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val photo = style.imagePath?.let { path ->
        BitmapFactory.decodeFile(path)
    }
    val alpha = style.backgroundAlpha.coerceIn(0, 255)
    if (photo != null && alpha > 0) {
        val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = max(width.toFloat() / photo.width, height.toFloat() / photo.height)
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate((width - photo.width * scale) / 2f, (height - photo.height * scale) / 2f)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.alpha = alpha
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.color = android.graphics.Color.argb(0x66 * alpha / 255, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    } else if (alpha > 0) {
        paint.color = android.graphics.Color.argb(
            alpha,
            android.graphics.Color.red(style.colorArgb),
            android.graphics.Color.green(style.colorArgb),
            android.graphics.Color.blue(style.colorArgb),
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
    return bitmap
}
