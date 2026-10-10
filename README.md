# 电池镜

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Version](https://img.shields.io/badge/version-1.2.10-blue.svg)](app/build.gradle.kts)

当前版本 **1.2.10**。四种小组件的文字颜色都和图例一致，2×2 固定显示八项内容。

给 Android 手机看整机电量，并判断电池满充容量有没有比出厂基准缩水。不是厂商官方应用，数据只留在手机上。Android 10 及以上可以安装。

打开后会读这台手机自己的型号和系统版本。努比亚国行 Z70 Ultra（**NX733J**，星云 AIOS 2.0.13，版本号 `NebulaAIOS2.0.13MR6_NX733J`）对得上时，会显示「星云 AIOS 2.0.13 · Android 16」和「NX733J · 努比亚 Z70 Ultra」。别的机型照样显示自己的型号，容量优先用电量计报的数。

## 能看什么

- 电量、充放电状态、电流、功率、电压、温度
- 今天放出和充入了多少毫安时，近 24 小时电量曲线
- 亮屏和息屏的平均放电电流
- 容量有没有缩减：保持率、少了多少毫安时、循环次数
- 设置里可以改比较基准，也能把系统实际读到的节点复制出来
- 桌面小组件支持 1×1、1×2、2×1、2×2，背景可以换成纯色或一张图片

分应用耗电系统不给普通应用。要看哪个应用费电，用量页里有「打开系统电池用量」。

## 容量怎么比

任何手机都先读电量计自己报的设计容量和满充容量。读不到时，才用机型典型值。国行 NX733J 的官网典型容量是 **6150mAh**（Z70 Ultra，80W）。Z70S Ultra 是 **6600mAh**。比较顺序：

1. 电量计直接报的满充容量 `charge_full`
2. Android 16 的系统健康度（满充容量相对额定容量的百分比）
3. 充满到 100% 之后记下的剩余容量
4. 按当前电量反推，这个最粗

和比较基准差在 2% 以内，先看成几乎没有缩减。官网典型值只是电量计没给数时的后备，新电池也不保证刚好等于标称容量。

## 在电脑上编译

需要 JDK 21 和 Android SDK 35。

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :core:test
./gradlew :app:assembleDebug
```

安装包在 `app/build/outputs/apk/debug/app-debug.apk`。手机打开开发者选项和 USB 调试后：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

系统若拦截未知来源，在设置里允许这次安装。装好后看总览顶部是不是这台手机的型号和系统版本。若容量分母不对，到设置里手填标称毫安时；努比亚 Z70 Ultra 可填 6150，Z70S Ultra 可填 6600。

算法说明在 `docs/design.md`。

## 许可证

本项目以 [MIT 许可证](LICENSE) 开源。
