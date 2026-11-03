package com.youyou.gaussmeter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.youyou.gaussmeter.data.SensorData

private val X_COLOR = Color(0xFF4CAF50) // 绿
private val Y_COLOR = Color(0xFF2196F3) // 蓝
private val Z_COLOR = Color(0xFFFF9800) // 橙

/**
 * 实时曲线图：绘制 X/Y/Z 三轴磁场。
 * 顶部有图例，防止读数稳定时三条线叠在边缘无法分辨。
 */
@Composable
fun RealtimeChart(
    data: List<SensorData>,
    modifier: Modifier = Modifier,
    windowSeconds: Int = 25,
    gridColor: Color = Color.White,
    backgroundColor: Color = Color.Black
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot("X", X_COLOR)
            LegendDot("Y", Y_COLOR)
            LegendDot("Z", Z_COLOR)
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            val width = size.width
            val height = size.height
            // 顶/底各留一点内边距，避免曲线落到边框上被裁掉
            val padY = 4f
            val innerTop = padY
            val innerBottom = height - padY
            val innerHeight = innerBottom - innerTop

            drawRect(color = backgroundColor)

            // 网格
            val gridLines = 5
            for (i in 0..gridLines) {
                val y = (height / gridLines) * i
                drawLine(
                    color = gridColor.copy(alpha = 0.2f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
                val x = (width / gridLines) * i
                drawLine(
                    color = gridColor.copy(alpha = 0.2f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
            }

            if (data.isEmpty()) return@Canvas

            val now = System.currentTimeMillis()
            val timeRange = windowSeconds * 1000L
            val windowStart = now - timeRange
            val recent = data.filter { it.timestamp >= windowStart }
            if (recent.isEmpty()) return@Canvas

            val xs = recent.map { it.x }
            val ys = recent.map { it.y }
            val zs = recent.map { it.z }
            val all = xs + ys + zs
            val vMin = all.minOrNull() ?: 0f
            val vMax = all.maxOrNull() ?: 1f
            val adjMin = if (vMin > 0) 0f else vMin
            val adjMax = if (vMax < 0) 0f else vMax
            // 读数稳定时三轴差值可能很小，加一点余量避免除 0 和贴边
            val span = if (adjMax - adjMin < 0.5f) 0.5f else adjMax - adjMin
            val lineWidth = 2.5f

            fun drawSeries(valueOf: (SensorData) -> Float, color: Color) {
                val p = Path()
                recent.forEachIndexed { i, s ->
                    val tx = (s.timestamp - windowStart).toFloat() / timeRange
                    val x = tx * width
                    val y = innerBottom - ((valueOf(s) - adjMin) / span) * innerHeight
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                if (recent.size == 1) {
                    val only = recent.first()
                    val y = innerBottom - ((valueOf(only) - adjMin) / span) * innerHeight
                    p.moveTo(0f, y); p.lineTo(width, y)
                }
                drawPath(path = p, color = color, style = Stroke(width = lineWidth))
            }

            drawSeries({ it.x }, X_COLOR)
            drawSeries({ it.y }, Y_COLOR)
            drawSeries({ it.z }, Z_COLOR)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}
