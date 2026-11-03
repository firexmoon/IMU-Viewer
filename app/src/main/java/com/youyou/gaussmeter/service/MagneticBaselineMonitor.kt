package com.youyou.gaussmeter.service

import com.youyou.gaussmeter.data.MagneticBaseline
import kotlin.math.abs
import kotlin.math.max

/**
 * 磁场基线告警判定。
 *
 * 判据：任一轴与基线的差值绝对值超过阈值即视为越界。
 * 越界时只触发一次警报，之后进入冷却期；必须回到阈值内才重新武装，
 * 因此持续的异常磁场不会连续响。
 *
 * 一次越界从告警触发开始到读数回落至阈值内结束，称为一个 burst。
 * burst 期间会累计最大偏差与持续时长，供 UI 展示。
 */
class MagneticBaselineMonitor(
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS
) {
    var threshold: Float = DEFAULT_THRESHOLD_UT
        set(value) {
            field = value.coerceAtLeast(MIN_THRESHOLD_UT)
        }

    var baseline: MagneticBaseline? = null
        private set

    // 是否需要等到回到阈值内才允许下一次告警
    private var armed = true
    private var lastAlertAt = Long.MIN_VALUE / 2

    // 当前 burst 跟踪
    private var inBurst = false
    private var burstStartAt = 0L
    private var burstMaxDelta = 0f

    val isArmed: Boolean get() = armed

    fun setBaseline(baseline: MagneticBaseline?) {
        this.baseline = baseline
        armed = true
        lastAlertAt = Long.MIN_VALUE / 2
        inBurst = false
        burstMaxDelta = 0f
    }

    /**
     * 处理一个样本。
     * @return 本次样本的告警状态变化（是否触发声音、是否处于 burst、是否结束 burst 等）。
     */
    fun onSample(timestamp: Long, x: Float, y: Float, z: Float): Result {
        val base = baseline ?: return Result.None

        val delta = max(max(abs(x - base.x), abs(y - base.y)), abs(z - base.z))
        val exceeded = delta > threshold

        if (!exceeded) {
            armed = true
            if (inBurst) {
                val duration = timestamp - burstStartAt
                val maxDelta = burstMaxDelta
                inBurst = false
                return Result(
                    triggered = false,
                    burstActive = false,
                    burstEnded = true,
                    burstMaxDelta = maxDelta,
                    burstDurationMs = duration
                )
            }
            return Result.None
        }

        // 越界：如果已重新武装且过了冷却期，开启新 burst 并触发一次告警
        if (!inBurst && armed && (timestamp - lastAlertAt) >= cooldownMs) {
            inBurst = true
            burstStartAt = timestamp
            burstMaxDelta = delta
            armed = false
            lastAlertAt = timestamp
            return Result(
                triggered = true,
                burstActive = true,
                burstEnded = false,
                burstMaxDelta = delta,
                burstDurationMs = 0L
            )
        }

        if (inBurst) {
            if (delta > burstMaxDelta) burstMaxDelta = delta
            return Result(
                triggered = false,
                burstActive = true,
                burstEnded = false,
                burstMaxDelta = burstMaxDelta,
                burstDurationMs = timestamp - burstStartAt
            )
        }

        // 越界但处于冷却期或尚未重新武装，什么都不记录
        return Result.None
    }

    /** 当前样本是否处于越界状态（用于 UI 显示）。 */
    fun isExceeded(x: Float, y: Float, z: Float): Boolean {
        val base = baseline ?: return false
        return abs(x - base.x) > threshold ||
            abs(y - base.y) > threshold ||
            abs(z - base.z) > threshold
    }

    data class Result(
        val triggered: Boolean,
        val burstActive: Boolean,
        val burstEnded: Boolean,
        val burstMaxDelta: Float,
        val burstDurationMs: Long
    ) {
        companion object {
            val None = Result(
                triggered = false,
                burstActive = false,
                burstEnded = false,
                burstMaxDelta = 0f,
                burstDurationMs = 0L
            )
        }
    }

    companion object {
        const val DEFAULT_THRESHOLD_UT = 10f
        const val MIN_THRESHOLD_UT = 1f
        const val DEFAULT_COOLDOWN_MS = 3000L
    }
}
