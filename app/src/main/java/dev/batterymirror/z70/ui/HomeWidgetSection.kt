package dev.batterymirror.z70.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.batterymirror.z70.widget.BatteryHomeWidget
import dev.batterymirror.z70.widget.WidgetConfigureActivity

/** 【设置入口】从应用里把小组件钉到桌面，或改已经放上去的背景。 */
@Composable
fun HomeWidgetSection() {
    val context = LocalContext.current
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, BatteryHomeWidget::class.java))
    InstrumentCard {
        SectionLabel("桌面小组件")
        Spacer(Modifier.height(6.dp))
        Text(
            "有 1×1、1×2、2×2 三种大小。添加时可以选纯色，也可以从相册挑一张图当背景。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (manager.isRequestPinAppWidgetSupported) {
            Button(
                onClick = {
                    val provider = ComponentName(context, BatteryHomeWidget::class.java)
                    manager.requestPinAppWidget(provider, null, null)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("添加到桌面") }
        } else {
            Text(
                "这个桌面不能由应用直接添加。请在桌面长按，选择小组件，找到电池镜。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ids.forEach { id ->
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(context, WidgetConfigureActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                    )
                },
            ) { Text("改第 ${id} 个小组件的背景") }
        }
    }
}
