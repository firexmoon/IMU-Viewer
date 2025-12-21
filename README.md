# IMU Viewer

项目后续在Gitee维护
[https://gitee.com/firexmoon/imu-viewer](https://gitee.com/firexmoon/imu-viewer)


Android手机实时三轴姿态查看器，基于惯性测量传感器，是一款能实时显示手机的姿态、加速度、陀螺仪和磁力计等传感器数据的Android APP。

## 功能特性

- **实时姿态显示**：通过加速度计、陀螺仪和磁力计数据计算并显示设备的三轴姿态（横滚角、俯仰角、偏航角）
- **多传感器监控**：
  - 姿态数据（Roll, Pitch, Yaw）
  - 加速度计数据（X, Y, Z轴）
  - 陀螺仪数据（X, Y, Z轴）
  - 磁力计数据（X, Y, Z轴）
- **图表可视化**：实时曲线图显示传感器数据变化趋势
- **高频率采样**：使用最快传感器采样率获取精确数据
- **持续监测**：支持手机屏幕常亮不锁屏

## 技术架构

### 核心组件

1. **SensorService**：后台服务负责传感器数据采集和处理
   - 使用Android SensorManager API访问硬件传感器
   - 实现SensorEventListener接口接收传感器事件
   - 采用最快的SENSOR_DELAY_FASTEST采样率

2. **AttitudeCalculator**：姿态解算器
   - 使用改进的互补滤波算法融合传感器数据
   - 结合加速度计、陀螺仪和磁力计提供准确的姿态信息
   - 输出单位为度的角度值（Roll, Pitch, Yaw）

3. **SensorDataStore**：数据存储
   - 使用Kotlin Flow进行响应式数据流管理
   - 实时更新当前传感器数据和历史数据
   - 提供数据刷新率统计

4. **UI层**：
   - 基于Jetpack Compose构建的声明式UI
   - 包含5个Tab页面：姿态、加速度、陀螺仪、磁力计、关于
   - RealtimeChart组件用于实时数据显示

## 安装要求

- Android 11 (API level 30) 或更高版本
- 需要支持加速度计、陀螺仪和磁力计的设备

## 权限说明

```xml
<uses-permission android:name="android.permission.HIGH_SAMPLING_RATE_SENSORS" />
```

该权限用于启用最高传感器采样率以获得更精确的数据。

## 开发环境

- Kotlin
- Jetpack Compose
- Android SDK 35
- Gradle 8.11.1

## 使用方法

1. 安装应用到支持所需传感器的Android设备
2. 启动应用后会自动开始传感器数据采集
3. 在顶部Tab中切换不同类型的传感器数据查看
4. 每个Tab显示当前数值和历史变化曲线图
5. 点击返回键可退出应用
