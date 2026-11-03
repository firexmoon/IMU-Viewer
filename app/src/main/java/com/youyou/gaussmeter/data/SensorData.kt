package com.youyou.gaussmeter.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

// 三轴磁场数据模型（单位 μT）
data class SensorData(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float
) {
    // 三轴合成强度
    val total: Float
        get() = sqrt(x * x + y * y + z * z)
}

// 当前磁场基线
data class MagneticBaseline(
    val x: Float,
    val y: Float,
    val z: Float
)

// 记录状态
data class RecordingState(
    val isRecording: Boolean = false,
    val rowCount: Int = 0,
    val limitReached: Boolean = false
)

/**
 * 一次基线越界告警记录。
 *
 * @param timestamp 触发声音告警的时间（毫秒）
 * @param maxDeltaUt burst 期内（从触发到回落至阈值内）单轴最大偏差，μT
 * @param durationMs burst 持续时长，毫秒。burst 未结束时为 0
 * @param ongoing true 表示 burst 仍在进行中
 */
data class AlertRecord(
    val timestamp: Long,
    val maxDeltaUt: Float,
    val durationMs: Long,
    val ongoing: Boolean
)

// 传感器数据存储类
class SensorDataStore {
    // 磁力计数据（用于曲线图，保留最近 25 秒）
    private val _magnetometerData = MutableStateFlow<List<SensorData>>(emptyList())
    val magnetometerData: StateFlow<List<SensorData>> = _magnetometerData

    // 当前实时数据
    private val _currentMagnetometer = MutableStateFlow(SensorData(0, 0f, 0f, 0f))
    val currentMagnetometer: StateFlow<SensorData> = _currentMagnetometer

    // 数据刷新率 (Hz)
    private val _magnetometerHz = MutableStateFlow(0f)
    val magnetometerHz: StateFlow<Float> = _magnetometerHz

    // 基线告警状态
    private val _baselineAlarm = MutableStateFlow(false)
    val baselineAlarm: StateFlow<Boolean> = _baselineAlarm

    // 当前基线
    private val _baseline = MutableStateFlow<MagneticBaseline?>(null)
    val baseline: StateFlow<MagneticBaseline?> = _baseline

    // 手机是否静止
    private val _isStill = MutableStateFlow(false)
    val isStill: StateFlow<Boolean> = _isStill

    // 告警记录：触发时间 + burst 最大偏差 + burst 持续时长（结束前为进行中）
    private val _alertHistory = MutableStateFlow<List<AlertRecord>>(emptyList())
    val alertHistory: StateFlow<List<AlertRecord>> = _alertHistory

    // 记录状态
    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState

    // 用于计算刷新率的时间戳列表
    private val magnetometerTimestamps = mutableListOf<Long>()

    // 刷新率计算的时间窗口 (1秒)
    private val HZ_CALC_WINDOW = 1000L

    private val MAX_TIME_MS = 60000L // 只保留最近60秒的数据，供曲线窗口选择

    fun updateMagnetometer(data: SensorData) {
        _currentMagnetometer.value = data
        updateDataList(data)
        updateHz(data.timestamp)
    }

    fun setBaseline(baseline: MagneticBaseline?) {
        _baseline.value = baseline
        if (baseline == null) {
            _baselineAlarm.value = false
        }
    }

    fun setBaselineAlarm(active: Boolean) {
        _baselineAlarm.value = active
    }

    fun setStill(still: Boolean) {
        _isStill.value = still
    }

    /** 新告警触发：以初始偏差作为临时最大值，burst 未结束。 */
    fun appendAlert(timestamp: Long, initialMaxDeltaUt: Float) {
        val next = ArrayList<AlertRecord>(_alertHistory.value.size + 1)
        next.add(AlertRecord(timestamp, initialMaxDeltaUt, 0L, ongoing = true))
        next.addAll(_alertHistory.value)
        if (next.size > MAX_ALERTS) next.subList(MAX_ALERTS, next.size).clear()
        _alertHistory.value = next
    }

    /** burst 进行中：刷新最新一条记录的最大偏差与时长。 */
    fun updateLatestBurst(maxDeltaUt: Float, durationMs: Long) {
        val list = _alertHistory.value
        if (list.isEmpty() || !list[0].ongoing) return
        val head = list[0]
        _alertHistory.value = list.toMutableList().also {
            it[0] = head.copy(maxDeltaUt = maxDeltaUt, durationMs = durationMs)
        }
    }

    /** burst 结束：固化最大偏差与时长并清除 ongoing 标记。 */
    fun finalizeLatestBurst(maxDeltaUt: Float, durationMs: Long) {
        val list = _alertHistory.value
        if (list.isEmpty() || !list[0].ongoing) return
        val head = list[0]
        _alertHistory.value = list.toMutableList().also {
            it[0] = head.copy(maxDeltaUt = maxDeltaUt, durationMs = durationMs, ongoing = false)
        }
    }

    fun clearAlerts() {
        _alertHistory.value = emptyList()
    }

    private val MAX_ALERTS = 200

    fun setRecordingState(state: RecordingState) {
        _recordingState.value = state
    }

    private fun updateDataList(data: SensorData) {
        val newList = _magnetometerData.value.toMutableList()
        newList.add(data)
        // 移除超过25秒前的数据
        val cutoffTime = data.timestamp - MAX_TIME_MS
        while (newList.isNotEmpty() && newList.first().timestamp < cutoffTime) {
            newList.removeAt(0)
        }
        _magnetometerData.value = newList
    }

    // 更新刷新率
    private fun updateHz(currentTime: Long) {
        magnetometerTimestamps.add(currentTime)

        // 移除超过1秒前的时间戳
        val cutoffTime = currentTime - HZ_CALC_WINDOW
        magnetometerTimestamps.removeAll { it < cutoffTime }

        // 计算刷新率
        val hz = if (magnetometerTimestamps.size < 2) {
            0f
        } else {
            val timeDiff = magnetometerTimestamps.last() - magnetometerTimestamps.first()
            if (timeDiff == 0L) {
                0f
            } else {
                ((magnetometerTimestamps.size - 1) * 1000f) / timeDiff
            }
        }

        _magnetometerHz.value = hz
    }
}
