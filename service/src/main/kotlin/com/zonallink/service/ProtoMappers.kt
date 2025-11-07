package com.zonallink.service

import com.google.protobuf.Timestamp
import com.zonallink.core.ControllerId
import com.zonallink.core.ControllerMetadata
import com.zonallink.core.ControllerStatus
import com.zonallink.core.CommandPayload
import com.zonallink.core.CommandResult
import com.zonallink.core.CommandStatus
import com.zonallink.core.DiagnosticsSnapshot
import com.zonallink.core.RawSignalValue
import com.zonallink.core.RawZonalMessage
import com.zonallink.core.SystemHealthReport
import com.zonallink.core.SystemHealthStatus
import com.zonallink.core.TelemetryEvent
import com.zonallink.core.TelemetryValue
import com.zonallink.core.ValueQuality
import com.zonallink.core.VehicleCommand
import com.zonallink.core.VehicleCommandType
import com.zonallink.core.VehicleDomain
import com.zonallink.core.VehicleStateSnapshot
import com.zonallink.core.VehicleZone
import com.zonallink.proto.v1.ControllerMetadata as ProtoControllerMetadata
import com.zonallink.proto.v1.ControllerStatus as ProtoControllerStatus
import com.zonallink.proto.v1.DiagnosticsSnapshot as ProtoDiagnosticsSnapshot
import com.zonallink.proto.v1.IngestionAck
import com.zonallink.proto.v1.RawSignalValue as ProtoRawSignalValue
import com.zonallink.proto.v1.RawZonalMessage as ProtoRawZonalMessage
import com.zonallink.proto.v1.TelemetryEvent as ProtoTelemetryEvent
import com.zonallink.proto.v1.TelemetryValue as ProtoTelemetryValue
import com.zonallink.proto.v1.ValueQuality as ProtoValueQuality
import com.zonallink.proto.v1.VehicleDomain as ProtoVehicleDomain
import com.zonallink.proto.v1.VehicleStateSnapshot as ProtoVehicleStateSnapshot
import com.zonallink.proto.v1.VehicleZone as ProtoVehicleZone
import com.zonallink.proto.v1.CommandStatus as ProtoCommandStatus
import com.zonallink.proto.v1.SystemHealthStatus as ProtoSystemHealthStatus
import com.zonallink.proto.v1.VehicleCommandRequest as ProtoVehicleCommandRequest
import com.zonallink.proto.v1.VehicleCommandResponse as ProtoVehicleCommandResponse
import com.zonallink.proto.v1.VehicleCommandType as ProtoVehicleCommandType
import com.zonallink.proto.v1.controllerHealth
import com.zonallink.proto.v1.diagnosticsSnapshot
import com.zonallink.proto.v1.ingestionAck
import com.zonallink.proto.v1.metricPoint
import com.zonallink.proto.v1.systemHealthSnapshot
import com.zonallink.proto.v1.telemetryEvent
import com.zonallink.proto.v1.telemetryValue
import com.zonallink.proto.v1.vehicleCommandResponse
import com.zonallink.proto.v1.vehicleStateSnapshot
import java.time.Instant

fun ProtoRawZonalMessage.toDomain(): RawZonalMessage =
    RawZonalMessage(
        messageId = messageId,
        controller = controller.toDomain(),
        signalName = signalName,
        value = value.toDomain(),
        controllerTimestamp = controllerTimestamp.toInstant(),
        sequenceNumber = sequenceNumber
    )

fun ProtoControllerMetadata.toDomain(): ControllerMetadata =
    ControllerMetadata(
        controllerId = ControllerId(controllerId),
        zone = zone.toDomain(),
        domain = domain.toDomain(),
        softwareVersion = softwareVersion,
        schemaVersion = schemaVersion
    )

fun ProtoRawSignalValue.toDomain(): RawSignalValue? =
    when (payloadCase) {
        ProtoRawSignalValue.PayloadCase.NUMBER_VALUE -> RawSignalValue.NumberValue(numberValue, unit)
        ProtoRawSignalValue.PayloadCase.BOOL_VALUE -> RawSignalValue.BooleanValue(boolValue, unit)
        ProtoRawSignalValue.PayloadCase.STRING_VALUE -> RawSignalValue.StringValue(stringValue, unit)
        ProtoRawSignalValue.PayloadCase.PAYLOAD_NOT_SET, null -> null
    }

fun TelemetryEvent.toProto(): ProtoTelemetryEvent =
    telemetryEvent {
        eventId = this@toProto.eventId
        sourceControllerId = this@toProto.sourceControllerId.value
        domain = this@toProto.domain.toProto()
        zone = this@toProto.zone.toProto()
        signalName = this@toProto.signalName
        value = this@toProto.value
        unit = this@toProto.unit
        quality = this@toProto.quality.toProto()
        eventTimestamp = this@toProto.eventTimestamp.toProto()
        receivedTimestamp = this@toProto.receivedTimestamp.toProto()
        warnings.addAll(this@toProto.warnings)
    }

fun VehicleStateSnapshot.toProto(): ProtoVehicleStateSnapshot =
    vehicleStateSnapshot {
        generatedAt = this@toProto.generatedAt.toProto()
        values.addAll(this@toProto.values.map { it.toProto() })
    }

fun TelemetryValue.toProto(): ProtoTelemetryValue =
    telemetryValue {
        signalName = this@toProto.signalName
        domain = this@toProto.domain.toProto()
        zone = this@toProto.zone.toProto()
        value = this@toProto.value
        unit = this@toProto.unit
        quality = this@toProto.quality.toProto()
        lastUpdatedAt = this@toProto.lastUpdatedAt.toProto()
        sourceControllerId = this@toProto.sourceControllerId.value
        staleAt = this@toProto.staleAt.toProto()
    }

fun DiagnosticsSnapshot.toProto(): ProtoDiagnosticsSnapshot =
    diagnosticsSnapshot {
        generatedAt = this@toProto.generatedAt.toProto()
        controllers.addAll(this@toProto.controllers.map { health ->
            controllerHealth {
                controllerId = health.controller.controllerId.value
                domain = health.controller.domain.toProto()
                zone = health.controller.zone.toProto()
                status = health.status.toProto()
                lastSeenAt = health.lastSeenAt.toProto()
                messagesAccepted = health.messagesAccepted
                messagesRejected = health.messagesRejected
                health.lastSequenceNumber?.let { lastSequenceNumber = it }
                consecutiveFailures = health.consecutiveFailures
                lastError = health.lastError ?: ""
            }
        })
        metrics.addAll(this@toProto.metrics.map { metric ->
            metricPoint {
                name = metric.name
                value = metric.value
                tags.putAll(metric.tags)
            }
        })
    }

fun ingestionAckFor(messageId: String, accepted: Boolean, quality: ValueQuality, warnings: List<String>): IngestionAck =
    ingestionAck {
        this.messageId = messageId
        this.accepted = accepted
        this.quality = quality.toProto()
        this.warnings.addAll(warnings)
    }

fun SystemHealthReport.toProto() =
    systemHealthSnapshot {
        status = this@toProto.status.toProto()
        generatedAt = this@toProto.generatedAt.toProto()
        controllerCount = this@toProto.controllerCount
        offlineControllers = this@toProto.offlineControllers
        degradedControllers = this@toProto.degradedControllers
        staleSignals = this@toProto.staleSignals
        rejectedMessages = this@toProto.rejectedMessages
        reasons.addAll(this@toProto.reasons)
    }

fun ProtoVehicleCommandRequest.toDomain(): VehicleCommand =
    VehicleCommand(
        commandId = commandId.ifBlank { java.util.UUID.randomUUID().toString() },
        type = type.toDomain(),
        requestedAt = requestedAt.toInstant(),
        payload = when (payloadCase) {
            ProtoVehicleCommandRequest.PayloadCase.TARGET_TEMPERATURE_CELSIUS ->
                CommandPayload.TemperatureTarget(targetTemperatureCelsius)
            ProtoVehicleCommandRequest.PayloadCase.ENABLED ->
                CommandPayload.BooleanTarget(enabled)
            ProtoVehicleCommandRequest.PayloadCase.EMPTY ->
                CommandPayload.Empty
            ProtoVehicleCommandRequest.PayloadCase.PAYLOAD_NOT_SET, null ->
                CommandPayload.Empty
        },
        clientId = clientId
    )

fun CommandResult.toProto(): ProtoVehicleCommandResponse =
    vehicleCommandResponse {
        commandId = this@toProto.commandId
        status = this@toProto.status.toProto()
        targetDomain = this@toProto.targetDomain?.toProto() ?: ProtoVehicleDomain.VEHICLE_DOMAIN_UNSPECIFIED
        warnings.addAll(this@toProto.warnings)
    }

fun Instant.toProto(): Timestamp =
    Timestamp.newBuilder().setSeconds(epochSecond).setNanos(nano).build()

fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanos.toLong())

fun VehicleDomain.toProto(): ProtoVehicleDomain =
    when (this) {
        VehicleDomain.BATTERY -> ProtoVehicleDomain.BATTERY
        VehicleDomain.DOORS -> ProtoVehicleDomain.DOORS
        VehicleDomain.HVAC -> ProtoVehicleDomain.HVAC
        VehicleDomain.LIGHTING -> ProtoVehicleDomain.LIGHTING
        VehicleDomain.CHASSIS -> ProtoVehicleDomain.CHASSIS
        VehicleDomain.THERMAL -> ProtoVehicleDomain.THERMAL
        VehicleDomain.DIAGNOSTICS -> ProtoVehicleDomain.DIAGNOSTICS
    }

fun ProtoVehicleDomain.toDomain(): VehicleDomain =
    when (this) {
        ProtoVehicleDomain.BATTERY -> VehicleDomain.BATTERY
        ProtoVehicleDomain.DOORS -> VehicleDomain.DOORS
        ProtoVehicleDomain.HVAC -> VehicleDomain.HVAC
        ProtoVehicleDomain.LIGHTING -> VehicleDomain.LIGHTING
        ProtoVehicleDomain.CHASSIS -> VehicleDomain.CHASSIS
        ProtoVehicleDomain.THERMAL -> VehicleDomain.THERMAL
        ProtoVehicleDomain.DIAGNOSTICS -> VehicleDomain.DIAGNOSTICS
        else -> VehicleDomain.DIAGNOSTICS
    }

fun VehicleZone.toProto(): ProtoVehicleZone =
    when (this) {
        VehicleZone.FRONT_LEFT -> ProtoVehicleZone.FRONT_LEFT
        VehicleZone.FRONT_RIGHT -> ProtoVehicleZone.FRONT_RIGHT
        VehicleZone.REAR_LEFT -> ProtoVehicleZone.REAR_LEFT
        VehicleZone.REAR_RIGHT -> ProtoVehicleZone.REAR_RIGHT
        VehicleZone.CENTER -> ProtoVehicleZone.CENTER
        VehicleZone.PACK -> ProtoVehicleZone.PACK
    }

fun ProtoVehicleZone.toDomain(): VehicleZone =
    when (this) {
        ProtoVehicleZone.FRONT_LEFT -> VehicleZone.FRONT_LEFT
        ProtoVehicleZone.FRONT_RIGHT -> VehicleZone.FRONT_RIGHT
        ProtoVehicleZone.REAR_LEFT -> VehicleZone.REAR_LEFT
        ProtoVehicleZone.REAR_RIGHT -> VehicleZone.REAR_RIGHT
        ProtoVehicleZone.CENTER -> VehicleZone.CENTER
        ProtoVehicleZone.PACK -> VehicleZone.PACK
        else -> VehicleZone.CENTER
    }

fun ValueQuality.toProto(): ProtoValueQuality =
    when (this) {
        ValueQuality.VALID -> ProtoValueQuality.VALID
        ValueQuality.STALE -> ProtoValueQuality.STALE
        ValueQuality.ESTIMATED -> ProtoValueQuality.ESTIMATED
        ValueQuality.MISSING -> ProtoValueQuality.MISSING
        ValueQuality.MALFORMED -> ProtoValueQuality.MALFORMED
        ValueQuality.UNSUPPORTED -> ProtoValueQuality.UNSUPPORTED
    }

fun ControllerStatus.toProto(): ProtoControllerStatus =
    when (this) {
        ControllerStatus.UNKNOWN -> ProtoControllerStatus.CONTROLLER_UNKNOWN
        ControllerStatus.ONLINE -> ProtoControllerStatus.CONTROLLER_ONLINE
        ControllerStatus.DEGRADED -> ProtoControllerStatus.CONTROLLER_DEGRADED
        ControllerStatus.OFFLINE -> ProtoControllerStatus.CONTROLLER_OFFLINE
    }

fun SystemHealthStatus.toProto(): ProtoSystemHealthStatus =
    when (this) {
        SystemHealthStatus.OK -> ProtoSystemHealthStatus.SYSTEM_HEALTH_OK
        SystemHealthStatus.DEGRADED -> ProtoSystemHealthStatus.SYSTEM_HEALTH_DEGRADED
        SystemHealthStatus.CRITICAL -> ProtoSystemHealthStatus.SYSTEM_HEALTH_CRITICAL
    }

fun ProtoVehicleCommandType.toDomain(): VehicleCommandType =
    when (this) {
        ProtoVehicleCommandType.SET_HVAC_TARGET_TEMPERATURE -> VehicleCommandType.SET_HVAC_TARGET_TEMPERATURE
        ProtoVehicleCommandType.SET_HEADLIGHTS -> VehicleCommandType.SET_HEADLIGHTS
        ProtoVehicleCommandType.START_CHARGING -> VehicleCommandType.START_CHARGING
        ProtoVehicleCommandType.STOP_CHARGING -> VehicleCommandType.STOP_CHARGING
        else -> VehicleCommandType.UNKNOWN
    }

fun CommandStatus.toProto(): ProtoCommandStatus =
    when (this) {
        CommandStatus.ACCEPTED -> ProtoCommandStatus.COMMAND_ACCEPTED
        CommandStatus.REJECTED -> ProtoCommandStatus.COMMAND_REJECTED
    }
