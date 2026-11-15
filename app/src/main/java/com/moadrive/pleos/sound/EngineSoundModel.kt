package com.moadrive.pleos.sound

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

internal data class EngineSoundFrame(
    val rpm: Double,
    val gear: Int,
    val load: Double,
    val shiftCut: Double,
    val braking: Double,
)

/** Deterministic engine motion driven only by the PCM clock and vehicle speed. */
internal class EngineSoundModel {
    var state = idleFrame()
        private set
    private var initialized = false
    private var smoothSpeed = 0.0
    private var acceleration = 0.0
    private var shiftRemaining = 0.0
    private var shiftCooldown = 0.0
    private var upshifting = false

    fun step(speedKmh: Double, gear: String, seconds: Double): EngineSoundFrame {
        if (!seconds.isFinite() || seconds <= 0.0) return state
        val explicitGear = gear.toIntOrNull()?.takeIf { it in 1..9 }
        if (!speedKmh.isFinite() || speedKmh !in 0.0..360.0 ||
            (gear !in SELECTORS && explicitGear == null)) return reset()
        if (gear == "P" || gear == "N") return reset()

        // Bound a delayed caller's step without permitting a large jump in pitch or torque.
        val dt = seconds.coerceAtMost(.05)
        val targetSpeed = if (gear == "R") speedKmh.coerceAtMost(30.0) else speedKmh
        if (!initialized) {
            smoothSpeed = targetSpeed
            initialized = true
        }
        val oldSpeed = smoothSpeed
        smoothSpeed = follow(smoothSpeed, targetSpeed, dt, .12)
        val observedAcceleration = ((smoothSpeed - oldSpeed) / (3.6 * dt)).coerceIn(-8.0, 8.0)
        acceleration = follow(acceleration, observedAcceleration, dt, .10)
        shiftCooldown = (shiftCooldown - dt).coerceAtLeast(0.0)
        shiftRemaining = (shiftRemaining - dt).coerceAtLeast(0.0)

        val oldGear = state.gear
        val nextGear = when {
            gear == "R" -> -1
            explicitGear != null -> explicitGear
            oldGear !in 1..6 -> 1 + UPSHIFT_SPEEDS.count { smoothSpeed >= it }
            shiftCooldown > 0.0 -> oldGear
            oldGear < 6 && smoothSpeed >= UPSHIFT_SPEEDS[oldGear - 1] -> oldGear + 1
            oldGear > 1 && smoothSpeed < DOWNSHIFT_SPEEDS[oldGear - 2] -> oldGear - 1
            else -> oldGear
        }
        if (nextGear != oldGear) {
            if (oldGear > 0 && nextGear > 0) {
                shiftRemaining = SHIFT_SECONDS
                shiftCooldown = .32
                upshifting = nextGear > oldGear
            } else {
                shiftRemaining = 0.0
                shiftCooldown = 0.0
            }
        }

        val shiftEnvelope = if (shiftRemaining > 0.0)
            sin(PI * (1.0 - shiftRemaining / SHIFT_SECONDS)).coerceIn(0.0, 1.0) else 0.0
        val shiftCut = if (upshifting) shiftEnvelope * .85 else shiftEnvelope * .12
        val brakingTarget = (-acceleration / 2.0).coerceIn(0.0, 1.0)
        val braking = follow(state.braking, brakingTarget, dt, if (brakingTarget > state.braking) .08 else .20)
        val loadTarget = (.20 + smoothSpeed.coerceAtMost(120.0) / 1_200.0 +
            (acceleration / 3.0).coerceIn(0.0, 1.0) * .70 - braking * .12).coerceIn(.08, 1.0)
        val load = follow(state.load, loadTarget, dt, if (loadTarget > state.load) .06 else .25)
        val targetRpm = if (nextGear == -1) {
            (IDLE_RPM + smoothSpeed * 70.0).coerceAtMost(3_000.0)
        } else {
            // The brief downshift blip joins the higher revs of the lower gear.
            val blip = if (!upshifting) shiftEnvelope * 180.0 else 0.0
            (IDLE_RPM + smoothSpeed * RPM_PER_KMH[nextGear - 1] + blip).coerceAtMost(5_600.0)
        }
        val rpmResponse = when {
            shiftRemaining > 0.0 && upshifting -> .045
            targetRpm < state.rpm -> .10
            else -> .12
        }
        val rpm = follow(state.rpm, targetRpm, dt, rpmResponse).coerceIn(IDLE_RPM, 5_600.0)
        state = EngineSoundFrame(rpm, nextGear, load.coerceIn(0.0, 1.0), shiftCut, braking.coerceIn(0.0, 1.0))
        return state
    }

    private fun reset(): EngineSoundFrame {
        initialized = false
        smoothSpeed = 0.0
        acceleration = 0.0
        shiftRemaining = 0.0
        shiftCooldown = 0.0
        upshifting = false
        state = idleFrame()
        return state
    }

    private fun follow(current: Double, target: Double, dt: Double, responseSeconds: Double): Double =
        current + (target - current) * (1.0 - exp(-dt / responseSeconds))

    private companion object {
        const val IDLE_RPM = 900.0
        const val SHIFT_SECONDS = .18
        val SELECTORS = setOf("P", "N", "R", "D")
        val UPSHIFT_SPEEDS = doubleArrayOf(36.0, 55.0, 80.0, 110.0, 145.0)
        val DOWNSHIFT_SPEEDS = doubleArrayOf(28.0, 43.0, 65.0, 90.0, 120.0)
        val RPM_PER_KMH = doubleArrayOf(130.0, 77.0, 52.0, 35.0, 24.0, 16.0, 13.0, 11.0, 9.0)
        fun idleFrame() = EngineSoundFrame(IDLE_RPM, 0, .18, 0.0, 0.0)
    }
}
