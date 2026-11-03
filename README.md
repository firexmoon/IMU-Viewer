# 高斯计 Gaussmeter

一款专注磁场测量的 Android 应用，基于手机内置的磁力传感器读取三轴磁场强度（μT），用于定位磁铁、校验磁屏蔽效果、监测磁场变化等场景。本项目在 [IMU Viewer](https://github.com/firexmoon/IMU-Viewer) 基础上修改而成，仅保留并深度完善磁力计相关功能。

> 高斯计是测量磁场强度的仪器，名字源于磁感应强度单位高斯（Gs，1 Gs = 100 μT）。手机传感器非专业设备，读数仅作参考。

## 功能特性

- **实时三轴读数**：X / Y / Z 轴磁场（μT）、三轴合成强度、刷新率
- **可调曲线窗口**：磁场变化曲线窗口 5–60 秒无级调节
- **霍尔元件位置标记**：全屏悬浮方框，手动校准到手机霍尔元件实际位置并持久保存
  - 「开始校准」后自动跳转读数页，方框变橙可拖动
  - 双击方框完成校准，自动锁回灰色，不拦截点击
- **基线告警**：以当前读数为基线，任一轴偏离超过阈值即蜂鸣告警
  - 阈值 1–100 μT 可调，默认 10 μT
  - 3 秒冷却期，必须回到阈值内才重新武装，避免持续响
  - 告警记录单独一栏，记录触发时间、burst 期内最大偏差、持续时长，可清空
- **CSV 记录与导出**：
  - 采样率 1–100 Hz 可调，默认 10 Hz
  - 列：时间戳（毫秒）、ISO 时间、Bx / By / Bz、合成强度
  - 通过 Storage Access Framework 导出到任意位置，无需额外权限
  - 单次记录上限 50 万行，达到后自动停止
- **使用引导**：首次启动分步引导（判断静止、定位霍尔、设基线、开始记录），每一步自动跳转到对应 Tab，可在「关于」页重开
- **屏幕常亮**：测量期间不自动锁屏

## 应用结构

5 个 Tab：

| Tab | 内容 |
|---|---|
| 读数 | X/Y/Z 轴数值、合成强度、刷新率、磁场变化曲线（可调时间窗口） |
| 基线 | 设基线/清基线、阈值滑块、当前状态、告警历史列表 |
| 记录 | 采样率滑块、开始/停止/清空/导出 CSV、行数状态 |
| 标记 | 开始校准、显示/隐藏方框、复位到屏幕中央 |
| 关于 | 版本号、使用指引入口、用法说明、开源信息 |

## 技术架构

- **Jetpack Compose + Material 3** 构建的声明式 UI
- **SensorService** 持有 `MagneticBaselineMonitor` / `CsvRecorder` / `StillnessDetector`，以 `SENSOR_DELAY_FASTEST` 注册磁力计
- **SensorDataStore** 以 Kotlin Flow 对外暴露各通道状态
- **ToneGenerator** 发声，走 `STREAM_ALARM`，无需任何权限
- 关键配置（霍尔位置、告警阈值、采样率、曲线窗口、引导状态）持久化到 SharedPreferences
- 核心判定逻辑（基线告警、CSV 采样）为纯 Kotlin，附带 JUnit 单测

## 安装要求

- Android 11（API 30）或更高
- 具备磁力传感器的设备

## 构建

```bash
# Debug
./gradlew :app:assembleDebug

# Release（未签名）
./gradlew :app:assembleRelease
```

需要 JDK 21 和 Android SDK 36 / Build-Tools 36.0.0。

签名发布 APK 的最简路径：先生成 keystore（`keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias gaussmeter`），再在 `app/build.gradle.kts` 中为 `release` buildType 配置 `signingConfig`，重新执行 `assembleRelease` 即可。

## 使用方法

1. 把手机放平静止，等「读数」页数值稳定
2. 「标记」Tab 点开始校准，用磁铁沿屏幕扫找读数最大点，拖方框到该点，双击方框锁定
3. 「基线」Tab 点设基线并调阈值，之后磁场偏离超过阈值会响一下
4. 「记录」Tab 选采样率、开始/停止记录、导出 CSV

## 开源信息

- 本修改版 Copyright © 2026 悠悠，基于 MIT License 分发
- 基于 [IMU Viewer](https://github.com/firexmoon/IMU-Viewer)（Copyright © 2025 李勃，MIT License）修改
- 原项目保留的 `LICENSE` 文件适用于本项目

## Changelog

- v1.0 首个 Release，功能见上
