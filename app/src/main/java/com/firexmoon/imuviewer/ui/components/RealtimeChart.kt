package com.firexmoon.imuviewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.firexmoon.imuviewer.data.SensorData

// 实时曲线图组件
@Composable
fun RealtimeChart(
    data: List<SensorData>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF4CAF50), // 亮绿色曲线
    gridColor: Color = Color.White,
    backgroundColor: Color = Color.Black
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .height(300.dp)
    ) {
        val width = size.width
        val height = size.height
        
        // 绘制背景
        drawRect(color = backgroundColor)
        
        // 绘制网格线
        val gridLines = 5
        for (i in 0..gridLines) {
            // 水平线
            val y = (height / gridLines) * i
            drawLine(
                color = gridColor.copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            
            // 垂直线
            val x = (width / gridLines) * i
            drawLine(
                color = gridColor.copy(alpha = 0.2f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
        }
        
        if (data.isEmpty()) return@Canvas
        
        // 计算时间范围（最近25秒）
        val currentTime = System.currentTimeMillis()
        val twentyFiveSecondsAgo = currentTime - 25000 // 25秒前
        val fixedTimeRange = 25000L // 固定25秒时间范围
        
        // 过滤出最近25秒的数据
        val recentData = data.filter { it.timestamp >= twentyFiveSecondsAgo }
        
        if (recentData.isEmpty()) return@Canvas
        
        // 计算数据范围
        val xValues = recentData.map { it.x }
        val yValues = recentData.map { it.y }
        val zValues = recentData.map { it.z }
        val allValues = xValues + yValues + zValues
        val minValue = allValues.minOrNull() ?: 0f
        val maxValue = allValues.maxOrNull() ?: 1f
        
        // 确保数据范围包含0
        val adjustedMinValue = if (minValue > 0) 0f else minValue
        val adjustedMaxValue = if (maxValue < 0) 0f else maxValue
        val adjustedValueRange = if (adjustedMaxValue == adjustedMinValue) 1f else (adjustedMaxValue - adjustedMinValue)
        
        val lineWidth = 2f // 线宽
        
        // X轴曲线
        val xPath = Path()
        
        // 绘制所有点，使用固定25秒时间范围
        for (i in recentData.indices) {
            // 根据时间戳计算x坐标，使用固定25秒时间范围，最右侧是当前时刻
            val timeProgress = (recentData[i].timestamp - twentyFiveSecondsAgo).toFloat() / fixedTimeRange
            val x = timeProgress * width
            val y = height - ((recentData[i].x - adjustedMinValue) / adjustedValueRange) * height
            
            if (i == 0) {
                xPath.moveTo(x, y)
            } else {
                xPath.lineTo(x, y)
            }
        }
        
        // 如果只有一个点，绘制一条水平线覆盖整个宽度
        if (recentData.size == 1) {
            val point = recentData.first()
            val y = height - ((point.x - adjustedMinValue) / adjustedValueRange) * height
            xPath.moveTo(0f, y)
            xPath.lineTo(width, y)
        }
        
        drawPath(
            path = xPath,
            color = lineColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = lineWidth)
        )
        
        // Y轴曲线
        val yPath = Path()
        
        // 绘制所有点，使用固定25秒时间范围
        for (i in recentData.indices) {
            // 根据时间戳计算x坐标，使用固定25秒时间范围，最右侧是当前时刻
            val timeProgress = (recentData[i].timestamp - twentyFiveSecondsAgo).toFloat() / fixedTimeRange
            val x = timeProgress * width
            val y = height - ((recentData[i].y - adjustedMinValue) / adjustedValueRange) * height
            
            if (i == 0) {
                yPath.moveTo(x, y)
            } else {
                yPath.lineTo(x, y)
            }
        }
        
        // 如果只有一个点，绘制一条水平线覆盖整个宽度
        if (recentData.size == 1) {
            val point = recentData.first()
            val y = height - ((point.y - adjustedMinValue) / adjustedValueRange) * height
            yPath.moveTo(0f, y)
            yPath.lineTo(width, y)
        }
        
        drawPath(
            path = yPath,
            color = Color(0xFF2196F3),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = lineWidth)
        ) // 蓝色
        
        // Z轴曲线
        val zPath = Path()
        
        // 绘制所有点，使用固定25秒时间范围
        for (i in recentData.indices) {
            // 根据时间戳计算x坐标，使用固定25秒时间范围，最右侧是当前时刻
            val timeProgress = (recentData[i].timestamp - twentyFiveSecondsAgo).toFloat() / fixedTimeRange
            val x = timeProgress * width
            val y = height - ((recentData[i].z - adjustedMinValue) / adjustedValueRange) * height
            
            if (i == 0) {
                zPath.moveTo(x, y)
            } else {
                zPath.lineTo(x, y)
            }
        }
        
        // 如果只有一个点，绘制一条水平线覆盖整个宽度
        if (recentData.size == 1) {
            val point = recentData.first()
            val y = height - ((point.z - adjustedMinValue) / adjustedValueRange) * height
            zPath.moveTo(0f, y)
            zPath.lineTo(width, y)
        }
        
        drawPath(
            path = zPath,
            color = Color(0xFFFF9800),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = lineWidth)
        ) // 橙色
    }
}
