package dev.batterymirror.z70.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.batterymirror.z70.ui.theme.BatteryMirrorTheme

/**
 * 【选背景】添加小组件，或之后再改背景时打开。
 * 图片要等点完成才写进本机，返回不保存就不会盖掉原来的图。
 */
class WidgetConfigureActivity : ComponentActivity() {
    private var pendingPick: ((Uri?) -> Unit)? = null
    private val saving = AtomicBoolean(false)

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pendingPick?.invoke(uri)
        pendingPick = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = widgetId()
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_CANCELED, resultIntent(widgetId))
        val existing = WidgetStyleStore(this).read(widgetId)
        setContent {
            BatteryMirrorTheme {
                ConfigureScreen(
                    initial = existing,
                    onPickImage = { onPicked ->
                        pendingPick = onPicked
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onSave = { color, alpha, imageUri, keepOldImage ->
                        // 【保存】解码图片和刷新小组件不放在主线程，避免点完成时卡住。
                        if (saving.compareAndSet(false, true)) thread(name = "widget-save") {
                            try {
                                val store = WidgetStyleStore(this)
                                val imported = if (imageUri != null) store.importImage(widgetId, imageUri) else null
                                val path = when {
                                    imageUri != null && imported != null -> imported
                                    imageUri != null -> existing.imagePath
                                    keepOldImage -> existing.imagePath
                                    else -> {
                                        store.clearImage(widgetId)
                                        null
                                    }
                                }
                                store.save(widgetId, WidgetStyle(color, path, alpha))
                                HomeWidgetUpdater.updateAll(this, ids = intArrayOf(widgetId), force = true)
                                runOnUiThread {
                                    setResult(RESULT_OK, resultIntent(widgetId))
                                    finish()
                                }
                            } catch (_: Throwable) {
                                saving.set(false)
                            }
                        }
                    },
                )
            }
        }
    }

    private fun widgetId(): Int {
        return intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    private fun resultIntent(widgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigureScreen(
    initial: WidgetStyle,
    onPickImage: ((Uri?) -> Unit) -> Unit,
    onSave: (color: Int, alpha: Int, imageUri: Uri?, keepOldImage: Boolean) -> Unit,
) {
    var color by remember { mutableIntStateOf(initial.colorArgb) }
    var opacity by remember { mutableIntStateOf((initial.backgroundAlpha * 100 / 255).coerceIn(0, 100)) }
    var red by remember { mutableIntStateOf(android.graphics.Color.red(initial.colorArgb)) }
    var green by remember { mutableIntStateOf(android.graphics.Color.green(initial.colorArgb)) }
    var blue by remember { mutableIntStateOf(android.graphics.Color.blue(initial.colorArgb)) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var keepOldImage by remember { mutableStateOf(initial.imagePath != null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("小组件背景", style = MaterialTheme.typography.headlineLarge)
        Text(
            "放置时直接选 1×1、1×2 或 2×2。背景可以调透明度，0 是全透明。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreviewCard(color, opacity, pickedUri, if (keepOldImage) initial.imagePath else null)
        Text("纯色", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetStyle.presets.forEach { (name, value) ->
                FilterChip(
                    selected = pickedUri == null && !keepOldImage && color == value,
                    onClick = {
                        pickedUri = null
                        keepOldImage = false
                        color = value
                        red = android.graphics.Color.red(value)
                        green = android.graphics.Color.green(value)
                        blue = android.graphics.Color.blue(value)
                    },
                    label = { Text(name) },
                )
            }
        }
        ColorSlider("红", red) {
            red = it
            pickedUri = null
            keepOldImage = false
            color = android.graphics.Color.rgb(red, green, blue)
        }
        ColorSlider("绿", green) {
            green = it
            pickedUri = null
            keepOldImage = false
            color = android.graphics.Color.rgb(red, green, blue)
        }
        ColorSlider("蓝", blue) {
            blue = it
            pickedUri = null
            keepOldImage = false
            color = android.graphics.Color.rgb(red, green, blue)
        }
        ColorSlider("不透明度", opacity, 100) { opacity = it }
        Button(
            onClick = { onPickImage { uri -> if (uri != null) { pickedUri = uri; keepOldImage = false } } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (pickedUri == null && !keepOldImage) "从相册选一张图" else "换一张图") }
        if (pickedUri != null || keepOldImage) {
            TextButton(onClick = { pickedUri = null; keepOldImage = false }) { Text("去掉图片，只用纯色") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSave(color, opacity * 255 / 100, pickedUri, keepOldImage) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("完成") }
    }
}

@Composable
private fun PreviewCard(color: Int, opacity: Int, imageUri: Uri?, imagePath: String?) {
    val context = LocalContext.current
    val bitmap = remember(imageUri, imagePath) {
        when {
            imageUri != null -> context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
            imagePath != null -> BitmapFactory.decodeFile(imagePath)
            else -> null
        }
    }
    val text = if (bitmap != null) Color.White else Color(WidgetStyle.textColorFor(color))
    val fade = (opacity / 100f).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(color).copy(alpha = fade)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                "背景预览",
                Modifier.fillMaxSize().graphicsLayer { this.alpha = fade },
                contentScale = ContentScale.Crop,
            )
        }
        Text("76%", modifier = Modifier.padding(16.dp), color = text, style = MaterialTheme.typography.headlineLarge)
    }
}

@Composable
private fun ColorSlider(label: String, value: Int, max: Int = 255, onChange: (Int) -> Unit) {
    val caption = if (max == 100 && value == 0) "$label 全透明" else "$label $value"
    Text(caption, style = MaterialTheme.typography.labelLarge)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt().coerceIn(0, max)) },
        valueRange = 0f..max.toFloat(),
    )
}
