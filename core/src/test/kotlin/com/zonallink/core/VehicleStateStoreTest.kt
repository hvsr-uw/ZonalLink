package com.zonallink.core

import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.Instant

class VehicleStateStoreTest {
    @Test
    fun `marks values stale when their signal freshness budget expires`() {
        val store = VehicleStateStore()
        val event = TelemetryEvent(
            eventId = "event-1",
            sourceControllerId = ControllerId("chassis-center"),
            domain = VehicleDomain.CHASSIS,
            zone = VehicleZone.CENTER,
            signalName = "chassis.vehicle_speed",
            value = "42",
            unit = "km/h",
            quality = ValueQuality.VALID,
            eventTimestamp = Instant.parse("2026-06-25T12:00:00Z"),
            receivedTimestamp = Instant.parse("2026-06-25T12:00:00Z")
        )

        store.apply(event)

        val fresh = store.snapshot(Instant.parse("2026-06-25T12:00:00.500Z"))
        val stale = store.snapshot(Instant.parse("2026-06-25T12:00:02Z"))

        assertEquals(ValueQuality.VALID, fresh.values.single().quality)
        assertEquals(Instant.parse("2026-06-25T12:00:01Z"), fresh.values.single().staleAt)
        assertEquals(ValueQuality.STALE, stale.values.single().quality)
    }
}
