package com.zonallink.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.Instant

class HealthAndJournalTest {
    private val controller = ControllerMetadata(
        controllerId = ControllerId("pack-controller"),
        zone = VehicleZone.PACK,
        domain = VehicleDomain.BATTERY,
        softwareVersion = "2026.18.4",
        schemaVersion = 1
    )

    @Test
    fun `accepted telemetry is appended to journal`() {
        val journal = RecordingTelemetryEventJournal()
        val engine = ZonalLinkEngine(eventJournal = journal)

        engine.ingest(message(1, 80.0))

        assertEquals(1, journal.events.size)
        assertEquals("battery.state_of_charge", journal.events.single().signalName)
    }

    @Test
    fun `journal failure is reported without rejecting telemetry`() {
        val engine = ZonalLinkEngine(eventJournal = FailingTelemetryEventJournal())

        val result = engine.ingest(message(1, 80.0))
        val metrics = engine.diagnostics().metrics

        assertTrue(result.accepted)
        assertTrue(metrics.any { it.name == "journal.append_failed" })
    }

    @Test
    fun `health report degrades when messages are rejected`() {
        val engine = ZonalLinkEngine()

        engine.ingest(message(1, 180.0))

        val health = engine.health()
        assertEquals(SystemHealthStatus.DEGRADED, health.status)
        assertEquals(1L, health.rejectedMessages)
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
