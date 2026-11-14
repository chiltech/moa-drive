package com.moagauge.pleos.trips

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TripRecorderEngineTest {
    private val origin = Instant.parse("2026-11-04T00:00:00Z").toEpochMilli()
    private var nextId = 0
    private fun engine(zone: String = "Asia/Seoul") = TripRecorderEngine(ZoneId.of(zone), idProvider = { "trip-${++nextId}" })
    private fun sample(
        seconds: Long,
        speed: Double? = 10.0,
        gear: String? = "D",
        energy: Double? = 50_000.0 - seconds * 10,
        soc: Int? = 80,
        ignition: Boolean? = null,
        wall: Long = origin + seconds * 1_000,
    ) = TripSample(wall, seconds * 1_000, speed, gear, energy, soc, ignition)

    private fun start(engine: TripRecorderEngine, gear: String = "D", speed: Double = 10.0) {
        engine.accept(sample(0, speed, gear))
        engine.accept(sample(1, speed, gear))
        engine.accept(sample(2, speed, gear))
    }

    @Test fun startsAfterTwoContinuousSecondsAndIncludesCandidateDistance() {
        val engine = engine()
        engine.accept(sample(0))
        engine.accept(sample(1))
        assertNull(engine.current)
        assertTrue(engine.accept(sample(2)).isEmpty())
        val trip = requireNotNull(engine.current)
        assertEquals(origin, trip.startedAtMs)
        assertEquals(2_000L, trip.durationMs)
        assertEquals(20.0, trip.distanceMeters, 0.0001)
        assertEquals(20.0, requireNotNull(trip.consumedWh), 0.0001)
        assertEquals(TripStatus.RECORDING, trip.status)
        assertTrue(trip.isActive)
    }

    @Test fun invalidCandidateBreaksContinuityAndParkNeverStartsTrip() {
        val engine = engine()
        engine.accept(sample(0))
        engine.accept(sample(1, speed = 0.5))
        engine.accept(sample(2))
        engine.accept(sample(3))
        assertNull(engine.current)
        engine.accept(sample(4))
        assertEquals(origin + 2_000, engine.current?.startedAtMs)
        val parked = engine()
        (0L..4).forEach { parked.accept(sample(it, gear = "P")) }
        assertNull(parked.current)
    }

    @Test fun reverseAndManualGearsMeasureAbsoluteSpeed() {
        for (gear in listOf("R", "1", "9")) {
            val engine = engine()
            start(engine, gear, -10.0)
            assertEquals("gear $gear", 20.0, requireNotNull(engine.current).distanceMeters, 0.0001)
        }
    }

    @Test fun varyingSpeedUsesTrapezoidDistance() {
        val engine = engine()
        engine.accept(sample(0, speed = 1.0))
        engine.accept(sample(1, speed = 3.0))
        engine.accept(sample(2, speed = 5.0))
        assertEquals(6.0, requireNotNull(engine.current).distanceMeters, 0.0001)
    }

    @Test fun driveAndNeutralStopsRemainOneTripAndIncludeWaitingTime() {
        val engine = engine()
        start(engine)
        (3L..70).forEach {
            assertTrue(engine.accept(sample(it, speed = 0.0, gear = if (it < 35) "D" else "N")).isEmpty())
        }
        assertEquals(70_000L, engine.current?.durationMs)
        assertEquals(TripStatus.RECORDING, engine.current?.status)
        assertEquals(25.0, requireNotNull(engine.current).distanceMeters, 0.0001)
    }

    @Test fun parkingEndsOnceAtFirstParkSampleAndExcludesGraceEnergy() {
        val engine = engine()
        start(engine)
        engine.accept(sample(3, speed = 0.0, gear = "P", energy = 49_970.0, soc = 79))
        assertEquals(TripStatus.PARKING, engine.current?.status)
        assertEquals(3_000L, engine.current?.durationMs)
        (4L..32).forEach { assertTrue(engine.accept(sample(it, 0.0, "P", energy = 60_000.0)).isEmpty()) }
        val ended = engine.accept(sample(33, 0.0, "P", energy = 60_000.0)).single()
        assertEquals(TripStatus.COMPLETED, ended.status)
        assertEquals(origin + 3_000, ended.endedAtMs)
        assertEquals(3_000L, ended.durationMs)
        assertEquals(25.0, ended.distanceMeters, 0.0001)
        assertEquals(30.0, requireNotNull(ended.consumedWh), 0.0001)
        assertEquals(79, ended.batteryEndPercent)
        assertFalse(ended.isActive)
        assertNull(engine.current)
        assertTrue(engine.accept(sample(34, 0.0, "P")).isEmpty())
        assertNull(engine.interrupt())
    }

    @Test fun briefParkingResumesSameTripIncludingElapsedWait() {
        val engine = engine()
        start(engine)
        val id = engine.current?.id
        (3L..10).forEach { engine.accept(sample(it, 0.0, "P")) }
        engine.accept(sample(11, 10.0, "D"))
        val trip = requireNotNull(engine.current)
        assertEquals(id, trip.id)
        assertEquals(TripStatus.RECORDING, trip.status)
        assertEquals(11_000L, trip.durationMs)
        assertEquals(30.0, trip.distanceMeters, 0.0001)
        assertEquals(110.0, requireNotNull(trip.consumedWh), 0.0001)
    }

    @Test fun gapIsNotInterpolatedAndSuppressesEfficiency() {
        val engine = engine()
        start(engine)
        engine.accept(sample(6))
        engine.accept(sample(7))
        val trip = requireNotNull(engine.current)
        assertEquals(30.0, trip.distanceMeters, 0.0001)
        assertEquals(7_000L, trip.durationMs)
        assertTrue(trip.hasDataGap)
        assertNull(trip.averageEfficiencyKmPerKwh)
    }

    @Test fun invalidSpeedOrGearDoesNotBridgeDistance() {
        for (bad in listOf(sample(3, speed = null), sample(3, speed = Double.NaN), sample(3, speed = 101.0), sample(3, gear = null))) {
            val engine = engine()
            start(engine)
            engine.accept(bad)
            engine.accept(sample(4))
            engine.accept(sample(5))
            assertEquals(30.0, requireNotNull(engine.current).distanceMeters, 0.0001)
            assertTrue(requireNotNull(engine.current).hasDataGap)
        }
    }

    @Test fun sixtySecondsWithoutValidTelemetryInterruptsAtLastTrustedPoint() {
        val engine = engine()
        start(engine)
        (3L..61).forEach { assertTrue(engine.accept(sample(it, speed = null, gear = null)).isEmpty()) }
        val ended = engine.accept(sample(62, speed = null, gear = null)).single()
        assertEquals(TripStatus.INTERRUPTED, ended.status)
        assertEquals(origin + 2_000, ended.endedAtMs)
        assertEquals(2_000L, ended.durationMs)
        assertEquals(20.0, ended.distanceMeters, 0.0001)
        assertTrue(ended.hasDataGap)
        assertNull(engine.current)
        assertTrue(engine.accept(sample(63, speed = null, gear = null)).isEmpty())
    }

    @Test fun silentSixtySecondGapClosesOldTripBeforeFreshCandidate() {
        val engine = engine()
        start(engine)
        val oldId = engine.current?.id
        val ended = engine.accept(sample(62)).single()
        assertEquals(oldId, ended.id)
        assertEquals(TripStatus.INTERRUPTED, ended.status)
        assertEquals(2_000L, ended.durationMs)
        engine.accept(sample(63))
        engine.accept(sample(64))
        assertNotEquals(oldId, engine.current?.id)
        assertEquals(origin + 62_000, engine.current?.startedAtMs)
    }

    @Test fun missingEnergyIsUnknownWithoutLosingDistanceOrSoc() {
        val engine = engine()
        start(engine)
        engine.accept(sample(3, energy = null, soc = -1))
        engine.accept(sample(4, soc = 79))
        val trip = requireNotNull(engine.current)
        assertEquals(40.0, trip.distanceMeters, 0.0001)
        assertNull(trip.consumedWh)
        assertEquals(80, trip.batteryStartPercent)
        assertEquals(79, trip.batteryEndPercent)
    }

    @Test fun regenerationReducesConsumptionWithoutClamping() {
        val engine = engine()
        start(engine)
        engine.accept(sample(3, energy = 49_990.0))
        assertEquals(10.0, requireNotNull(engine.current?.consumedWh), 0.0001)
        engine.accept(sample(4, energy = 50_010.0))
        assertNull(engine.current?.consumedWh)
        engine.accept(sample(5, energy = 49_940.0))
        assertEquals(60.0, requireNotNull(engine.current?.consumedWh), 0.0001)
    }

    @Test fun energyResetAndStationaryChargingStayUnknown() {
        for (bad in listOf(sample(3, energy = 60_000.0), sample(3, energy = Double.NaN), sample(3, energy = -10.0))) {
            val engine = engine()
            start(engine)
            engine.accept(bad)
            engine.accept(sample(4))
            assertEquals(40.0, requireNotNull(engine.current).distanceMeters, 0.0001)
            assertNull(requireNotNull(engine.current).consumedWh)
        }
        val engine = engine()
        start(engine)
        engine.accept(sample(3, 0.0, "N", 49_970.0))
        engine.accept(sample(4, 0.0, "N", 49_980.0))
        assertNull(requireNotNull(engine.current).consumedWh)
    }

    @Test fun downwardEnergyResetMakesConsumptionUnknownPermanently() {
        val engine = engine()
        (0L..2).forEach { engine.accept(sample(it, energy = 50_000.0 - it * 100)) }
        assertEquals(200.0, requireNotNull(engine.current?.consumedWh), 0.0001)
        engine.accept(sample(3, energy = 1_000.0))
        assertEquals(30.0, requireNotNull(engine.current).distanceMeters, 0.0001)
        assertNull(requireNotNull(engine.current).consumedWh)
        engine.accept(sample(4, energy = 900.0))
        assertNull(requireNotNull(engine.current).consumedWh)
        assertNull(requireNotNull(engine.interrupt()).averageEfficiencyKmPerKwh)
    }

    @Test fun missingOrInvalidEndingSocDoesNotReuseAnEarlierReading() {
        for (endingSoc in listOf(null, -1, 101)) {
            val engine = engine()
            start(engine)
            engine.accept(sample(3, soc = endingSoc))
            val ended = requireNotNull(engine.interrupt())
            assertEquals(80, ended.batteryStartPercent)
            assertNull(ended.batteryEndPercent)
        }
    }

    @Test fun parkEndpointWithMissingSocStaysUnknownEvenIfGraceSamplesHaveSoc() {
        val engine = engine()
        start(engine)
        engine.accept(sample(3, 0.0, "P", soc = null))
        (4L..32).forEach { engine.accept(sample(it, 0.0, "P", soc = 79)) }
        val ended = engine.accept(sample(33, 0.0, "P", soc = 79)).single()
        assertEquals(origin + 3_000, ended.endedAtMs)
        assertNull(ended.batteryEndPercent)
    }

    @Test fun observedIgnitionOnToOffEndsButInitialOffDoesNotBlockStart() {
        val engine = engine()
        (0L..2).forEach { engine.accept(sample(it, ignition = false)) }
        assertNotNull(engine.current)
        engine.accept(sample(3, ignition = true))
        engine.accept(sample(4, ignition = null))
        val ended = engine.accept(sample(5, ignition = false)).single()
        assertEquals(TripStatus.COMPLETED, ended.status)
        assertEquals(5_000L, ended.durationMs)
        assertNull(engine.current)
    }

    @Test fun slowStationaryChargingInvalidatesEnergyEvenBelowPerSampleNoiseThreshold() {
        val engine = engine()
        start(engine)
        engine.accept(sample(3, 0.0, "N", 49_970.0))
        engine.accept(sample(4, 0.0, "N", 49_973.0))
        engine.accept(sample(5, 0.0, "N", 49_976.0))
        assertNull(requireNotNull(engine.current).consumedWh)
        engine.accept(sample(6, energy = 49_940.0))
        assertNull(requireNotNull(engine.current).consumedWh)
    }

    @Test fun clockChangesDoNotChangeDurationOrStartDateAcrossMidnight() {
        val engine = engine()
        val beforeMidnight = Instant.parse("2026-11-04T14:59:59Z").toEpochMilli()
        engine.accept(sample(0, wall = beforeMidnight))
        engine.accept(sample(1, wall = beforeMidnight + 1_000))
        engine.accept(sample(2, wall = beforeMidnight + 86_400_000))
        engine.accept(sample(3, wall = beforeMidnight - 86_400_000))
        val ended = requireNotNull(engine.interrupt())
        assertEquals(3_000L, ended.durationMs)
        assertEquals(beforeMidnight + 3_000, ended.endedAtMs)
        assertEquals("2026-11-04", ended.startDate)
        assertEquals("Asia/Seoul", ended.zoneId)
    }

    @Test fun eachDepartureCapturesCurrentTimezoneWithoutChangingAnExistingTrip() {
        var currentZone = ZoneId.of("Asia/Seoul")
        val engine = TripRecorderEngine(
            zoneId = ZoneId.of("Asia/Seoul"),
            idProvider = { "trip-${++nextId}" },
            zoneProvider = { currentZone },
        )
        engine.accept(sample(0))
        currentZone = ZoneId.of("America/Los_Angeles")
        engine.accept(sample(1))
        engine.accept(sample(2))
        val first = requireNotNull(engine.interrupt())
        assertEquals("2026-11-04", first.startDate)
        assertEquals("Asia/Seoul", first.zoneId)

        (3L..5).forEach { engine.accept(sample(it)) }
        val second = requireNotNull(engine.current)
        assertEquals("2026-11-03", second.startDate)
        assertEquals("America/Los_Angeles", second.zoneId)
        assertEquals("2026-11-04", first.startDate)
        assertEquals("Asia/Seoul", first.zoneId)
    }

    @Test fun outOfOrderSamplesCannotMoveTripClockBackwardOrDoubleCount() {
        val engine = engine()
        start(engine)
        engine.accept(sample(1, energy = 0.0))
        engine.accept(sample(2, energy = 0.0))
        engine.accept(sample(3))
        assertEquals(3_000L, engine.current?.durationMs)
        assertEquals(30.0, requireNotNull(engine.current?.distanceMeters), 0.0001)
        assertEquals(30.0, requireNotNull(engine.current?.consumedWh), 0.0001)
    }

    @Test fun interruptIsIdempotentAndNewTripUsesNewId() {
        val engine = engine()
        start(engine)
        val first = requireNotNull(engine.interrupt())
        assertEquals(TripStatus.INTERRUPTED, first.status)
        assertEquals(origin + 2_000, first.endedAtMs)
        assertNull(engine.interrupt())
        (3L..5).forEach { engine.accept(sample(it)) }
        assertNotEquals(first.id, engine.current?.id)
    }

    @Test fun efficiencyUsesTripTotalsAndRequiresMeaningfulCompleteData() {
        val engine = engine()
        (0L..10).forEach { engine.accept(sample(it, energy = 50_000.0 - it * 5)) }
        val trip = requireNotNull(engine.current)
        assertEquals(2.0, requireNotNull(trip.averageEfficiencyKmPerKwh), 0.0001)
        assertNull(trip.copy(distanceMeters = 99.0).averageEfficiencyKmPerKwh)
        assertNull(trip.copy(consumedWh = 49.0).averageEfficiencyKmPerKwh)
        assertNull(trip.copy(hasDataGap = true).averageEfficiencyKmPerKwh)
        assertNull(trip.copy(consumedWh = null).averageEfficiencyKmPerKwh)
    }
}
