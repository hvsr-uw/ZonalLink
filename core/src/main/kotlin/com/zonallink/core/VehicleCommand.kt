package com.zonallink.core

import java.time.Instant
import java.util.UUID

enum class VehicleCommandType {
    UNKNOWN,
    SET_HVAC_TARGET_TEMPERATURE,
    SET_HEADLIGHTS,
    START_CHARGING,
    STOP_CHARGING
}

enum class CommandStatus {
    ACCEPTED,
    REJECTED
}

sealed interface CommandPayload {
    data class TemperatureTarget(val celsius: Double) : CommandPayload
    data class BooleanTarget(val enabled: Boolean) : CommandPayload
    data object Empty : CommandPayload
}

data class VehicleCommand(
    val commandId: String = UUID.randomUUID().toString(),
    val type: VehicleCommandType,
    val requestedAt: Instant,
    val payload: CommandPayload,
    val clientId: String
)

data class CommandResult(
    val commandId: String,
    val status: CommandStatus,
    val targetDomain: VehicleDomain?,
    val warnings: List<String>
)

/**
 * Validates client command requests at the connectivity-service boundary.
 *
 * The validator checks payload shape and current vehicle state. Hardware actuation remains a
 * separate integration concern.
 */
class VehicleCommandValidator {
    fun validate(command: VehicleCommand, snapshot: VehicleStateSnapshot): CommandResult {
        if (command.clientId.isBlank()) {
            return rejected(command, "client id is required")
        }

        return when (command.type) {
            VehicleCommandType.UNKNOWN -> rejected(command, "command type is required")
            VehicleCommandType.SET_HVAC_TARGET_TEMPERATURE -> validateTemperature(command)
            VehicleCommandType.SET_HEADLIGHTS -> validateBoolean(command, VehicleDomain.LIGHTING)
            VehicleCommandType.START_CHARGING -> validateChargingCommand(command, snapshot, expectedState = "PLUGGED_IN")
            VehicleCommandType.STOP_CHARGING -> validateEmpty(command, VehicleDomain.BATTERY)
        }
    }

    private fun validateTemperature(command: VehicleCommand): CommandResult {
        val payload = command.payload as? CommandPayload.TemperatureTarget
            ?: return rejected(command, "temperature command requires TemperatureTarget payload")
        if (payload.celsius !in 15.0..32.0) {
            return rejected(command, "target temperature must be between 15C and 32C")
        }
        return accepted(command, VehicleDomain.HVAC)
    }

    private fun validateBoolean(command: VehicleCommand, domain: VehicleDomain): CommandResult {
        if (command.payload !is CommandPayload.BooleanTarget) {
            return rejected(command, "command requires BooleanTarget payload")
        }
        return accepted(command, domain)
    }

    private fun validateChargingCommand(
        command: VehicleCommand,
        snapshot: VehicleStateSnapshot,
        expectedState: String
    ): CommandResult {
        if (command.payload !is CommandPayload.Empty) {
            return rejected(command, "charging command does not accept a payload")
        }
        val chargingState = snapshot.values.find { it.signalName == "battery.charging_state" }
        if (chargingState == null || chargingState.quality != ValueQuality.VALID || chargingState.value != expectedState) {
            return rejected(command, "vehicle must report battery.charging_state=$expectedState before starting charge")
        }
        return accepted(command, VehicleDomain.BATTERY)
    }

    private fun validateEmpty(command: VehicleCommand, domain: VehicleDomain): CommandResult {
        if (command.payload !is CommandPayload.Empty) {
            return rejected(command, "command does not accept a payload")
        }
        return accepted(command, domain)
    }

    private fun accepted(command: VehicleCommand, domain: VehicleDomain): CommandResult =
        CommandResult(command.commandId, CommandStatus.ACCEPTED, domain, emptyList())

    private fun rejected(command: VehicleCommand, warning: String): CommandResult =
        CommandResult(command.commandId, CommandStatus.REJECTED, null, listOf(warning))
}
