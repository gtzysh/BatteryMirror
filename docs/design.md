# 电池镜设计

## 目标系统

国行努比亚 Z70 Ultra，官方系统，不是第三方包。

| 项目 | 值 |
| --- | --- |
| 型号 | NX733J |
| 系统 | 星云 AIOS 2.0.13 |
| 版本号 | NebulaAIOS2.0.13MR6_NX733J |
| Android | 16 |
| 处理器 | 骁龙 8 至尊版 |
| 屏幕 | 2688×1216 |
| 官网典型容量 | 6150mAh，80W 有线 |

卫星版代号常见 NX736J，容量同样按 6150mAh。Z70S Ultra 常见 NX737J，典型容量 6600mAh。只看到 NX733J、又没有星云 AIOS 字样时，不直接当成 6150，避免把国际版 Z70S 算错。

应用不读取、不保存 IMEI。

## 为什么不能做分应用耗电

`BATTERY_STATS` 是系统签名权限。星云 AIOS 不会把它授给普通应用，所以这里只做整机：电流积分、剩余容量差值和电量百分比估算。分应用排行交给系统电池页。

## 容量有没有缩减

保持率 = 现在充满能装的容量 / 比较基准。

自动识别不依赖努比亚机型表。任何 Android 手机都先读电量计：

1. 设计容量：`charge_full_design`、`design_capacity`、`fg_fullcapnom`，或把 `energy_full_design` 按 3.85V 换成毫安时
2. 现在的满充容量：`charge_full`，或 `energy_full`
3. 电量计没写设计容量、但有满充容量和健康度时，用满充容量除以健康度反推额定容量
4. 以上都没有，才看是不是 Z70 / Z70S 的官网典型值，再没有就用手填

比较基准按设置选择：

- 自动：优先电量计里的设计容量，没有再用机型典型值，再没有就用手填值
- 官网典型值：Z70 Ultra 6150，Z70S Ultra 6600
- 手填：1500 到 12000 mAh

满充容量按这个顺序取，取到就停：

1. 节点 `charge_full`（微安时；小于 10 万的数当成毫安时）
2. Android 16 `BatteryManager` 属性 10，即 state of health。它的定义就是满充容量相对额定容量的百分比。星云若没开放，调用会失败，不当成 0%
3. 节点里的 `soh` / `battery_soh`
4. 以前充满到 100% 且电流很小的时候，用剩余容量反推并记下的中位数
5. 此刻电量不低于 99% 且已经充满或电流很小，临时反推

结论：

- 算不出来：还判断不了
- 少了不到 2%，或保持率不低于 98%：几乎没有缩减
- 保持率不低于 90%：轻微缩减
- 再低：容量已经缩减

2% 这条是为了避开典型值和电量计口径的正常差距。读数高于基准时，不当成容量变多。

Android 16 的健康度用属性编号 10 去问，这样用 SDK 35 也能编译，装到 Android 16 上会走到系统实现。系统「电池健康：良好」只表示没有过热或过压，不参与缩水判断。

## 用电量

采样在应用打开时大约 5 秒进内存，30 秒写一次文件，保留 7 天。

- 间隔不超过 3 分钟且两端都有电流：用电流积分。正电流算充入，负电流算放出
- 间隔更长：用剩余容量的差值，这是电量计自己累计的
- 还没有剩余容量：用电量变化乘以满充容量
- 超过 6 小时的空洞丢掉，避免把瞬时电流当成一直不变
- 亮屏、息屏平均只统计间隔短、屏幕状态没变的放电段

预计可用时间用最近 15 分钟的放电电流，至少要有 2 分钟有效采样。

电流方向以 Android 约定为准：正为充电，负为放电。和充放电状态明显矛盾时才翻转。

## 持续记录

默认只在应用打开时采样。打开「持续记录」后用前台服务，类型是 `specialUse`，通知栏留一条低优先级通知。开机广播只有在这个开关开着、并且通知权限还在时才恢复。不持有唤醒锁，息屏后系统仍可能把采样间隔拉长。

## 实现口径

这些数是为了把上面的规则写成可测的代码：

- 电流很小：绝对值小于 50mA。快充时不把剩余容量记成满充容量。
- 学会的满充容量：12 小时内只记一条，最多留 30 条，取中位数。满 3 条置信度升到中。
- 采样：界面约 2 秒刷新当前值；满 5 秒才记一条；满 30 秒写入文件。
- 样本保留 7 天。容量趋势按天保留 180 天，同一天不拿更差的来源覆盖电量计或系统健康度。
- 亮屏、息屏平均只统计不超过 3 分钟、屏幕状态没变、而且用了真实电流的放电段。

## 桌面小组件

一块小组件可以在桌面上拉成三种大小：

- 1×1：电量和充放电状态
- 1×2：再加上电流。拉宽成横条时，改成电量旁边排状态、电流和功率
- 2×2：再加上功率、电压和容量结论

添加时先选背景。背景可以是纯色（预设或红绿蓝自选），也可以从相册选一张图。图片会缩小后只存在本机。每个小组件的背景互不影响。应用打开或持续记录开着时，大约 20 秒推一次新电量；只放在桌面上时，系统大约 30 分钟刷新一次。

## 代码落点

- `core/src/main/kotlin/dev/batterymirror/core/CapacityMath.kt`：缩水判断
- `core/src/main/kotlin/dev/batterymirror/core/UsageMath.kt`：毫安时积分
- `core/src/main/kotlin/dev/batterymirror/core/DeviceProfiles.kt`：Z70 / Z70S 典型容量
- `core/src/main/kotlin/dev/batterymirror/core/SystemBuildParser.kt`：从版本号抽出星云 AIOS 版本
- `app/src/main/java/dev/batterymirror/z70/probe/Probes.kt`：BatteryManager 和 `/sys/class/power_supply`
- `app/src/main/java/dev/batterymirror/z70/data/BatteryRepository.kt`：把采样收成四个页面共用的状态
- `app/src/main/java/dev/batterymirror/z70/data/Stores.kt`：本机设置和 7 天记录
- `app/src/main/java/dev/batterymirror/z70/service/MonitorService.kt`：持续记录的前台服务
- `app/src/main/java/dev/batterymirror/z70/ui/Screens.kt`：总览、用量、健康、设置
- `app/src/main/java/dev/batterymirror/z70/ui/Widgets.kt`：电量环、指标卡和曲线
- `app/src/main/java/dev/batterymirror/z70/widget/BatteryHomeWidget.kt`：桌面小组件
- `app/src/main/java/dev/batterymirror/z70/widget/WidgetConfigureActivity.kt`：小组件背景
