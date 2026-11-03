package com.youyou.gaussmeter.data

import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * 按设定采样率采集磁力计数据并缓存为 CSV 行。
 *
 * 传感器回调频率远高于导出采样率，这里用时间戳做限流：
 * 只有距上一次采样超过 1000/hz 毫秒时才记录一行。
 */
class CsvRecorder {

    private class Row(
        val timestamp: Long,
        val x: Float,
        val y: Float,
        val z: Float
    )

    private val rows = ArrayList<Row>()
    private val lock = Any()
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    @Volatile
    var isRecording: Boolean = false
        private set

    // 导出采样率（Hz）
    @Volatile
    var sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ

    @Volatile
    var limitReached: Boolean = false
        private set

    private var lastSampleAt = 0L
    private var hasSampled = false

    val rowCount: Int
        get() = synchronized(lock) { rows.size }

    fun start() {
        synchronized(lock) {
            rows.clear()
            lastSampleAt = 0L
            limitReached = false
            isRecording = true
        }
    }

    fun stop() {
        isRecording = false
    }

    fun clear(): Int {
        synchronized(lock) {
            val count = rows.size
            rows.clear()
            lastSampleAt = 0L
            limitReached = false
            return count
        }
    }

    /** 传感器每次回调时调用，内部按采样率决定是否真正记录。 */
    fun onSample(timestamp: Long, x: Float, y: Float, z: Float) {
        if (!isRecording) return

        synchronized(lock) {
            if (!isRecording || limitReached) return

            val interval = maxOf(1L, 1000L / sampleRateHz.coerceAtLeast(1))
            if (hasSampled && timestamp - lastSampleAt < interval) return

            lastSampleAt = timestamp
            hasSampled = true
            rows.add(Row(timestamp, x, y, z))

            if (rows.size >= MAX_ROWS) {
                limitReached = true
                isRecording = false
            }
        }
    }

    /** 把已缓存的数据写成 CSV。返回写入的行数。 */
    fun writeCsv(writer: Writer): Int {
        val snapshot = synchronized(lock) { ArrayList(rows) }

        writer.write(HEADER)
        writer.write("\n")

        snapshot.forEach { row ->
            val total = sqrt(row.x * row.x + row.y * row.y + row.z * row.z)
            val iso = synchronized(isoFormat) { isoFormat.format(Date(row.timestamp)) }
            writer.write(
                "${row.timestamp},$iso," +
                    "${format(row.x)},${format(row.y)},${format(row.z)},${format(total)}\n"
            )
        }

        return snapshot.size
    }

    private fun format(value: Float): String = String.format(Locale.US, "%.3f", value)

    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 10
        const val MIN_SAMPLE_RATE_HZ = 1
        const val MAX_SAMPLE_RATE_HZ = 100
        const val MAX_ROWS = 500_000
        const val HEADER = "timestamp_ms,iso_time,bx_ut,by_ut,bz_ut,b_total_ut"
    }
}
