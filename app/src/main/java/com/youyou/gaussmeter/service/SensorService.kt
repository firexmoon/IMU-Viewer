package com.youyou.gaussmeter.service

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Binder
import android.os.IBinder
import com.youyou.gaussmeter.data.CsvRecorder
import com.youyou.gaussmeter.data.MagneticBaseline
import com.youyou.gaussmeter.data.RecordingState
import com.youyou.gaussmeter.data.SensorData
import com.youyou.gaussmeter.data.SensorDataStore
import com.youyou.gaussmeter.data.StillnessDetector
import java.io.Writer

class SensorService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var magnetometer: Sensor

    private val sensorDataStore = SensorDataStore()
    private val baselineMonitor = MagneticBaselineMonitor()
    private val csvRecorder = CsvRecorder()
    private val stillnessDetector = StillnessDetector()

    private var prefs: SharedPreferences? = null
    private var toneGenerator: ToneGenerator? = null

    // 当前传感器数据
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

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)!!

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        baselineMonitor.threshold = prefs!!.getFloat(KEY_THRESHOLD, MagneticBaselineMonitor.DEFAULT_THRESHOLD_UT)
        csvRecorder.sampleRateHz = prefs!!.getInt(KEY_SAMPLE_RATE, CsvRecorder.DEFAULT_SAMPLE_RATE_HZ)

        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_ALARM, TONE_VOLUME)
        } catch (e: RuntimeException) {
            null
        }

        // 只注册磁力计，使用最高采样率（导出采样率在 CsvRecorder 内限流）
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        toneGenerator?.release()
        toneGenerator = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 返回START_STICKY，确保服务被杀死后能重启
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return

            val timestamp = System.currentTimeMillis()
            currentMagnetometer = SensorData(
                timestamp = timestamp,
                x = it.values[0],
                y = it.values[1],
                z = it.values[2]
            )

            sensorDataStore.updateMagnetometer(currentMagnetometer)

            // 静止判定
            sensorDataStore.setStill(
                stillnessDetector.onSample(timestamp, it.values[0], it.values[1], it.values[2])
            )

            // 基线告警判定
            val result = baselineMonitor.onSample(timestamp, it.values[0], it.values[1], it.values[2])
            sensorDataStore.setBaselineAlarm(
                baselineMonitor.isExceeded(it.values[0], it.values[1], it.values[2])
            )
            if (result.triggered) {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_DURATION_MS)
                sensorDataStore.appendAlert(timestamp, result.burstMaxDelta)
            } else if (result.burstActive) {
                sensorDataStore.updateLatestBurst(result.burstMaxDelta, result.burstDurationMs)
            } else if (result.burstEnded) {
                sensorDataStore.finalizeLatestBurst(result.burstMaxDelta, result.burstDurationMs)
            }

            // CSV 采样（内部按采样率限流）
            csvRecorder.onSample(timestamp, it.values[0], it.values[1], it.values[2])
            publishRecordingState()

            if (csvRecorder.limitReached && csvRecorder.isRecording.not()) {
                // 达到行数上限后自动停止
                csvRecorder.stop()
                publishRecordingState()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 传感器精度变化时的处理
    }

    // ---- 基线 ----

    fun setBaselineFromCurrent() {
        val data = currentMagnetometer
        val baseline = MagneticBaseline(data.x, data.y, data.z)
        baselineMonitor.setBaseline(baseline)
        sensorDataStore.setBaseline(baseline)
        sensorDataStore.setBaselineAlarm(false)
    }

    fun clearBaseline() {
        baselineMonitor.setBaseline(null)
        sensorDataStore.setBaseline(null)
        sensorDataStore.setBaselineAlarm(false)
    }

    fun setThreshold(value: Float) {
        baselineMonitor.threshold = value
        prefs?.edit()?.putFloat(KEY_THRESHOLD, value)?.apply()
    }

    val threshold: Float get() = baselineMonitor.threshold

    // ---- 记录 ----

    fun setSampleRate(hz: Int) {
        csvRecorder.sampleRateHz = hz.coerceIn(CsvRecorder.MIN_SAMPLE_RATE_HZ, CsvRecorder.MAX_SAMPLE_RATE_HZ)
        prefs?.edit()?.putInt(KEY_SAMPLE_RATE, csvRecorder.sampleRateHz)?.apply()
    }

    val sampleRateHz: Int get() = csvRecorder.sampleRateHz

    fun startRecording() {
        csvRecorder.start()
        publishRecordingState()
    }

    fun stopRecording() {
        csvRecorder.stop()
        publishRecordingState()
    }

    fun clearRecording() {
        csvRecorder.clear()
        publishRecordingState()
    }

    fun exportCsv(writer: Writer): Int {
        return csvRecorder.writeCsv(writer)
    }

    private fun publishRecordingState() {
        sensorDataStore.setRecordingState(
            RecordingState(
                isRecording = csvRecorder.isRecording,
                rowCount = csvRecorder.rowCount,
                limitReached = csvRecorder.limitReached
            )
        )
    }

    // 获取传感器数据存储实例
    fun getSensorDataStore(): SensorDataStore {
        return sensorDataStore
    }

    companion object {
        private const val PREFS_NAME = "magnetometer"
        private const val KEY_THRESHOLD = "alert_threshold"
        private const val KEY_SAMPLE_RATE = "export_sample_rate"
        private const val TONE_VOLUME = 80
        private const val TONE_DURATION_MS = 250
    }
}
