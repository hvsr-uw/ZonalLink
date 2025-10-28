package com.zonallink.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.Instant

class TelemetryNormalizerTest {
    private val normalizer = TelemetryNormalizer()
    private val controller = ControllerMetadata(
        controllerId = ControllerId("front-left-body"),
        zone = VehicleZone.FRONT_LEFT,
        domain = VehicleDomain.HVAC,
        softwareVersion = "2026.18.4",
        schemaVersion = 1
    )

    @Test
    fun `converts fahrenheit cabin temperature to canonical celsius`() {
        val result = normalizer.normalize(
            RawZonalMessage.create(
                controller = controller,
                signalName = "hvac.cabin_temperature",
                value = RawSignalValue.NumberValue(68.0, "F"),
                controllerTimestamp = Instant.parse("2026-06-25T12:00:00Z"),
                sequenceNumber = 1
            )
        )

        assertTrue(result.accepted)
        assertNotNull(result.event)
        assertEquals("20", result.event.value)
        assertEquals("C", result.event.unit)
        assertEquals(ValueQuality.VALID, result.quality)
    }

    @Test
    fun `rejects unsupported signal names without producing an event`() {
        val result = normalizer.normalize(
            RawZonalMessage.create(
                controller = controller,
                signalName = "unknown.signal",
                value = RawSignalValue.NumberValue(1.0, ""),
                controllerTimestamp = Instant.now(),
                sequenceNumber = 1
            )
        )

        assertFalse(result.accepted)
        assertEquals(ValueQuality.UNSUPPORTED, result.quality)
        assertEquals(null, result.event)
    }

    @Test
    fun `rejects physically impossible numeric values`() {
        val result = normalizer.normalize(
            RawZonalMessage.create(
                controller = controller.copy(domain = VehicleDomain.BATTERY, zone = VehicleZone.PACK),
                signalName = "battery.state_of_charge",
                value = RawSignalValue.NumberValue(128.0, "%"),
                controllerTimestamp = Instant.now(),
                sequenceNumber = 2
            )
        )

        assertFalse(result.accepted)
        assertEquals(ValueQuality.MALFORMED, result.quality)
        assertTrue(result.warnings.first().contains("above expected maximum"))
    }

    @Test
    fun `rejects unsupported schema versions before exposing telemetry`() {
        val result = normalizer.normalize(
            RawZonalMessage.create(
                controller = controller.copy(schemaVersion = 99),
                signalName = "hvac.cabin_temperature",
                value = RawSignalValue.NumberValue(21.0, "C"),
                controllerTimestamp = Instant.now(),
                sequenceNumber = 3
            )
        )

        assertFalse(result.accepted)
        assertEquals(ValueQuality.UNSUPPORTED, result.quality)
    }

    @Test
    fun `rejects unknown units instead of assuming the number is canonical`() {
        val result = normalizer.normalize(
            RawZonalMessage.create(
                controller = controller.copy(domain = VehicleDomain.CHASSIS, zone = VehicleZone.CENTER),
                signalName = "chassis.vehicle_speed",
                value = RawSignalValue.NumberValue(20.0, "knots"),
                controllerTimestamp = Instant.now(),
                sequenceNumber = 4
            )
        )

        assertFalse(result.accepted)
        assertEquals(ValueQuality.MALFORMED, result.quality)
    }
}
