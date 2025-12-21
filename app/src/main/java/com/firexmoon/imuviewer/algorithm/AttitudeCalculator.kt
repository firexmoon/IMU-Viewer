package com.firexmoon.imuviewer.algorithm

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// 姿态解算器，使用改进的互补滤波算法
class AttitudeCalculator {
    // 滤波系数，0-1之间，值越大越信任陀螺仪
    // 调整为0.9，更平衡陀螺仪和加速度计的信任度
    private val alpha = 0.9f
    
    // 当前姿态角（弧度）
    private var roll = 0f
    private var pitch = 0f
    private var yaw = 0f
    
    // 上次更新时间
    private var lastTime = 0L
    
    // 使用加速度计计算姿态角
    private fun calculateAttitudeFromAccelerometer(ax: Float, ay: Float, az: Float): Pair<Float, Float> {
        // 标准姿态角计算公式
        // 计算横滚角（Roll）：绕Y轴旋转
        val rollAcc = atan2(-ax.toDouble(), sqrt(ay * ay + az * az.toDouble())).toFloat()
        
        // 计算俯仰角（Pitch）：绕X轴旋转
        val pitchAcc = atan2(ay.toDouble(), az.toDouble()).toFloat()
        
        return Pair(rollAcc, pitchAcc)
    }
    
    // 使用加速度计和磁力计计算完整姿态角
    private fun calculateFullAttitude(ax: Float, ay: Float, az: Float, mx: Float, my: Float, mz: Float): Triple<Float, Float, Float> {
        // 1. 计算加速度计姿态
        val (rollAcc, pitchAcc) = calculateAttitudeFromAccelerometer(ax, ay, az)
        
        // 2. 归一化加速度计数据
        val accNorm = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        if (accNorm == 0f) return Triple(rollAcc, pitchAcc, yaw)

        // 3. 旋转磁力计数据以补偿倾斜
        val cosRoll = cos(rollAcc.toDouble()).toFloat()
        val sinRoll = sin(rollAcc.toDouble()).toFloat()
        val cosPitch = cos(pitchAcc.toDouble()).toFloat()
        val sinPitch = sin(pitchAcc.toDouble()).toFloat()
        
        // 倾斜补偿后的磁力计X分量
        val mxComp = mx * cosPitch + mz * sinPitch
        // 倾斜补偿后的磁力计Y分量
        val myComp = mx * sinRoll * sinPitch + my * cosRoll - mz * sinRoll * cosPitch
        
        // 4. 计算偏航角
        val yawAcc = atan2(-myComp.toDouble(), mxComp.toDouble()).toFloat()
        
        return Triple(rollAcc, pitchAcc, yawAcc)
    }
    
    // 更新姿态
    fun updateAttitude(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float, mx: Float, my: Float, mz: Float, timestamp: Long): Triple<Float, Float, Float> {
        if (lastTime == 0L) {
            lastTime = timestamp
            // 初始姿态从加速度计和磁力计获取
            val (rollAcc, pitchAcc, yawAcc) = calculateFullAttitude(ax, ay, az, mx, my, mz)
            roll = rollAcc
            pitch = pitchAcc
            yaw = yawAcc
            return Triple(Math.toDegrees(roll.toDouble()).toFloat(), 
                         Math.toDegrees(pitch.toDouble()).toFloat(), 
                         Math.toDegrees(yaw.toDouble()).toFloat())
        }
        
        // 计算时间差（秒）
        val dt = (timestamp - lastTime) / 1000f
        lastTime = timestamp
        
        // 限制dt范围，避免传感器延迟导致的大跳变
        val limitedDt = dt.coerceIn(0.001f, 0.1f)
        
        // 1. 陀螺仪积分更新姿态（预测）
        val predictedRoll = roll + gy * limitedDt
        val predictedPitch = pitch + gx * limitedDt
        val predictedYaw = yaw + gz * limitedDt
        
        // 2. 从加速度计和磁力计获取校正姿态
        val (rollAcc, pitchAcc, yawAcc) = calculateFullAttitude(ax, ay, az, mx, my, mz)
        
        // 3. 互补滤波融合
        // 对roll和pitch使用互补滤波
        roll = alpha * predictedRoll + (1 - alpha) * rollAcc
        pitch = alpha * predictedPitch + (1 - alpha) * pitchAcc
        
        // 对yaw使用更低的alpha值，增加磁力计的信任度
        val yawAlpha = 0.7f
        yaw = yawAlpha * predictedYaw + (1 - yawAlpha) * yawAcc
        
        // 4. 归一化角度到[-π, π]范围
        roll = normalizeAngle(roll)
        pitch = normalizeAngle(pitch)
        yaw = normalizeAngle(yaw)
        
        // 返回角度值（转换为度）
        return Triple(
            Math.toDegrees(roll.toDouble()).toFloat(), 
            Math.toDegrees(pitch.toDouble()).toFloat(), 
            Math.toDegrees(yaw.toDouble()).toFloat()
        )
    }
    
    // 归一化角度到 [-π, π] 范围
    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle
        while (normalized > Math.PI) normalized -= (2 * Math.PI).toFloat()
        while (normalized < -Math.PI) normalized += (2 * Math.PI).toFloat()
        return normalized
    }
}
