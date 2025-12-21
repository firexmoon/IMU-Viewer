package com.firexmoon.imuviewer.algorithm

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AttitudeCalculator {
    private val alpha = 0.98f
    private val magnetometerQualityThreshold = 0.8f
    
    private var roll = 0f
    private var pitch = 0f
    private var yaw = 0f
    private var lastTime = 0L
    
    private fun calculateAttitudeFromAccelerometer(ax: Float, ay: Float, az: Float): Pair<Float, Float> {
        val accNorm = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        if (accNorm == 0f) return Pair(0f, 0f)
        
        val axNorm = ax / accNorm
        val ayNorm = ay / accNorm
        val azNorm = az / accNorm
        
        val rollAcc = atan2(ayNorm.toDouble(), azNorm.toDouble()).toFloat()
        val pitchAcc = atan2(axNorm.toDouble(), sqrt(ayNorm * ayNorm + azNorm * azNorm).toDouble()).toFloat()
        
        return Pair(rollAcc, pitchAcc)
    }
    
    private fun calculateFullAttitude(ax: Float, ay: Float, az: Float, mx: Float, my: Float, mz: Float): Triple<Float, Float, Float> {
        val (rollAcc, pitchAcc) = calculateAttitudeFromAccelerometer(ax, ay, az)
        
        val magNorm = sqrt((mx * mx + my * my + mz * mz).toDouble()).toFloat()
        if (magNorm == 0f) return Triple(rollAcc, pitchAcc, yaw)
        
        val expectedMagFieldStrength = 50f
        val magFieldRatio = magNorm / expectedMagFieldStrength
        
        val mxNorm = mx / magNorm
        val myNorm = my / magNorm
        val mzNorm = mz / magNorm

        val cosRoll = cos(rollAcc.toDouble()).toFloat()
        val sinRoll = sin(rollAcc.toDouble()).toFloat()
        val cosPitch = cos(pitchAcc.toDouble()).toFloat()
        val sinPitch = sin(pitchAcc.toDouble()).toFloat()
        
        val myRoll = myNorm * cosRoll - mzNorm * sinRoll
        val mzRoll = myNorm * sinRoll + mzNorm * cosRoll
        
        val mxComp = mxNorm * cosPitch + mzRoll * sinPitch
        val myComp = myRoll
        val mzComp = -mxNorm * sinPitch + mzRoll * cosPitch
        
        val yawAcc = if (magFieldRatio < magnetometerQualityThreshold || magFieldRatio > (2f - magnetometerQualityThreshold)) {
            yaw
        } else {
            atan2(myComp.toDouble(), mxComp.toDouble()).toFloat()
        }
        
        return Triple(rollAcc, pitchAcc, yawAcc)
    }
    
    fun updateAttitude(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float, mx: Float, my: Float, mz: Float, timestamp: Long): Triple<Float, Float, Float> {
        if (lastTime == 0L) {
            lastTime = timestamp
            val (rollAcc, pitchAcc, yawAcc) = calculateFullAttitude(ax, ay, az, mx, my, mz)
            roll = rollAcc
            pitch = pitchAcc
            yaw = yawAcc
            return Triple(Math.toDegrees(roll.toDouble()).toFloat(), 
                         Math.toDegrees(pitch.toDouble()).toFloat(), 
                         Math.toDegrees(yaw.toDouble()).toFloat())
        }
        
        val dt = (timestamp - lastTime) / 1000f
        lastTime = timestamp
        
        val limitedDt = dt.coerceIn(0.001f, 0.1f)
        
        val predictedRoll = roll + gx * limitedDt
        val predictedPitch = pitch + gy * limitedDt
        val predictedYaw = yaw + gz * limitedDt
        
        val (rollAcc, pitchAcc, yawAcc) = calculateFullAttitude(ax, ay, az, mx, my, mz)
        
        roll = alpha * predictedRoll + (1 - alpha) * rollAcc
        pitch = alpha * predictedPitch + (1 - alpha) * pitchAcc
        yaw = alpha * predictedYaw + (1 - alpha) * yawAcc
        
        roll = normalizeAngle(roll)
        pitch = normalizeAngle(pitch)
        yaw = normalizeAngle(yaw)
        
        return Triple(
            Math.toDegrees(roll.toDouble()).toFloat(), 
            Math.toDegrees(pitch.toDouble()).toFloat(), 
            Math.toDegrees(yaw.toDouble()).toFloat()
        )
    }
    
    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle
        while (normalized > Math.PI) normalized -= (2 * Math.PI).toFloat()
        while (normalized < -Math.PI) normalized += (2 * Math.PI).toFloat()
        return normalized
    }
}
