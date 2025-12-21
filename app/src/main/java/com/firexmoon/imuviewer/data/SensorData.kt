package com.firexmoon.imuviewer.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// 传感器数据模型
data class SensorData(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float
)

// 姿态数据模型
data class AttitudeData(
    val timestamp: Long,
    val roll: Float,  // 横滚角
    val pitch: Float, // 俯仰角
    val yaw: Float    // 偏航角
)

// 传感器数据存储类
class SensorDataStore {
    // 加速度计数据
    private val _accelerometerData = MutableStateFlow<List<SensorData>>(emptyList())
    val accelerometerData: StateFlow<List<SensorData>> = _accelerometerData
    
    // 陀螺仪数据
    private val _gyroscopeData = MutableStateFlow<List<SensorData>>(emptyList())
    val gyroscopeData: StateFlow<List<SensorData>> = _gyroscopeData
    
    // 磁力计数据
    private val _magnetometerData = MutableStateFlow<List<SensorData>>(emptyList())
    val magnetometerData: StateFlow<List<SensorData>> = _magnetometerData
    
    // 姿态数据
    private val _attitudeData = MutableStateFlow<List<AttitudeData>>(emptyList())
    val attitudeData: StateFlow<List<AttitudeData>> = _attitudeData
    
    // 当前实时数据
    private val _currentAccelerometer = MutableStateFlow(SensorData(0, 0f, 0f, 0f))
    val currentAccelerometer: StateFlow<SensorData> = _currentAccelerometer
    
    private val _currentGyroscope = MutableStateFlow(SensorData(0, 0f, 0f, 0f))
    val currentGyroscope: StateFlow<SensorData> = _currentGyroscope
    
    private val _currentMagnetometer = MutableStateFlow(SensorData(0, 0f, 0f, 0f))
    val currentMagnetometer: StateFlow<SensorData> = _currentMagnetometer
    
    private val _currentAttitude = MutableStateFlow(AttitudeData(0, 0f, 0f, 0f))
    val currentAttitude: StateFlow<AttitudeData> = _currentAttitude
    
    // 数据刷新率 (Hz)
    private val _accelerometerHz = MutableStateFlow(0f)
    val accelerometerHz: StateFlow<Float> = _accelerometerHz
    
    private val _gyroscopeHz = MutableStateFlow(0f)
    val gyroscopeHz: StateFlow<Float> = _gyroscopeHz
    
    private val _magnetometerHz = MutableStateFlow(0f)
    val magnetometerHz: StateFlow<Float> = _magnetometerHz
    
    private val _attitudeHz = MutableStateFlow(0f)
    val attitudeHz: StateFlow<Float> = _attitudeHz
    
    // 用于计算刷新率的时间戳列表
    private val accelerometerTimestamps = mutableListOf<Long>()
    private val gyroscopeTimestamps = mutableListOf<Long>()
    private val magnetometerTimestamps = mutableListOf<Long>()
    private val attitudeTimestamps = mutableListOf<Long>()
    
    // 刷新率计算的时间窗口 (1秒)
    private val HZ_CALC_WINDOW = 1000L
    
    private val MAX_TIME_MS = 25000L // 只保留最近25秒的数据
    
    fun updateAccelerometer(data: SensorData) {
        _currentAccelerometer.value = data
        updateDataList(_accelerometerData, data)
        updateHz(accelerometerTimestamps, data.timestamp, _accelerometerHz)
    }
    
    fun updateGyroscope(data: SensorData) {
        _currentGyroscope.value = data
        updateDataList(_gyroscopeData, data)
        updateHz(gyroscopeTimestamps, data.timestamp, _gyroscopeHz)
    }
    
    fun updateMagnetometer(data: SensorData) {
        _currentMagnetometer.value = data
        updateDataList(_magnetometerData, data)
        updateHz(magnetometerTimestamps, data.timestamp, _magnetometerHz)
    }
    
    fun updateAttitude(data: AttitudeData) {
        _currentAttitude.value = data
        updateAttitudeList(_attitudeData, data)
        updateHz(attitudeTimestamps, data.timestamp, _attitudeHz)
    }
    
    private fun updateDataList(stateFlow: MutableStateFlow<List<SensorData>>, data: SensorData) {
        val newList = stateFlow.value.toMutableList()
        newList.add(data)
        // 移除超过25秒前的数据
        val cutoffTime = data.timestamp - MAX_TIME_MS
        while (newList.isNotEmpty() && newList.first().timestamp < cutoffTime) {
            newList.removeAt(0)
        }
        stateFlow.value = newList
    }
    
    private fun updateAttitudeList(stateFlow: MutableStateFlow<List<AttitudeData>>, data: AttitudeData) {
        val newList = stateFlow.value.toMutableList()
        newList.add(data)
        // 移除超过25秒前的数据
        val cutoffTime = data.timestamp - MAX_TIME_MS
        while (newList.isNotEmpty() && newList.first().timestamp < cutoffTime) {
            newList.removeAt(0)
        }
        stateFlow.value = newList
    }
    
    // 更新刷新率
    private fun updateHz(
        timestamps: MutableList<Long>,
        currentTime: Long,
        hzFlow: MutableStateFlow<Float>
    ) {
        // 添加当前时间戳
        timestamps.add(currentTime)
        
        // 移除超过1秒前的时间戳
        val cutoffTime = currentTime - HZ_CALC_WINDOW
        timestamps.removeAll { it < cutoffTime }
        
        // 计算刷新率
        val hz = if (timestamps.size < 2) {
            0f
        } else {
            val timeDiff = timestamps.last() - timestamps.first()
            if (timeDiff == 0L) {
                0f
            } else {
                ((timestamps.size - 1) * 1000f) / timeDiff
            }
        }
        
        hzFlow.value = hz
    }
}
