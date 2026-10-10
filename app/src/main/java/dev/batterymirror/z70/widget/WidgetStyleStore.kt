package dev.batterymirror.z70.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 【小组件配置】背景色和图片只放在本机，按小组件编号分开存。
 * 删掉桌面上的小组件时，把对应图片一起删掉。
 */
class WidgetStyleStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val imageDir = File(appContext.filesDir, "widget-bg")

    fun read(widgetId: Int): WidgetStyle {
        val color = prefs.getInt(colorKey(widgetId), WidgetStyle.DEFAULT_COLOR)
        val path = prefs.getString(imageKey(widgetId), null)?.takeIf { File(it).isFile }
        val alpha = prefs.getInt(alphaKey(widgetId), 255).coerceIn(0, 255)
        return WidgetStyle(color, path, alpha)
    }

    fun save(widgetId: Int, style: WidgetStyle) {
        prefs.edit()
            .putInt(colorKey(widgetId), style.colorArgb)
            .putInt(alphaKey(widgetId), style.backgroundAlpha.coerceIn(0, 255))
            .putString(imageKey(widgetId), style.imagePath)
            .apply()
    }

    /** 相册图片先缩小再保存，避免小组件绑定过大的位图。 */
    fun importImage(widgetId: Int, uri: Uri): String? {
        val decoded = appContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null
        val scaled = scaleDown(decoded, MAX_EDGE)
        if (scaled !== decoded) decoded.recycle()
        imageDir.mkdirs()
        val file = File(imageDir, "$widgetId.jpg")
        val temp = File(imageDir, "$widgetId.tmp.jpg")
        FileOutputStream(temp).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (scaled !== decoded) scaled.recycle()
        if (file.exists() && !file.delete()) return null
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        return file.absolutePath
    }

    fun clearImage(widgetId: Int) {
        val path = prefs.getString(imageKey(widgetId), null)
        if (path != null) File(path).delete()
        prefs.edit().remove(imageKey(widgetId)).apply()
    }

    fun delete(widgetId: Int) {
        clearImage(widgetId)
        prefs.edit().remove(colorKey(widgetId)).remove(alphaKey(widgetId)).apply()
    }

    private fun scaleDown(source: Bitmap, maxEdge: Int): Bitmap {
        val edge = max(source.width, source.height)
        if (edge <= maxEdge) return source
        val ratio = maxEdge.toFloat() / edge
        val width = (source.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (source.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun colorKey(widgetId: Int) = "color_$widgetId"

    private fun imageKey(widgetId: Int) = "image_$widgetId"

    private fun alphaKey(widgetId: Int) = "alpha_$widgetId"

    companion object {
        private const val PREFS = "battery_widget_style"
        private const val MAX_EDGE = 720
    }
}
