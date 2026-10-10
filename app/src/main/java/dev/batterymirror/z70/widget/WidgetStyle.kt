package dev.batterymirror.z70.widget

import android.graphics.Color
import kotlin.math.pow

/**
 * 【小组件外观】每个桌面小组件单独记一份背景。
 * 有图片时图片盖住纯色；没有图片就用纯色。
 */
data class WidgetStyle(
    val colorArgb: Int = DEFAULT_COLOR,
    val imagePath: String? = null,
    /** 0 为全透明，255 为不透明。透明时字用浅色，避免贴在壁纸上看不见。 */
    val backgroundAlpha: Int = 255,
) {
    fun textColor(): Int {
        if (imagePath != null || backgroundAlpha < 200) return LIGHT_TEXT
        return textColorFor(colorArgb)
    }

    /** 【图例灰色】四种尺寸共用：状态、电压、温度。浅色背景改成深灰。 */
    fun mutedTextColor(): Int = if (textColor() == LIGHT_TEXT) MUTED_ON_DARK else MUTED_ON_LIGHT

    /** 【图例黄绿】四种尺寸的健康度。浅色背景改成深绿，避免和浅底糊在一起。 */
    fun accentColor(): Int = if (textColor() == LIGHT_TEXT) ACCENT_ON_DARK else ACCENT_ON_LIGHT

    companion object {
        const val DEFAULT_COLOR = 0xFF151A18.toInt()
        const val LIGHT_TEXT = 0xFFF3F6F1.toInt()
        const val DARK_TEXT = 0xFF14211B.toInt()
        /** 和 widget_*.xml、预览图里的 #A7B2AB、#D6F25C 相同。 */
        const val MUTED_ON_DARK = 0xFFA7B2AB.toInt()
        const val MUTED_ON_LIGHT = 0xFF5C675F.toInt()
        const val ACCENT_ON_DARK = 0xFFD6F25C.toInt()
        const val ACCENT_ON_LIGHT = 0xFF1F6A32.toInt()

        val presets: List<Pair<String, Int>> = listOf(
            "墨黑" to DEFAULT_COLOR,
            "夜色" to 0xFF0C0F0E.toInt(),
            "暖纸" to 0xFFF4F1E8.toInt(),
            "青柠" to 0xFFD6F25C.toInt(),
            "深林" to 0xFF1F6A32.toInt(),
            "琥珀" to 0xFF8A5A12.toInt(),
        )

        /** 背景偏亮时用深色字，避免青柠、暖纸上看不清。 */
        fun textColorFor(colorArgb: Int): Int {
            val r = Color.red(colorArgb) / 255.0
            val g = Color.green(colorArgb) / 255.0
            val b = Color.blue(colorArgb) / 255.0
            val luminance = 0.2126 * r.pow(2.2) + 0.7152 * g.pow(2.2) + 0.0722 * b.pow(2.2)
            return if (luminance > 0.45) DARK_TEXT else LIGHT_TEXT
        }
    }
}
