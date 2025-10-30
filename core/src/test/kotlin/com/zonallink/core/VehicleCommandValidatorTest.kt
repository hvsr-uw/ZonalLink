package com.zonallink.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.Instant

class VehicleCommandValidatorTest {
    private val validator = VehicleCommandValidator()

    @Test
    fun `accepts in-range hvac target command`() {
        val result = validator.validate(
            VehicleCommand(
                type = VehicleCommandType.SET_HVAC_TARGET_TEMPERATURE,
                requestedAt = Instant.now(),
                payload = CommandPayload.TemperatureTarget(21.0),
                clientId = "infotainment"
            ),
            VehicleStateSnapshot(Instant.now(), emptyList())
        )

        assertEquals(CommandStatus.ACCEPTED, result.status)
        assertEquals(VehicleDomain.HVAC, result.targetDomain)
    }

    @Test
    fun `rejects charging start when vehicle is not plugged in`() {
        val result = validator.validate(
            VehicleCommand(
                type = VehicleCommandType.START_CHARGING,
                requestedAt = Instant.now(),
                payload = CommandPayload.Empty,
                clientId = "infotainment"
            ),
            VehicleStateSnapshot(Instant.now(), emptyList())
        )

        assertEquals(CommandStatus.REJECTED, result.status)
        assertTrue(result.warnings.single().contains("battery.charging_state=PLUGGED_IN"))
    }
}
