package com.youyou.gaussmeter.data

import kotlin.math.sqrt

/**
 * 用磁场合成强度的方差判断手机是否静止。
 *
 * 静止时手机相对地磁场不转动，合成强度 |B| 几乎不变（噪声 < 0.3 μT）；
 * 一旦有旋转或移动，|B| 会跟着姿态变化出现几 μT 的抖动。
 * 比起加速度计省一个传感器注册，并且和磁力计共用回调。
 */
class StillnessDetector(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val thresholdUt: Float = DEFAULT_THRESHOLD_UT
) {
    private data class Sample(val t: Long, val total: Float)

    private val samples = ArrayDeque<Sample>()

    /**
     * 处理一个磁场样本，返回此刻是否判定为静止。
     * 样本不足窗口大小时返回 false。
     */
    fun onSample(timestamp: Long, x: Float, y: Float, z: Float): Boolean {
        val total = sqrt(x * x + y * y + z * z)
        samples.addLast(Sample(timestamp, total))
        while (samples.isNotEmpty() && samples.first().t < timestamp - windowMs) {
            samples.removeFirst()
        }
        if (samples.size < MIN_SAMPLES) return false

        val mean = samples.sumOf { it.total.toDouble() } / samples.size
        val variance = samples.sumOf {
            val d = it.total - mean
            d * d
        } / samples.size
        return sqrt(variance) < thresholdUt
    }

    fun reset() {
        samples.clear()
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 500L
        const val DEFAULT_THRESHOLD_UT = 0.6f
        private const val MIN_SAMPLES = 5
    }
}
