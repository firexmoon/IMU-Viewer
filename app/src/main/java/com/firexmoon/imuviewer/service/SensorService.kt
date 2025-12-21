package com.firexmoon.imuviewer.service

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.IBinder
import com.firexmoon.imuviewer.algorithm.AttitudeCalculator
import com.firexmoon.imuviewer.data.AttitudeData
import com.firexmoon.imuviewer.data.SensorData
import com.firexmoon.imuviewer.data.SensorDataStore

class SensorService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var gyroscope: Sensor
    private lateinit var magnetometer: Sensor
    
    private val attitudeCalculator = AttitudeCalculator()
    private val sensorDataStore = SensorDataStore()
    
    // 当前传感器数据
    private var currentAccelerometer = SensorData(0, 0f, 0f, 0f)
    private var currentGyroscope = SensorData(0, 0f, 0f, 0f)
    private var currentMagnetometer = SensorData(0, 0f, 0f, 0f)
    
    // Binder用于绑定服务
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): SensorService = this@SensorService
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化传感器管理器
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        
        // 获取传感器实例
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)!!
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)!!
        
        // 注册传感器监听器，使用最高采样率
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_FASTEST // 最快的采样率，约200-1000Hz
        )
        
        sensorManager.registerListener(
            this,
            gyroscope,
            SensorManager.SENSOR_DELAY_FASTEST
        )
        
        sensorManager.registerListener(
            this,
            magnetometer,
            SensorManager.SENSOR_DELAY_FASTEST
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 取消注册传感器监听器
        sensorManager.unregisterListener(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 返回START_STICKY，确保服务被杀死后能重启
        return START_STICKY
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val timestamp = System.currentTimeMillis()
            
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    currentAccelerometer = SensorData(
                        timestamp = timestamp,
                        x = it.values[0],
                        y = it.values[1],
                        z = it.values[2]
                    )
                    sensorDataStore.updateAccelerometer(currentAccelerometer)
                }
                
                Sensor.TYPE_GYROSCOPE -> {
                    currentGyroscope = SensorData(
                        timestamp = timestamp,
                        x = it.values[0],
                        y = it.values[1],
                        z = it.values[2]
                    )
                    sensorDataStore.updateGyroscope(currentGyroscope)
                }
                
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    currentMagnetometer = SensorData(
                        timestamp = timestamp,
                        x = it.values[0],
                        y = it.values[1],
                        z = it.values[2]
                    )
                    sensorDataStore.updateMagnetometer(currentMagnetometer)
                }
            }
            
            // 每次传感器数据更新时都更新姿态，与其他传感器数据保持一致的刷新率
            updateAttitude()
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 传感器精度变化时的处理
    }
    
    // 更新姿态数据
    private fun updateAttitude() {
        val timestamp = System.currentTimeMillis()
        
        val (roll, pitch, yaw) = attitudeCalculator.updateAttitude(
            ax = currentAccelerometer.x,
            ay = currentAccelerometer.y,
            az = currentAccelerometer.z,
            gx = currentGyroscope.x,
            gy = currentGyroscope.y,
            gz = currentGyroscope.z,
            mx = currentMagnetometer.x,
            my = currentMagnetometer.y,
            mz = currentMagnetometer.z,
            timestamp = timestamp
        )
        
        val attitudeData = AttitudeData(
            timestamp = timestamp,
            roll = roll,
            pitch = pitch,
            yaw = yaw
        )
        
        sensorDataStore.updateAttitude(attitudeData)
    }
    
    // 获取传感器数据存储实例
    fun getSensorDataStore(): SensorDataStore {
        return sensorDataStore
    }
}
