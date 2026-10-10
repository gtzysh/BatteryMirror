package dev.batterymirror.z70.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import dev.batterymirror.z70.widget.BatteryWidgetOneByOne
import dev.batterymirror.z70.widget.BatteryWidgetOneByTwo
import dev.batterymirror.z70.widget.BatteryWidgetTwoByOne
import dev.batterymirror.z70.widget.WidgetConfigureActivity

/** 【设置入口】三种尺寸分开添加，桌面列表里也能直接选。 */
@Composable
fun HomeWidgetSection() {
    val context = LocalContext.current
    val manager = AppWidgetManager.getInstance(context)
    val providers = listOf(
        BatteryWidgetOneByOne::class.java to "添加 1×1",
        BatteryWidgetOneByTwo::class.java to "添加 1×2",
        BatteryWidgetTwoByOne::class.java to "添加 2×1",
        BatteryHomeWidget::class.java to "添加 2×2",
    )
    val ids = providers.flatMap { (type, _) ->
        manager.getAppWidgetIds(ComponentName(context, type)).toList()
    }
    InstrumentCard {
        SectionLabel("桌面小组件")
        Spacer(Modifier.height(6.dp))
        Text(
            "窗口小工具里有四项：电量 1×1、1×2、2×1、2×2。2×1 是横条，宽是高的两倍。背景可以调成透明。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (manager.isRequestPinAppWidgetSupported) {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                providers.forEach { (type, label) ->
                    Button(
                        onClick = {
                            manager.requestPinAppWidget(ComponentName(context, type), null, null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label) }
                }
            }
        } else {
            Text(
                "这个桌面不能由应用直接添加。请长按桌面，在窗口小工具里选对应尺寸。",
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
            ) { Text("改小组件 $id 的背景") }
        }
    }
}
