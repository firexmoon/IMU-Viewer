package com.youyou.gaussmeter

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.youyou.gaussmeter.data.CsvRecorder
import com.youyou.gaussmeter.data.RecordingState
import com.youyou.gaussmeter.data.SensorData
import com.youyou.gaussmeter.service.MagneticBaselineMonitor
import com.youyou.gaussmeter.service.SensorService
import com.youyou.gaussmeter.ui.components.DEFAULT_HALL_POS
import com.youyou.gaussmeter.ui.components.HallFloatingMarker
import com.youyou.gaussmeter.ui.components.OnboardingCard
import com.youyou.gaussmeter.ui.components.OnboardingStep
import com.youyou.gaussmeter.ui.components.RealtimeChart
import com.youyou.gaussmeter.ui.theme.IMUViewerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    private var _sensorService: SensorService? = null
    private var isBound = false
    private lateinit var sensorServiceIntent: Intent

    private val sensorServiceState = mutableStateOf<SensorService?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SensorService.LocalBinder
            _sensorService = binder.getService()
            isBound = true
            sensorServiceState.value = _sensorService
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _sensorService = null
            isBound = false
            sensorServiceState.value = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorServiceIntent = Intent(this, SensorService::class.java)
        startService(sensorServiceIntent)
        bindService(sensorServiceIntent, connection, BIND_AUTO_CREATE)

        setContent {
            IMUViewerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
        AlertDialog.Builder(this)
            .setTitle("退出提示")
            .setMessage("确定要退出应用吗？")
            .setPositiveButton("确定") { _, _ ->
                stopService(sensorServiceIntent)
                finish()
                exitProcess(0)
            }
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show()
    }
}

@Composable
fun SensorDataApp(sensorService: SensorService?) {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences("magnetometer", Context.MODE_PRIVATE)
    }
    val currentService by rememberUpdatedState(sensorService)

    // 共享状态
    var currentMag by remember { mutableStateOf(SensorData(0, 0f, 0f, 0f)) }
    var magnetometerData by remember { mutableStateOf(emptyList<SensorData>()) }
    var magnetometerHz by remember { mutableFloatStateOf(0f) }
    var alarmActive by remember { mutableStateOf(false) }
    var hasBaseline by remember { mutableStateOf(false) }
    var recordingState by remember { mutableStateOf(RecordingState()) }
    var threshold by remember { mutableFloatStateOf(MagneticBaselineMonitor.DEFAULT_THRESHOLD_UT) }
    var sampleRate by remember { mutableIntStateOf(CsvRecorder.DEFAULT_SAMPLE_RATE_HZ) }
    var chartWindowSec by remember { mutableIntStateOf(prefs.getInt(KEY_CHART_WINDOW_SEC, 25).coerceIn(5, 60)) }
    var hallPos by remember {
        mutableStateOf(
            Offset(
                prefs.getFloat(KEY_HALL_X, DEFAULT_HALL_POS.x),
                prefs.getFloat(KEY_HALL_Y, DEFAULT_HALL_POS.y)
            )
        )
    }
    var markerVisible by remember { mutableStateOf(prefs.getBoolean(KEY_HALL_SHOW, true)) }
    var markerEditing by remember { mutableStateOf(false) }
    var isStill by remember { mutableStateOf(false) }
    var alertHistory by remember { mutableStateOf(emptyList<com.youyou.gaussmeter.data.AlertRecord>()) }
    var tabIndex by remember { mutableIntStateOf(0) }

    // 引导状态
    var onboardingDone by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDING_DONE, false)) }
    var onboardingStep by remember { mutableStateOf(OnboardingStep.Welcome) }

    val dataStore = currentService?.getSensorDataStore()
    LaunchedEffect(dataStore) {
        if (dataStore != null) {
            launch { dataStore.currentMagnetometer.collect { currentMag = it } }
            launch { dataStore.magnetometerData.collect { magnetometerData = it } }
            launch { dataStore.magnetometerHz.collect { magnetometerHz = it } }
            launch { dataStore.baselineAlarm.collect { alarmActive = it } }
            launch { dataStore.baseline.collect { hasBaseline = it != null } }
            launch { dataStore.recordingState.collect { recordingState = it } }
            launch { dataStore.isStill.collect { isStill = it } }
            launch { dataStore.alertHistory.collect { alertHistory = it } }
        }
    }

    // 每个引导步骤对应的 Tab
    fun tabForStep(step: OnboardingStep): Int = when (step) {
        OnboardingStep.Welcome -> 0           // 读数
        OnboardingStep.HoldStill -> 0         // 读数：看合成值是否稳
        OnboardingStep.LocateIntro -> 3       // 标记：说明和控制面板
        OnboardingStep.LocateCalibrate -> 0   // 读数：扫磁铁时看读数变化
        OnboardingStep.SetBaseline -> 1       // 基线
        OnboardingStep.Record -> 2            // 记录
    }

    // 引导步骤变化时自动跳到对应 Tab，并把"定位校准"这一步设成可编辑
    LaunchedEffect(onboardingStep, onboardingDone) {
        if (!onboardingDone) {
            tabIndex = tabForStep(onboardingStep)
            markerEditing = onboardingStep == OnboardingStep.LocateCalibrate
        }
    }


    // 设基线成功后自动推进到"记录"步
    LaunchedEffect(hasBaseline, onboardingStep) {
        if (onboardingStep == OnboardingStep.SetBaseline && hasBaseline) {
            onboardingStep = OnboardingStep.Record
        }
    }
    LaunchedEffect(currentService) {
        currentService?.let {
            threshold = it.threshold
            sampleRate = it.sampleRateHz
        }
    }

    val tabs = listOf("读数", "基线", "记录", "标记", "关于")

    // 外层 Box：Tab 内容在底，悬浮方框铺在上面
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp)
        ) {
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

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                when (tabIndex) {
                    0 -> ReadingsTab(
                        currentMag = currentMag,
                        hz = magnetometerHz,
                        alarmActive = alarmActive,
                        hasBaseline = hasBaseline,
                        chartData = magnetometerData,
                        chartWindowSec = chartWindowSec,
                        onChartWindowSecChange = {
                            chartWindowSec = it
                            prefs.edit().putInt(KEY_CHART_WINDOW_SEC, it).apply()
                        }
                    )
                    1 -> BaselineTab(
                        currentService = currentService,
                        hasBaseline = hasBaseline,
                        alarmActive = alarmActive,
                        threshold = threshold,
                        onThresholdChange = { threshold = it; currentService?.setThreshold(it) },
                        alertHistory = alertHistory,
                        onClearAlerts = { currentService?.getSensorDataStore()?.clearAlerts() }
                    )
                    2 -> RecordingTab(
                        currentService = currentService,
                        sampleRate = sampleRate,
                        onSampleRateChange = { sampleRate = it; currentService?.setSampleRate(it) },
                        recordingState = recordingState
                    )
                    3 -> MarkerTab(
                        markerVisible = markerVisible,
                        onMarkerVisibleChange = {
                            markerVisible = it
                            prefs.edit().putBoolean(KEY_HALL_SHOW, it).apply()
                        },
                        onReset = {
                            hallPos = DEFAULT_HALL_POS
                            prefs.edit()
                                .putFloat(KEY_HALL_X, DEFAULT_HALL_POS.x)
                                .putFloat(KEY_HALL_Y, DEFAULT_HALL_POS.y)
                                .apply()
                        },
                        isEditing = markerEditing,
                        onStartEditing = {
                            markerEditing = true
                            tabIndex = 0   // 跳到读数页，方便一边扫一边看
                            Toast.makeText(
                                context,
                                "校准中：拖动橙色方框，双击方框完成校准",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                    4 -> AboutTab(
                        onRestartOnboarding = {
                            prefs.edit().putBoolean(KEY_ONBOARDING_DONE, false).apply()
                            onboardingStep = OnboardingStep.Welcome
                            onboardingDone = false
                        }
                    )
                }
            }
        }

        // 全屏悬浮标记：永远在最上层，不随 Tab 切换消失
        if (markerVisible) {
            HallFloatingMarker(
                position = hallPos,
                onPositionChange = { newPos ->
                    hallPos = newPos
                    prefs.edit()
                        .putFloat(KEY_HALL_X, newPos.x)
                        .putFloat(KEY_HALL_Y, newPos.y)
                        .apply()
                },
                editable = markerEditing,
                onDoneEditing = {
                    markerEditing = false
                    Toast.makeText(context, "校准完成，方框已锁定", Toast.LENGTH_SHORT).show()
                },
                boxColor = when {
                    alarmActive -> Color(0xFFFF5252)       // 告警优先
                    markerEditing -> Color(0xFFFF9800)     // 校准中：醒目橙
                    else -> Color(0xFF9E9E9E)              // 锁定：灰
                }
            )
        }

        // 底部引导卡片
        if (!onboardingDone) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                OnboardingCard(
                    step = onboardingStep,
                    isReady = when (onboardingStep) {
                        OnboardingStep.HoldStill -> isStill
                        else -> true
                    },
                    onNext = {
                        when (onboardingStep) {
                            OnboardingStep.Welcome -> onboardingStep = OnboardingStep.HoldStill
                            OnboardingStep.HoldStill -> onboardingStep = OnboardingStep.LocateIntro
                            OnboardingStep.LocateIntro -> onboardingStep = OnboardingStep.LocateCalibrate
                            OnboardingStep.LocateCalibrate -> onboardingStep = OnboardingStep.SetBaseline
                            OnboardingStep.SetBaseline -> onboardingStep = OnboardingStep.Record
                            OnboardingStep.Record -> {
                                onboardingDone = true
                                prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
                            }
                        }
                    },
                    onSkip = {
                        onboardingDone = true
                        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
                    }
                )
            }
        }
    }
}

// 1. 读数 Tab
@Composable
private fun ReadingsTab(
    currentMag: SensorData,
    hz: Float,
    alarmActive: Boolean,
    hasBaseline: Boolean,
    chartData: List<SensorData>,
    chartWindowSec: Int,
    onChartWindowSecChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("当前磁场", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            if (hasBaseline) {
                Text(
                    text = if (alarmActive) "⚠ 基线异常" else "基线正常",
                    color = if (alarmActive) Color(0xFFFF5252) else Color(0xFF4CAF50),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        ReadingRow("X轴 μT", currentMag.x.format(2), Color(0xFF4CAF50))
        ReadingRow("Y轴 μT", currentMag.y.format(2), Color(0xFF2196F3))
        ReadingRow("Z轴 μT", currentMag.z.format(2), Color(0xFFFF9800))
        ReadingRow(
            "合成 μT",
            currentMag.total.format(2),
            if (alarmActive) Color(0xFFFF5252) else Color.White
        )
        ReadingRow("刷新率", "${hz.toInt()} Hz", Color.White)

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("磁场变化曲线", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("窗口 ${chartWindowSec}s", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = chartWindowSec.toFloat(),
            onValueChange = { onChartWindowSecChange(it.toInt().coerceIn(5, 60)) },
            valueRange = 5f..60f
        )
        RealtimeChart(data = chartData, windowSeconds = chartWindowSec)
    }
}

// 2. 基线 Tab
@Composable
private fun BaselineTab(
    currentService: SensorService?,
    hasBaseline: Boolean,
    alarmActive: Boolean,
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    alertHistory: List<com.youyou.gaussmeter.data.AlertRecord>,
    onClearAlerts: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("基线告警", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "设基线后，任一轴偏离基线超过阈值就会响一声。冷却期 3 秒。",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodyMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { currentService?.setBaselineFromCurrent() }) {
                Text(if (hasBaseline) "重新设基线" else "设基线")
            }
            OutlinedButton(
                onClick = { currentService?.clearBaseline() },
                enabled = hasBaseline
            ) {
                Text("清除基线")
            }
        }

        Text(
            text = "当前状态：" + when {
                !hasBaseline -> "未设置"
                alarmActive -> "⚠ 基线异常"
                else -> "基线正常"
            },
            color = when {
                !hasBaseline -> Color.LightGray
                alarmActive -> Color(0xFFFF5252)
                else -> Color(0xFF4CAF50)
            },
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "阈值：${threshold.toInt()} μT",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        Slider(
            value = threshold,
            onValueChange = onThresholdChange,
            valueRange = MagneticBaselineMonitor.MIN_THRESHOLD_UT..100f
        )
        Text(
            text = "范围 1–100 μT，默认 10 μT。阈值越小越灵敏。",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "告警记录（${alertHistory.size}）",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedButton(
                onClick = onClearAlerts,
                enabled = alertHistory.isNotEmpty()
            ) {
                Text("清空")
            }
        }
        if (alertHistory.isEmpty()) {
            Text(
                text = "暂无告警。",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            val formatter = remember {
                java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                alertHistory.forEachIndexed { index, rec ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${alertHistory.size - index}",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(40.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatter.format(java.util.Date(rec.timestamp)),
                                color = Color(0xFFFF5252),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Text(
                                text = buildString {
                                    append("峰值 ")
                                    append("%.1f".format(rec.maxDeltaUt))
                                    append(" μT · ")
                                    if (rec.ongoing) {
                                        append("进行中 ")
                                        append(formatDuration(rec.durationMs))
                                    } else {
                                        append("持续 ")
                                        append(formatDuration(rec.durationMs))
                                    }
                                },
                                color = if (rec.ongoing) Color(0xFFFF9800) else Color.LightGray,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// 3. 记录 Tab
@Composable
private fun RecordingTab(
    currentService: SensorService?,
    sampleRate: Int,
    onSampleRateChange: (Int) -> Unit,
    recordingState: RecordingState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exported by remember { mutableStateOf(false) }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val service = currentService ?: return@rememberLauncherForActivityResult
        scope.launch {
            val rows = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.bufferedWriter().use { writer ->
                            service.exportCsv(writer)
                        }
                    } ?: 0
                } catch (e: Exception) {
                    -1
                }
            }
            if (rows >= 0) {
                exported = true
                Toast.makeText(context, "已导出 $rows 行", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "导出失败", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("CSV 记录", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "点击开始后按设定采样率缓存数据，停止后导出到任意位置。",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(4.dp))
        Text("导出采样率：$sampleRate Hz", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Slider(
            value = sampleRate.toFloat(),
            onValueChange = {
                val hz = it.toInt().coerceIn(
                    CsvRecorder.MIN_SAMPLE_RATE_HZ,
                    CsvRecorder.MAX_SAMPLE_RATE_HZ
                )
                onSampleRateChange(hz)
            },
            valueRange = CsvRecorder.MIN_SAMPLE_RATE_HZ.toFloat()..CsvRecorder.MAX_SAMPLE_RATE_HZ.toFloat()
        )
        Text("范围 1–100 Hz，上限 50 万行自动停止。", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (recordingState.isRecording) currentService?.stopRecording()
                else {
                    exported = false
                    currentService?.startRecording()
                }
            }) {
                Text(if (recordingState.isRecording) "停止记录" else "开始记录")
            }
            OutlinedButton(
                onClick = {
                    saveCsvLauncher.launch("magnetometer_${System.currentTimeMillis()}.csv")
                },
                enabled = !recordingState.isRecording && recordingState.rowCount > 0
            ) {
                Text("导出 CSV")
            }
            OutlinedButton(
                onClick = { currentService?.clearRecording(); exported = false },
                enabled = !recordingState.isRecording && recordingState.rowCount > 0
            ) {
                Text("清空")
            }
        }

        Text(
            text = buildString {
                append("已记录 ${recordingState.rowCount} 行")
                if (recordingState.isRecording) append("（记录中）")
                if (recordingState.limitReached) append(" — 达到上限自动停止")
                if (exported) append(" — 上次导出成功")
            },
            color = when {
                recordingState.limitReached -> Color(0xFFFF5252)
                recordingState.isRecording -> Color(0xFF4CAF50)
                else -> Color.LightGray
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// 4. 标记 Tab
@Composable
private fun MarkerTab(
    markerVisible: Boolean,
    onMarkerVisibleChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    isEditing: Boolean,
    onStartEditing: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("霍尔元件位置", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "方框是悬浮在屏幕任意位置的参考标记，拖它到你手机上霍尔元件" +
                "的实际位置。Android 不提供硬件坐标，所以位置需要手动校准。",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onStartEditing, enabled = markerVisible && !isEditing) {
            Text(if (isEditing) "校准中…" else "开始校准")
        }
        Text(
            text = if (isEditing)
                "校准中：会自动跳到读数页。拖动橙色方框找到磁场最强点，双击方框完成校准。"
            else
                "方框已锁定（灰色），不会拦截点击。点上面按钮开始校准，完成时双击方框即可。",
            color = if (isEditing) Color(0xFFFF9800) else Color.LightGray,
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMarkerVisibleChange(!markerVisible) }) {
                Text(if (markerVisible) "隐藏方框" else "显示方框")
            }
            OutlinedButton(onClick = onReset, enabled = markerVisible) {
                Text("复位到屏幕中央")
            }
        }
        Text(
            text = "小提示：方框越过告警阈值时会变红。",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ReadingRow(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = color, modifier = Modifier.width(120.dp))
        Text(
            text = value,
            color = color,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

fun Float.format(digits: Int): String {
    val str = "%+.${digits}f".format(this)
    if (str.startsWith("-0.0")) return "+0.0"
    return str
}

private fun formatDuration(ms: Long): String {
    if (ms < 1000) return "${ms} ms"
    val sec = ms / 1000.0
    return "%.1f s".format(sec)
}

private const val KEY_HALL_X = "hall_x"
private const val KEY_HALL_Y = "hall_y"
private const val KEY_HALL_SHOW = "hall_show"
private const val KEY_ONBOARDING_DONE = "onboarding_done"
private const val KEY_CHART_WINDOW_SEC = "chart_window_sec"

fun ComponentActivity.getAppInfo(): Pair<String, String> {
    return try {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName ?: "Unknown"
        val versionCode = packageInfo.longVersionCode.toString()
        Pair(versionName, versionCode)
    } catch (e: PackageManager.NameNotFoundException) {
        Pair("Unknown", "Unknown")
    }
}

@Composable
fun AboutTab(onRestartOnboarding: () -> Unit = {}) {
    val activity = (LocalContext.current as ComponentActivity)
    val (versionName, _) = activity.getAppInfo()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("高斯计 v$versionName", color = Color.White, style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(4.dp))
        Button(onClick = onRestartOnboarding) {
            Text("查看使用指引")
        }

        Spacer(Modifier.height(12.dp))
        Text("什么是高斯计", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "高斯计是用来测量磁场强度的仪器，名字源于磁感应强度单位高斯（Gs，1 Gs = 100 μT）。" +
                "本应用借助手机内置的磁力传感器读取三轴磁场（单位 μT），可用于判断磁铁极性、" +
                "定位隐藏磁铁、校验屏蔽效果、或检测磁场相对于初始基线的变化。" +
                "手机传感器非专业设备，读数仅作参考。",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(12.dp))
        Text("用法", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "1. 把手机放平静止，等「读数」页数值稳定\n" +
                "2. 在「标记」页点开始校准，用磁铁沿屏幕扫找读数最大点，拖方框到该点，双击方框锁定\n" +
                "3. 在「基线」页点设基线并调阈值，之后磁场变化超阈值会响一下\n" +
                "4. 在「记录」页选采样率、开始/停止记录，再导出 CSV",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(12.dp))
        Text("开源信息", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "本修改版 Copyright © 2026 悠悠，基于 MIT License 分发。",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "基于 IMU Viewer（Copyright © 2025 李勃，MIT License）修改，专用于磁场测量。",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "本项目仓库：https://github.com/youyou-pm10/IMU-Viewer-Magnetic",
            color = Color(0xFF90CAF9),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "原项目仓库：https://github.com/firexmoon/IMU-Viewer",
            color = Color(0xFF90CAF9),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}
