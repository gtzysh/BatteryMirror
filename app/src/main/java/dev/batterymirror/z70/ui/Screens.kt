package dev.batterymirror.z70.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.batterymirror.core.BaselineChoice
import dev.batterymirror.core.ChargeStatus
import dev.batterymirror.core.ProfileChoice
import dev.batterymirror.core.Verdict
import dev.batterymirror.z70.data.MirrorUiState
import dev.batterymirror.z70.service.MonitorService
import dev.batterymirror.z70.ui.theme.Amber
import dev.batterymirror.z70.ui.theme.Coral

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("overview", "总览", Icons.Filled.Bolt),
    Tab("usage", "用量", Icons.Filled.Timeline),
    Tab("health", "健康", Icons.Filled.HealthAndSafety),
    Tab("settings", "设置", Icons.Filled.Tune),
)

/** 【界面】总览、用量、健康、设置读同一份状态。 */
@Composable
fun MirrorApp(viewModel: BatteryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.ensureMonitor(context) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val current = nav.currentBackStackEntryAsState().value?.destination?.route
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "overview",
            modifier = Modifier.padding(padding),
        ) {
            composable("overview") { OverviewScreen(state) { nav.navigate("health") } }
            composable("usage") { UsageScreen(state) }
            composable("health") { HealthScreen(state) }
            composable("settings") { SettingsScreen(state, viewModel) }
        }
    }
}

@Composable
private fun OverviewScreen(state: MirrorUiState, onOpenHealth: () -> Unit) {
    if (state.loading && state.capturedAt == null) {
        LoadingPane()
        return
    }
    Page {
        Text("电池镜", style = MaterialTheme.typography.headlineLarge)
        Text(state.systemHeadline.ifBlank { "正在识别系统" }, style = MaterialTheme.typography.titleMedium)
        Text(state.modelLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        BatteryGauge(
            percent = state.levelPercent,
            accent = gaugeAccent(state.status, state.levelPercent),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "${state.statusLabel} · ${state.pluggedLabel}",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.titleMedium,
        )
        val clock = formatClock(state.capturedAt)
        if (clock.isNotBlank()) {
            Text(
                "$clock 更新",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(etaLine(state), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        TwoMetrics(
            left = { MetricTile("电流", formatCurrent(state.currentMa)) },
            right = { MetricTile("功率", formatWatt(state.powerMw)) },
        )
        Spacer(Modifier.height(12.dp))
        TwoMetrics(
            left = { MetricTile("电压", formatVolt(state.voltageMv)) },
            right = { MetricTile("温度", formatTemp(state.temperatureC), valueColor = tempColor(state.temperatureC)) },
        )
        Spacer(Modifier.height(12.dp))
        val assessment = state.assessment
        InstrumentCard(onClick = onOpenHealth) {
            SectionLabel("容量")
            Spacer(Modifier.height(6.dp))
            Text(assessment?.verdictTitle ?: "还在算", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                assessment?.headline ?: "正在向系统要健康度。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.notes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            NoteList(state.notes)
        }
    }
}
@Composable
private fun UsageScreen(state: MirrorUiState) {
    val context = LocalContext.current
    Page {
        Text("整机用量", style = MaterialTheme.typography.headlineLarge)
        Text(
            "统计的是这台手机整机的电流和电量变化，不是某一个应用。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(formatWatt(state.powerMw), style = MaterialTheme.typography.displayLarge)
        Text(formatCurrent(state.currentMa), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(etaLine(state), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        InstrumentCard {
            SectionLabel("近 24 小时电量")
            Spacer(Modifier.height(8.dp))
            SeriesChart(
                points = state.levelSeries,
                accent = MaterialTheme.colorScheme.primary,
                emptyText = "曲线还没形成。保持应用打开一两分钟，或在设置里打开持续记录。",
                valueRange = 0f..100f,
            )
        }
        Spacer(Modifier.height(12.dp))
        TwoMetrics(
            left = { MetricTile("今天放出", formatSignedMah(state.today.dischargedMah)) },
            right = { MetricTile("今天充入", formatSignedMah(state.today.chargedMah)) },
        )
        Spacer(Modifier.height(12.dp))
        TwoMetrics(
            left = { MetricTile("亮屏平均", state.today.screenOnAvgMa?.let { "${it.toInt()} mA" } ?: "—", "只统计采样较密的时段") },
            right = { MetricTile("息屏平均", state.today.screenOffAvgMa?.let { "${it.toInt()} mA" } ?: "—") },
        )
        Spacer(Modifier.height(12.dp))
        InstrumentCard {
            Text("今天已记录 ${formatSpan(state.today.integratedMillis)}", style = MaterialTheme.typography.titleMedium)
            state.today.netLevelDropPerHour?.let { drop ->
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        drop >= 0.3 -> "按电量计，大约 ${"%.1f".format(drop)}% 每小时下降"
                        drop <= -0.3 -> "按电量计，大约 ${"%.1f".format(-drop)}% 每小时上升"
                        else -> "这段时间电量几乎没动"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.today.usedLevelFallback) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "其中一部分是按电量百分比估算的，没有电流那么准。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.today.integratedMillis == 0L) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "今天还没有足够的采样。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "分应用耗电被系统留着自己用，普通应用拿不到。要看哪个应用耗电，打开系统电池页。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { openBatteryUsage(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("打开系统电池用量")
        }
    }
}
@Composable
private fun HealthScreen(state: MirrorUiState) {
    val assessment = state.assessment
    Page {
        Text("容量健康", style = MaterialTheme.typography.headlineLarge)
        Text(
            state.modelLine.ifBlank { "等待识别型号" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            assessment?.verdictTitle ?: "还判断不了有没有缩减",
            style = MaterialTheme.typography.headlineMedium,
            color = verdictColor(assessment?.verdict),
        )
        Text(formatPercent(assessment?.retentionPercent), style = MaterialTheme.typography.displayLarge)
        Text("容量保持率 · ${assessment?.gradeLabel ?: "暂无法判断"}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(assessment?.headline ?: "正在读系统健康度。", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        val designMah = assessment?.designMah
        if (assessment != null && designMah != null) {
            InstrumentCard {
                CapacityBars(designMah, assessment.fullMah)
                Spacer(Modifier.height(12.dp))
                Text(
                    "比较基准：${formatMah(assessment.designMah)} · ${assessment.designSourceLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "满充来源：${assessment.fullSourceLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        } else if (assessment?.fullMah != null) {
            InstrumentCard {
                Text(
                    "现在的满充容量：${formatMah(assessment.fullMah)} · ${assessment.fullSourceLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "电量计没有给出额定容量，还没法算缩了多少。可以在设置里手填标称毫安时。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        TwoMetrics(
            left = {
                MetricTile(
                    "系统健康度",
                    formatPercent(assessment?.reportedSohPercent),
                    assessment?.reportedSohLabel ?: "星云还没给出",
                )
            },
            right = {
                MetricTile(
                    "循环次数",
                    assessment?.cycleCount?.toString() ?: "—",
                    "系统没给就留空",
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        InstrumentCard {
            Text("置信度 ${assessment?.confidenceLabel ?: "无"}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                assessment?.detail ?: "还没有结论。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "系统标志「${state.healthFlagLabel.ifBlank { "未知" }}」只说明有没有过热、过压，不代表容量没缩。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        InstrumentCard {
            SectionLabel("满充容量趋势")
            Spacer(Modifier.height(8.dp))
            SeriesChart(
                points = state.capacitySeries,
                accent = MaterialTheme.colorScheme.tertiary,
                emptyText = "读到满充容量后，会按天记下来，方便以后看有没有继续缩。",
                axisPattern = "MM-dd",
            )
        }
        state.technology?.let {
            Spacer(Modifier.height(12.dp))
            Text("电池技术：$it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(state: MirrorUiState, viewModel: BatteryViewModel) {
    val context = LocalContext.current
    var customText by remember { mutableStateOf("") }
    var seeded by remember { mutableStateOf(false) }
    var customError by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(state.settings.customDesignMah) {
        if (!seeded) {
            customText = state.settings.customDesignMah?.toString().orEmpty()
            seeded = true
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.setMonitor(true) { MonitorService.start(context.applicationContext) }
        } else {
            viewModel.setMonitor(false) { MonitorService.stop(context.applicationContext) }
        }
    }
    Page {
        HomeWidgetSection()
        Spacer(Modifier.height(16.dp))
        Text("这台手机", style = MaterialTheme.typography.headlineLarge)
        InstrumentCard {
            Text(state.systemHeadline.ifBlank { "还没读到系统版本" }, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(state.modelLine, style = MaterialTheme.typography.bodyLarge)
            if (state.sdkInt > 0) {
                Text("API ${state.sdkInt}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.displayLine.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(state.displayLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "任何手机都先读电量计里的设计容量和满充容量，不认机型也能比较。只有电量计没给数时，才用努比亚的典型值：Z70 Ultra 6150mAh，Z70S Ultra 6600mAh。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("机型")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("自动识别", state.settings.profileChoice == ProfileChoice.AUTO) { viewModel.setProfile(ProfileChoice.AUTO) }
            ChoiceChip("Z70 Ultra 6150", state.settings.profileChoice == ProfileChoice.Z70_ULTRA) { viewModel.setProfile(ProfileChoice.Z70_ULTRA) }
            ChoiceChip("Z70S Ultra 6600", state.settings.profileChoice == ProfileChoice.Z70S_ULTRA) { viewModel.setProfile(ProfileChoice.Z70S_ULTRA) }
            ChoiceChip("不指定机型", state.settings.profileChoice == ProfileChoice.GENERIC) { viewModel.setProfile(ProfileChoice.GENERIC) }
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("比较基准")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("自动", state.settings.baseline == BaselineChoice.AUTO) { viewModel.setBaseline(BaselineChoice.AUTO) }
            ChoiceChip("官网典型值", state.settings.baseline == BaselineChoice.PROFILE) { viewModel.setBaseline(BaselineChoice.PROFILE) }
            ChoiceChip("手填", state.settings.baseline == BaselineChoice.CUSTOM) { saveCustom(customText, viewModel) { customError = it } }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "当前基准：${state.assessment?.designSourceLabel ?: "还没有"} · ${formatMah(state.assessment?.designMah)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = customText,
            onValueChange = {
                customText = it.filter { ch -> ch.isDigit() }.take(5)
                customError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("手填容量 mAh") },
            supportingText = { Text(customError ?: "1500 到 12000，只在选择手填时使用") },
            isError = customError != null,
            singleLine = true,
        )
        TextButton(onClick = { saveCustom(customText, viewModel) { customError = it } }) { Text("保存手填值") }
        Spacer(Modifier.height(8.dp))
        InstrumentCard {
            Text("持续记录", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "打开后会留一条低优先级通知，大约每 5 秒记一次。不持有唤醒锁，息屏后间隔可能变长。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(if (state.settings.monitorEnabled) "已打开" else "只在应用打开时采样")
                Switch(
                    checked = state.settings.monitorEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            viewModel.setMonitor(false) { MonitorService.stop(context.applicationContext) }
                        } else if (MonitorService.notificationsAllowed(context)) {
                            viewModel.setMonitor(true) { MonitorService.start(context.applicationContext) }
                        } else {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
            if (state.settings.monitorEnabled && !MonitorService.notificationsAllowed(context)) {
                Spacer(Modifier.height(6.dp))
                Text("通知权限关了，后台记录不会启动。", color = MaterialTheme.colorScheme.error)
            }
            if (state.settings.monitorEnabled && !state.ignoringOptimizations) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { requestIgnoreOptimizations(context) }) { Text("请求忽略电池优化") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("已载入 ${state.sampleCount} 条近期采样。样本在手机上保留 7 天。", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        if (!confirmClear) {
            TextButton(onClick = { confirmClear = true }) { Text("清空记录") }
        } else {
            Text("会删掉曲线，以及已经记下的充满容量。比较基准和机型选择还留着。")
            TextButton(onClick = {
                viewModel.clearRecords()
                confirmClear = false
            }) { Text("确认清空") }
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("系统读数")
        Spacer(Modifier.height(8.dp))
        if (state.diagnostics.isEmpty()) {
            Text("还没有读到节点。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.diagnostics.forEach { line ->
                Text("${line.label}：${line.value}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                copyDiagnostics(context, state)
                copied = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (copied) "已复制" else "复制这些读数") }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
}

private fun saveCustom(text: String, viewModel: BatteryViewModel, onError: (String?) -> Unit) {
    val value = text.toIntOrNull()
    if (value == null || value !in 1_500..12_000) {
        onError("请填 1500 到 12000 之间的毫安时")
        return
    }
    onError(null)
    viewModel.setCustomMah(value)
    viewModel.setBaseline(BaselineChoice.CUSTOM)
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), content = content)
    }
}

private fun etaLine(state: MirrorUiState): String = when (state.status) {
    ChargeStatus.CHARGING -> "充满大约还要 ${formatDurationMinutes(state.chargeEtaMinutes)}"
    ChargeStatus.FULL -> "已经充满"
    ChargeStatus.DISCHARGING, ChargeStatus.NOT_CHARGING -> "按最近的放电速度，大约还能用 ${formatDurationMinutes(state.dischargeEtaMinutes)}"
    ChargeStatus.UNKNOWN -> "还在等电流稳定下来"
}

private fun gaugeAccent(status: ChargeStatus, level: Int?): Color = when {
    status == ChargeStatus.CHARGING || status == ChargeStatus.FULL -> Color(0xFF7DFFC3)
    (level ?: 100) <= 15 -> Coral
    (level ?: 100) <= 30 -> Amber
    else -> Color(0xFFD6F25C)
}

private fun tempColor(celsius: Double?): Color = when {
    celsius == null -> Color.Unspecified
    celsius >= 45.0 -> Coral
    celsius >= 40.0 -> Amber
    else -> Color.Unspecified
}

private fun verdictColor(verdict: Verdict?): Color = when (verdict) {
    Verdict.NO_SHRINK -> Color(0xFF7DFFC3)
    Verdict.SLIGHT_SHRINK -> Amber
    Verdict.SHRUNK -> Coral
    else -> Color.Unspecified
}

private fun openBatteryUsage(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
        Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Throwable) {
            // 【入口】星云可能藏起其中某一个设置页，换下一个。
        }
    }
}

private fun requestIgnoreOptimizations(context: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(direct)
    } catch (_: Throwable) {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun copyDiagnostics(context: Context, state: MirrorUiState) {
    val text = state.diagnostics.joinToString("\n") { "${it.label}\t${it.value}" }
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("电池镜诊断", text))
}
