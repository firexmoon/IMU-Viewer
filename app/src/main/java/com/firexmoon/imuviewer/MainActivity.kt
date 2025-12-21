package com.firexmoon.imuviewer

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.firexmoon.imuviewer.data.AttitudeData
import com.firexmoon.imuviewer.data.SensorData
import com.firexmoon.imuviewer.service.SensorService
import com.firexmoon.imuviewer.ui.components.RealtimeChart
import com.firexmoon.imuviewer.ui.theme.IMUViewerTheme
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    private var _sensorService: SensorService? = null
    private var isBound = false
    private lateinit var sensorServiceIntent: Intent
    
    // 使用mutableStateOf来跟踪服务实例，确保Compose能感知到服务绑定状态变化
    private val sensorServiceState = mutableStateOf<SensorService?>(null)
    
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SensorService.LocalBinder
            _sensorService = binder.getService()
            isBound = true
            // 更新Compose状态，触发UI重组
            sensorServiceState.value = _sensorService
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            _sensorService = null
            isBound = false
            // 更新Compose状态，触发UI重组
            sensorServiceState.value = null
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用edge-to-edge显示
        enableEdgeToEdge()
        
        // 启动传感器服务
        sensorServiceIntent = Intent(this, SensorService::class.java)
        startService(sensorServiceIntent)
        
        // 绑定传感器服务
        bindService(sensorServiceIntent, connection, BIND_AUTO_CREATE)
        
        setContent {
            IMUViewerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 使用Compose状态传递服务实例
                    SensorDataApp(sensorServiceState.value)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
    
    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // 创建退出提示对话框
        AlertDialog.Builder(this)
            .setTitle("退出提示")
            .setMessage("确定要退出应用吗？")
            .setPositiveButton("确定") { _, _ ->
                // 停止传感器服务
                stopService(sensorServiceIntent)
                // 结束当前Activity
                finish()
                // 结束应用进程
                exitProcess(0)
            }
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show()
    }
}

@Composable
fun SensorDataApp(sensorService: SensorService?) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("姿态", "加速度", "陀螺仪", "磁力计", "关于")
    
    // 使用rememberUpdatedState确保获取最新的服务实例
    val currentService by rememberUpdatedState(sensorService)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp) // 使用固定的顶部内边距，避免API兼容性问题
    ) {
        // TabRow
        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = Color.Black,
            contentColor = Color.White
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(title) }
                )
            }
        }
        
        // 内容区域
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            when (tabIndex) {
                0 -> AttitudeTab(currentService)
                1 -> AccelerometerTab(currentService)
                2 -> GyroscopeTab(currentService)
                3 -> MagnetometerTab(currentService)
                4 -> AboutTab()
            }
        }
    }
}

// 设备姿态Tab
@Composable
fun AttitudeTab(sensorService: SensorService?) {
    val attitudeDataStore = sensorService?.getSensorDataStore()
    val coroutineScope = rememberCoroutineScope()
    
    // 使用remember保存状态，通过LaunchedEffect更新
    var attitudeData by remember { mutableStateOf(emptyList<AttitudeData>()) }
    var currentAttitude by remember { mutableStateOf(AttitudeData(0, 0f, 0f, 0f)) }
    var attitudeHz by remember { mutableFloatStateOf(0f) }
    
    // 监听数据变化
    LaunchedEffect(attitudeDataStore) {
        if (attitudeDataStore != null) {
            coroutineScope.launch {
                attitudeDataStore.attitudeData.collect {
                    attitudeData = it
                }
            }
            coroutineScope.launch {
                attitudeDataStore.currentAttitude.collect {
                    currentAttitude = it
                }
            }
            coroutineScope.launch {
                attitudeDataStore.attitudeHz.collect {
                    attitudeHz = it
                }
            }
        }
    }
    
    // 将AttitudeData转换为SensorData，以便使用RealtimeChart组件
    val attitudeDataAsSensorData = remember(attitudeData) {
        attitudeData.map { 
            SensorData(
                timestamp = it.timestamp,
                x = it.roll,  // Roll作为X轴
                y = it.pitch, // Pitch作为Y轴
                z = it.yaw    // Yaw作为Z轴
            )
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "当前姿态数据",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        
        // 当前数据显示 - 表格布局
        Column {
            Row {
                Text(text = "横滚角 角度", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = currentAttitude.roll.format(1), color = Color.White, modifier = Modifier.width(120.dp))
            }
            Row {
                Text(text = "俯仰角 角度", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = currentAttitude.pitch.format(1), color = Color.White, modifier = Modifier.width(120.dp))
            }
            Row {
                Text(text = "偏航角 角度", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = currentAttitude.yaw.format(1), color = Color.White, modifier = Modifier.width(120.dp))
            }
            Row {
                Text(text = "刷新率", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = "${attitudeHz.toInt()} Hz", color = Color.White, modifier = Modifier.width(120.dp))
            }
        }
        
        // 姿态曲线图 - 使用RealtimeChart替代AttitudeChart，消除组件冗余
        Text(
            text = "姿态变化曲线",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        RealtimeChart(data = attitudeDataAsSensorData)
    }
}

// 加速度计Tab
@Composable
fun AccelerometerTab(sensorService: SensorService?) {
    val dataStore = sensorService?.getSensorDataStore()
    val coroutineScope = rememberCoroutineScope()
    
    var accelerometerData by remember { mutableStateOf(emptyList<SensorData>()) }
    var currentAccel by remember { mutableStateOf(SensorData(0, 0f, 0f, 0f)) }
    var accelerometerHz by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(dataStore) {
        if (dataStore != null) {
            coroutineScope.launch {
                dataStore.accelerometerData.collect {
                    accelerometerData = it
                }
            }
            coroutineScope.launch {
                dataStore.currentAccelerometer.collect {
                    currentAccel = it
                }
            }
            coroutineScope.launch {
                dataStore.accelerometerHz.collect {
                    accelerometerHz = it
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "当前加速度数据",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        
        // 当前数据显示 - 表格布局
        Column {
            Row {
                Text(text = "X轴 m/s²", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = currentAccel.x.format(1), color = Color.White, modifier = Modifier.width(120.dp))
            }
            Row {
                Text(text = "Y轴 m/s²", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = currentAccel.y.format(1), color = Color.White, modifier = Modifier.width(120.dp))
            }
            Row {
                Text(text = "Z轴 m/s²", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = currentAccel.z.format(1), color = Color.White, modifier = Modifier.width(120.dp))
            }
            Row {
                Text(text = "刷新率", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = "${accelerometerHz.toInt()} Hz", color = Color.White, modifier = Modifier.width(120.dp))
            }
        }
        
        // 加速度曲线图
        Text(
            text = "加速度变化曲线",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        RealtimeChart(data = accelerometerData)
    }
}

// 陀螺仪Tab
@Composable
fun GyroscopeTab(sensorService: SensorService?) {
    val dataStore = sensorService?.getSensorDataStore()
    val coroutineScope = rememberCoroutineScope()
    
    var gyroscopeData by remember { mutableStateOf(emptyList<SensorData>()) }
    var currentGyro by remember { mutableStateOf(SensorData(0, 0f, 0f, 0f)) }
    var gyroscopeHz by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(dataStore) {
        if (dataStore != null) {
            coroutineScope.launch {
                dataStore.gyroscopeData.collect {
                    gyroscopeData = it
                }
            }
            coroutineScope.launch {
                dataStore.currentGyroscope.collect {
                    currentGyro = it
                }
            }
            coroutineScope.launch {
                dataStore.gyroscopeHz.collect {
                    gyroscopeHz = it
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "当前陀螺仪数据",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        
        // 当前数据显示 - 表格布局
        Column {
            Row {
                Text(text = "X轴 rad/s", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = currentGyro.x.format(1), color = Color.White, modifier = Modifier.width(120.dp))
            }
            Row {
                Text(text = "Y轴 rad/s", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = currentGyro.y.format(1), color = Color.White, modifier = Modifier.width(120.dp))
            }
            Row {
                Text(text = "Z轴 rad/s", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = currentGyro.z.format(1), color = Color.White, modifier = Modifier.width(120.dp))
            }
            Row {
                Text(text = "刷新率", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = "${gyroscopeHz.toInt()} Hz", color = Color.White, modifier = Modifier.width(120.dp))
            }
        }
        
        // 陀螺仪曲线图
        Text(
            text = "陀螺仪变化曲线",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        RealtimeChart(data = gyroscopeData)
    }
}

// 磁力计Tab
@Composable
fun MagnetometerTab(sensorService: SensorService?) {
    val dataStore = sensorService?.getSensorDataStore()
    val coroutineScope = rememberCoroutineScope()
    
    var magnetometerData by remember { mutableStateOf(emptyList<SensorData>()) }
    var currentMag by remember { mutableStateOf(SensorData(0, 0f, 0f, 0f)) }
    var magnetometerHz by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(dataStore) {
        if (dataStore != null) {
            coroutineScope.launch {
                dataStore.magnetometerData.collect {
                    magnetometerData = it
                }
            }
            coroutineScope.launch {
                dataStore.currentMagnetometer.collect {
                    currentMag = it
                }
            }
            coroutineScope.launch {
                dataStore.magnetometerHz.collect {
                    magnetometerHz = it
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "当前磁力计数据",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        
        // 当前数据显示 - 表格布局
        Column {
            Row {
                Text(text = "X轴 μT", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = currentMag.x.format(1), color = Color.White, modifier = Modifier.width(120.dp))
            }
            Row {
                Text(text = "Y轴 μT", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = currentMag.y.format(1), color = Color.White, modifier = Modifier.width(120.dp))
            }
            Row {
                Text(text = "Z轴 μT", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = currentMag.z.format(1), color = Color.White, modifier = Modifier.width(120.dp))
            }
            Row {
                Text(text = "刷新率", color = Color.White, modifier = Modifier.width(120.dp))
                Text(text = "${magnetometerHz.toInt()} Hz", color = Color.White, modifier = Modifier.width(120.dp))
            }
        }
        
        // 磁力计曲线图
        Text(
            text = "磁力计变化曲线",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        RealtimeChart(data = magnetometerData)
    }
}

// 扩展函数：格式化浮点数
fun Float.format(digits: Int): String {
    // 处理-0.0的情况，将其显示为+0.0
    val value = if (this == -0.0f) 0.0f else this
    return "%+.${digits}f".format(value)
}

/**
 * 获取应用程序信息
 */
fun ComponentActivity.getAppInfo(): Pair<String, String> {
    return try {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName ?: "Unknown"
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }
        Pair(versionName, versionCode)
    } catch (e: PackageManager.NameNotFoundException) {
        Pair("Unknown", "Unknown")
    }
}

/**
 * 关于标签页
 */
@Composable
fun AboutTab() {
    val activity = (androidx.compose.ui.platform.LocalContext.current as ComponentActivity)
    val (versionName, versionCode) = activity.getAppInfo()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = androidx.compose.ui.Alignment.Start
    ) {
        Text(
            text = "IMU Viewer",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "包名: ${activity.packageName}",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "版本号: $versionName",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "版本代码: $versionCode",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "© 2025 FireXMoon",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "firexmoon@hotmail.com",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}
