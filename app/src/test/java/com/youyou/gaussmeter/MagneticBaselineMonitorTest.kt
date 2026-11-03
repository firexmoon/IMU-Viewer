package com.youyou.gaussmeter

import com.youyou.gaussmeter.data.MagneticBaseline
import com.youyou.gaussmeter.service.MagneticBaselineMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagneticBaselineMonitorTest {

    private fun monitor(threshold: Float = 10f, cooldownMs: Long = 3000L) =
        MagneticBaselineMonitor(cooldownMs).apply {
            this.threshold = threshold
            setBaseline(MagneticBaseline(20f, 30f, -40f))
        }

    @Test
    fun noAlertWithoutBaseline() {
        val m = MagneticBaselineMonitor()
        assertFalse(m.onSample(1000L, 999f, 999f, 999f).triggered)
    }

    @Test
    fun singleAxisExceedingTriggersOnce() {
        val m = monitor()
        assertTrue(m.onSample(1000L, 45f, 30f, -40f).triggered) // X 偏 25 μT
        assertFalse("同一次越界不应重复触发", m.onSample(1100L, 46f, 30f, -40f).triggered)
    }

    @Test
    fun otherAxesAlsoTrigger() {
        val m = monitor()
        assertTrue(m.onSample(1000L, 20f, 30f, -75f).triggered) // Z 偏 35 μT
    }

    @Test
    fun withinThresholdDoesNotTrigger() {
        val m = monitor()
        assertFalse(m.onSample(1000L, 29f, 39f, -49f).triggered) // 每轴偏 9 μT
    }

    @Test
    fun rearmsAfterReturningToBaseline() {
        val m = monitor()
        assertTrue(m.onSample(1000L, 45f, 30f, -40f).triggered)
        val end = m.onSample(2000L, 20f, 30f, -40f) // 回到基线，重新武装
        assertFalse(end.triggered)
        assertTrue("burst 结束时应该上报", end.burstEnded)
        assertTrue(m.onSample(4000L, 45f, 30f, -40f).triggered) // 冷却期已过
    }

    @Test
    fun flappingWithinCooldownIsSuppressed() {
        val m = monitor(cooldownMs = 3000L)
        assertTrue(m.onSample(1000L, 45f, 30f, -40f).triggered)
        assertFalse(m.onSample(2000L, 20f, 30f, -40f).triggered) // 回到基线，重新武装
        assertFalse(m.onSample(3000L, 45f, 30f, -40f).triggered) // 距上次报警仅 2 秒，仍在冷却期
    }

    @Test
    fun cooldownBlocksRetriggerWithoutRecovery() {
        val m = monitor(cooldownMs = 3000L)
        assertTrue(m.onSample(1000L, 45f, 30f, -40f).triggered)
        // 仍在越界且未重新武装
        assertFalse(m.onSample(2000L, 45f, 30f, -40f).triggered)
        assertFalse(m.onSample(3500L, 45f, 30f, -40f).triggered)
    }

    @Test
    fun thresholdIsCoercedToMinimum() {
        val m = MagneticBaselineMonitor()
        m.threshold = 0f
        assertTrue(m.threshold >= MagneticBaselineMonitor.MIN_THRESHOLD_UT)
    }

    @Test
    fun isExceededReflectsCurrentState() {
        val m = monitor()
        assertTrue(m.isExceeded(45f, 30f, -40f))
        assertFalse(m.isExceeded(20f, 30f, -40f))
    }

    @Test
    fun burstTracksMaxDeltaAndDuration() {
        val m = monitor()
        val start = m.onSample(1000L, 45f, 30f, -40f) // X 偏 25
        assertTrue(start.triggered)
        assertTrue(start.burstActive)
        assertEquals(25f, start.burstMaxDelta, 0.001f)
        assertEquals(0L, start.burstDurationMs)

        val mid = m.onSample(1200L, 60f, 30f, -40f) // 偏 40，刷新 max
        assertFalse(mid.triggered)
        assertTrue(mid.burstActive)
        assertEquals(40f, mid.burstMaxDelta, 0.001f)
        assertEquals(200L, mid.burstDurationMs)

        val smaller = m.onSample(1500L, 50f, 30f, -40f) // 偏 30，不覆盖 max
        assertEquals(40f, smaller.burstMaxDelta, 0.001f)
        assertEquals(500L, smaller.burstDurationMs)

        val end = m.onSample(2000L, 20f, 30f, -40f) // 回到基线
        assertTrue(end.burstEnded)
        assertFalse(end.burstActive)
        assertEquals(40f, end.burstMaxDelta, 0.001f)
        assertEquals(1000L, end.burstDurationMs)
    }

    @Test
    fun subsequentBurstStartsFreshStats() {
        val m = monitor(cooldownMs = 1000L)
        val b1 = m.onSample(1000L, 60f, 30f, -40f) // 偏 40
        assertEquals(40f, b1.burstMaxDelta, 0.001f)
        m.onSample(1500L, 20f, 30f, -40f) // burst 结束
        val b2 = m.onSample(3000L, 45f, 30f, -40f) // 新 burst，偏 25
        assertTrue(b2.triggered)
        assertEquals(25f, b2.burstMaxDelta, 0.001f)
        assertEquals(0L, b2.burstDurationMs)
    }
}
