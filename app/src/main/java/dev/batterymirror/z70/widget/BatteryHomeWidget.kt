package dev.batterymirror.z70.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import dev.batterymirror.z70.data.MirrorUiState
import kotlin.concurrent.thread

/**
 * 【桌面小组件】系统大约每 30 分钟会叫一次。
 * 应用开着或持续记录开着时，仓库会更勤地推一次最新电量。
 */
open class BatteryHomeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        thread(name = "battery-widget") {
            try {
                HomeWidgetUpdater.updateAll(context.applicationContext, ids = ids, force = true)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle,
    ) {
        val pending = goAsync()
        thread(name = "battery-widget") {
            try {
                HomeWidgetUpdater.updateAll(context.applicationContext, ids = intArrayOf(id), force = true)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val store = WidgetStyleStore(context)
        ids.forEach { store.delete(it) }
    }
}

object HomeWidgetUpdater {
    private var lastPushedAt = 0L

    fun updateAll(
        context: Context,
        state: MirrorUiState? = null,
        ids: IntArray? = null,
        force: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPushedAt < 20_000L) return
        lastPushedAt = now
        val manager = AppWidgetManager.getInstance(context)
        val widgetIds = ids ?: widgetProviders.flatMap { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider)).toList()
        }.toIntArray()
        if (widgetIds.isEmpty()) return
        val reading = if (state?.capturedAt != null) {
            readingFromState(state)
        } else {
            readingFromProbe(context)
        }
        val styles = WidgetStyleStore(context)
        widgetIds.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val views = bindWidget(context, id, options, styles.read(id), reading)
            manager.updateAppWidget(id, views)
        }
    }

    private val widgetProviders = listOf(
        BatteryWidgetOneByOne::class.java,
        BatteryWidgetOneByTwo::class.java,
        BatteryWidgetTwoByOne::class.java,
        BatteryHomeWidget::class.java,
    )
}

/** 【1×1】放置列表里单独一项，不再靠放上去再缩小。 */
class BatteryWidgetOneByOne : BatteryHomeWidget()

/** 【1×2】竖条，高是宽的两倍。 */
class BatteryWidgetOneByTwo : BatteryHomeWidget()

/** 【2×1】横条，宽是高的两倍。 */
class BatteryWidgetTwoByOne : BatteryHomeWidget()
