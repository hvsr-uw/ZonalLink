package com.zonallink.simulator

import com.zonallink.core.ControllerId
import com.zonallink.core.ControllerMetadata
import com.zonallink.core.RawSignalValue
import com.zonallink.core.RawZonalMessage
import com.zonallink.core.VehicleDomain
import com.zonallink.core.VehicleZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

enum class ScenarioKind {
    NORMAL_DRIVE,
    CHARGING_SESSION,
    DEGRADED_NETWORK
}

data class ScenarioStep(
    val delay: Duration,
    val signalName: String,
    val value: RawSignalValue,
    val controller: ControllerMetadata
)

class ScenarioProfile(
    private val kind: ScenarioKind,
    private val clock: Clock = Clock.systemUTC(),
    seed: Long = 42L
) {
    private val random = Random(seed)

    fun messages(): Flow<RawZonalMessage> = flow {
        var sequence = 1L
        for (step in stepsFor(kind)) {
            delay(step.delay.toMillis())
            emit(
                RawZonalMessage(
                    messageId = "${kind.name.lowercase()}-$sequence",
                    controller = step.controller,
                    signalName = step.signalName,
                    value = step.value,
                    controllerTimestamp = Instant.now(clock),
                    sequenceNumber = sequence++
                )
            )
        }
    }

    private fun stepsFor(kind: ScenarioKind): List<ScenarioStep> =
        when (kind) {
            ScenarioKind.NORMAL_DRIVE -> normalDrive()
            ScenarioKind.CHARGING_SESSION -> chargingSession()
            ScenarioKind.DEGRADED_NETWORK -> degradedNetwork()
        }

    private fun normalDrive(): List<ScenarioStep> =
        listOf(
            step(pack, "battery.state_of_charge", RawSignalValue.NumberValue(82.0, "%")),
            step(chassis, "chassis.vehicle_speed", RawSignalValue.NumberValue(0.0, "km/h")),
            step(frontLeft, "door.front_left.open", RawSignalValue.BooleanValue(false)),
            step(frontRight, "door.front_right.open", RawSignalValue.BooleanValue(false)),
            step(hvac, "hvac.cabin_temperature", RawSignalValue.NumberValue(68.0, "F")),
            step(lighting, "lighting.headlights_on", RawSignalValue.BooleanValue(true)),
            step(chassis, "chassis.vehicle_speed", RawSignalValue.NumberValue(28.0, "mph")),
            step(pack, "battery.state_of_charge", RawSignalValue.NumberValue(81.7, "%")),
            step(thermal, "thermal.battery_loop_temperature", RawSignalValue.NumberValue(31.4, "C")),
            step(chassis, "chassis.vehicle_speed", RawSignalValue.NumberValue(64.0, "km/h"))
        )

    private fun chargingSession(): List<ScenarioStep> =
        listOf(
            step(pack, "battery.charging_state", RawSignalValue.StringValue("PLUGGED_IN")),
            step(pack, "battery.state_of_charge", RawSignalValue.NumberValue(33.0, "%")),
            step(pack, "battery.pack_voltage", RawSignalValue.NumberValue(721500.0, "mV")),
            step(thermal, "thermal.battery_loop_temperature", RawSignalValue.NumberValue(24.0, "C")),
            step(pack, "battery.charging_state", RawSignalValue.StringValue("CHARGING")),
            step(pack, "battery.state_of_charge", RawSignalValue.NumberValue(34.0, "%")),
            step(pack, "battery.state_of_charge", RawSignalValue.NumberValue(35.0, "%"))
        )

    private fun degradedNetwork(): List<ScenarioStep> =
        listOf(
            step(pack, "battery.state_of_charge", RawSignalValue.NumberValue(71.0, "%")),
            step(chassis, "chassis.vehicle_speed", RawSignalValue.NumberValue(42.0, "km/h")),
            step(pack, "battery.state_of_charge", RawSignalValue.NumberValue(130.0 + random.nextInt(20), "%")),
            step(hvac, "hvac.cabin_temperature", RawSignalValue.NumberValue(21.0, "C"), Duration.ofMillis(1800)),
            step(pack, "battery.unknown_vendor_signal", RawSignalValue.NumberValue(1.0, "")),
            step(frontLeft, "door.front_left.open", RawSignalValue.BooleanValue(true), Duration.ofMillis(2300))
        )

    private fun step(
        controller: ControllerMetadata,
        signalName: String,
        value: RawSignalValue,
        delay: Duration = Duration.ofMillis(250)
    ): ScenarioStep = ScenarioStep(delay, signalName, value, controller)

    companion object {
        val pack = ControllerMetadata(ControllerId("pack-controller"), VehicleZone.PACK, VehicleDomain.BATTERY, "2026.18.4", 1)
        val chassis = ControllerMetadata(ControllerId("center-chassis"), VehicleZone.CENTER, VehicleDomain.CHASSIS, "2026.18.2", 1)
        val frontLeft = ControllerMetadata(ControllerId("front-left-body"), VehicleZone.FRONT_LEFT, VehicleDomain.DOORS, "2026.17.9", 1)
        val frontRight = ControllerMetadata(ControllerId("front-right-body"), VehicleZone.FRONT_RIGHT, VehicleDomain.DOORS, "2026.17.9", 1)
        val hvac = ControllerMetadata(ControllerId("center-hvac"), VehicleZone.CENTER, VehicleDomain.HVAC, "2026.18.1", 1)
        val lighting = ControllerMetadata(ControllerId("front-lighting"), VehicleZone.FRONT_LEFT, VehicleDomain.LIGHTING, "2026.18.0", 1)
        val thermal = ControllerMetadata(ControllerId("pack-thermal"), VehicleZone.PACK, VehicleDomain.THERMAL, "2026.18.3", 1)
    }
}
