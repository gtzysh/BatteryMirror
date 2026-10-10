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
) {
    fun textColor(): Int = if (imagePath != null) LIGHT_TEXT else textColorFor(colorArgb)

    fun mutedTextColor(): Int {
        val base = textColor()
        return Color.argb(180, Color.red(base), Color.green(base), Color.blue(base))
    }

    companion object {
        const val DEFAULT_COLOR = 0xFF151A18.toInt()
        const val LIGHT_TEXT = 0xFFF3F6F1.toInt()
        const val DARK_TEXT = 0xFF14211B.toInt()

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
