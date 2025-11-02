package com.zonallink.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.Instant

class ControllerLifecycleTest {
    private val controller = ControllerMetadata(
        controllerId = ControllerId("pack-controller"),
        zone = VehicleZone.PACK,
        domain = VehicleDomain.BATTERY,
        softwareVersion = "2026.18.4",
        schemaVersion = 1
    )

    @Test
    fun `rejects duplicate sequence without overwriting newer state`() {
        val engine = ZonalLinkEngine()

        engine.ingest(message(sequence = 10, stateOfCharge = 72.0))
        val duplicate = engine.ingest(message(sequence = 10, stateOfCharge = 12.0))

        assertFalse(duplicate.accepted)
        assertEquals(ValueQuality.STALE, duplicate.quality)
        assertEquals("72", engine.snapshot().values.single().value)
        assertEquals(1L, engine.diagnostics().controllers.single().messagesRejected)
    }

    @Test
    fun `records sequence gaps as degraded but accepts newer telemetry`() {
        val engine = ZonalLinkEngine()

        engine.ingest(message(sequence = 1, stateOfCharge = 72.0))
        val result = engine.ingest(message(sequence = 4, stateOfCharge = 71.5))

        assertTrue(result.accepted)
        assertTrue(result.warnings.single().contains("detected gap"))
        assertEquals(ControllerStatus.DEGRADED, engine.diagnostics().controllers.single().status)
        assertEquals(4L, engine.diagnostics().controllers.single().lastSequenceNumber)
    }

    @Test
    fun `marks repeatedly rejected controller degraded`() {
        val engine = ZonalLinkEngine()
        repeat(3) { index ->
            engine.ingest(
                RawZonalMessage.create(
                    controller = controller,
                    signalName = "battery.state_of_charge",
                    value = RawSignalValue.NumberValue(150.0 + index, "%"),
                    controllerTimestamp = Instant.now(),
                    sequenceNumber = index + 1L
                )
            )
        }

        val health = engine.diagnostics().controllers.single()
        assertEquals(ControllerStatus.DEGRADED, health.status)
        assertEquals(3, health.consecutiveFailures)
        assertEquals(3L, health.messagesRejected)
    }

    private fun message(sequence: Long, stateOfCharge: Double): RawZonalMessage =
        RawZonalMessage.create(
            controller = controller,
            signalName = "battery.state_of_charge",
            value = RawSignalValue.NumberValue(stateOfCharge, "%"),
            controllerTimestamp = Instant.now(),
            sequenceNumber = sequence
        )
}
