package com.zonallink.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.Instant

class ZonalLinkEngineTest {
    @Test
    fun `accepted messages update snapshot and diagnostics`() {
        val engine = ZonalLinkEngine()
        val controller = ControllerMetadata(
            controllerId = ControllerId("pack-controller"),
            zone = VehicleZone.PACK,
            domain = VehicleDomain.BATTERY,
            softwareVersion = "2026.18.4",
            schemaVersion = 1
        )

        val result = engine.ingest(
            RawZonalMessage.create(
                controller = controller,
                signalName = "battery.state_of_charge",
                value = RawSignalValue.NumberValue(74.2, "%"),
                controllerTimestamp = Instant.now(),
                sequenceNumber = 100
            )
        )

        assertTrue(result.accepted)
        assertEquals("74.2", engine.snapshot().values.single().value)
        assertEquals(1L, engine.diagnostics().controllers.single().messagesAccepted)
    }
}
