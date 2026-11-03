package com.youyou.gaussmeter

import com.youyou.gaussmeter.data.CsvRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class CsvRecorderTest {

    @Test
    fun ignoresSamplesWhenNotRecording() {
        val r = CsvRecorder()
        r.onSample(1000L, 1f, 2f, 3f)
        assertEquals(0, r.rowCount)
    }

    @Test
    fun rateLimitsToConfiguredSampleRate() {
        val r = CsvRecorder().apply {
            sampleRateHz = 10 // 100 ms 间隔
            start()
        }
        // 传感器以约 100 Hz 回调
        var t = 0L
        repeat(100) {
            r.onSample(t, 20f, 30f, 40f)
            t += 10
        }
        // 1000 ms / 100 ms = 10 行（首行 + 9 个间隔）
        assertEquals(10, r.rowCount)
    }

    @Test
    fun higherSampleRateProducesMoreRows() {
        val r = CsvRecorder().apply {
            sampleRateHz = 50 // 20 ms 间隔
            start()
        }
        var t = 0L
        repeat(100) {
            r.onSample(t, 20f, 30f, 40f)
            t += 10
        }
        assertEquals(50, r.rowCount)
    }

    @Test
    fun stopPreventsFurtherSamples() {
        val r = CsvRecorder().apply {
            sampleRateHz = 10
            start()
        }
        r.onSample(0L, 1f, 1f, 1f)
        r.stop()
        r.onSample(1000L, 1f, 1f, 1f)
        assertEquals(1, r.rowCount)
        assertFalse(r.isRecording)
    }

    @Test
    fun writeCsvIncludesHeaderAndRows() {
        val r = CsvRecorder().apply {
            sampleRateHz = 10
            start()
        }
        r.onSample(1_700_000_000_000L, 20f, 30f, 40f)
        r.onSample(1_700_000_000_500L, 21f, 30f, 40f)
        r.stop()

        val out = StringWriter()
        val rows = r.writeCsv(out)
        val lines = out.toString().trim().lines()

        assertEquals(2, rows)
        assertEquals(CsvRecorder.HEADER, lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1].startsWith("1700000000000,"))
        // 20² + 30² + 40² = 2900 -> √2900 ≈ 53.852
        assertTrue(lines[1].endsWith(",53.852"))
    }

    @Test
    fun clearResetsRows() {
        val r = CsvRecorder().apply {
            sampleRateHz = 10
            start()
        }
        r.onSample(0L, 1f, 1f, 1f)
        r.stop()
        assertEquals(1, r.clear())
        assertEquals(0, r.rowCount)
    }
}
