package com.zonallink.core

import java.time.Duration

/**
 * Catalog of telemetry signals the service is willing to expose as stable client API.
 *
 * Adding a signal here is a deliberate compatibility decision: the catalog defines type, unit,
 * bounds, enum values, and freshness expectations used by normalization and snapshots.
 */
class SignalCatalog(
    definitions: Collection<SignalDefinition> = defaultDefinitions
) {
    private val byName = definitions.associateBy { it.name }

    fun definitionFor(name: String): SignalDefinition? = byName[name]

    fun all(): Collection<SignalDefinition> = byName.values

    companion object {
        val defaultDefinitions = listOf(
            SignalDefinition(
                name = "battery.state_of_charge",
                domain = VehicleDomain.BATTERY,
                expectedType = SignalType.NUMBER,
                canonicalUnit = "%",
                min = 0.0,
                max = 100.0,
                staleAfter = Duration.ofSeconds(5)
            ),
            SignalDefinition(
                name = "battery.pack_voltage",
                domain = VehicleDomain.BATTERY,
                expectedType = SignalType.NUMBER,
                canonicalUnit = "V",
                min = 250.0,
                max = 950.0,
                staleAfter = Duration.ofSeconds(5)
            ),
            SignalDefinition(
                name = "battery.charging_state",
                domain = VehicleDomain.BATTERY,
                expectedType = SignalType.STRING,
                canonicalUnit = "",
                allowedStringValues = setOf("UNPLUGGED", "PLUGGED_IN", "CHARGING", "COMPLETE", "FAULTED"),
                staleAfter = Duration.ofSeconds(5)
            ),
            SignalDefinition(
                name = "door.front_left.open",
                domain = VehicleDomain.DOORS,
                expectedType = SignalType.BOOLEAN,
                canonicalUnit = "",
                staleAfter = Duration.ofSeconds(2)
            ),
            SignalDefinition(
                name = "door.front_right.open",
                domain = VehicleDomain.DOORS,
                expectedType = SignalType.BOOLEAN,
                canonicalUnit = "",
                staleAfter = Duration.ofSeconds(2)
            ),
            SignalDefinition(
                name = "door.rear_left.open",
                domain = VehicleDomain.DOORS,
                expectedType = SignalType.BOOLEAN,
                canonicalUnit = "",
                staleAfter = Duration.ofSeconds(2)
            ),
            SignalDefinition(
                name = "door.rear_right.open",
                domain = VehicleDomain.DOORS,
                expectedType = SignalType.BOOLEAN,
                canonicalUnit = "",
                staleAfter = Duration.ofSeconds(2)
            ),
            SignalDefinition(
                name = "hvac.cabin_temperature",
                domain = VehicleDomain.HVAC,
                expectedType = SignalType.NUMBER,
                canonicalUnit = "C",
                min = -40.0,
                max = 85.0,
                staleAfter = Duration.ofSeconds(4)
            ),
            SignalDefinition(
                name = "hvac.target_temperature",
                domain = VehicleDomain.HVAC,
                expectedType = SignalType.NUMBER,
                canonicalUnit = "C",
                min = 15.0,
                max = 32.0,
                staleAfter = Duration.ofSeconds(8)
            ),
            SignalDefinition(
                name = "lighting.headlights_on",
                domain = VehicleDomain.LIGHTING,
                expectedType = SignalType.BOOLEAN,
                canonicalUnit = "",
                staleAfter = Duration.ofSeconds(3)
            ),
            SignalDefinition(
                name = "chassis.vehicle_speed",
                domain = VehicleDomain.CHASSIS,
                expectedType = SignalType.NUMBER,
                canonicalUnit = "km/h",
                min = 0.0,
                max = 260.0,
                staleAfter = Duration.ofSeconds(1)
            ),
            SignalDefinition(
                name = "thermal.battery_loop_temperature",
                domain = VehicleDomain.THERMAL,
                expectedType = SignalType.NUMBER,
                canonicalUnit = "C",
                min = -40.0,
                max = 120.0,
                staleAfter = Duration.ofSeconds(3)
            ),
            SignalDefinition(
                name = "diagnostics.controller_fault",
                domain = VehicleDomain.DIAGNOSTICS,
                expectedType = SignalType.STRING,
                canonicalUnit = "",
                allowedStringValues = setOf("NONE", "RECOVERABLE", "CRITICAL", "COMMUNICATION_LOSS"),
                staleAfter = Duration.ofSeconds(10)
            )
        )
    }
}
