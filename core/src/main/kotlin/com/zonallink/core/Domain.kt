package com.zonallink.core

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Canonical vehicle-domain model used inside ZonalLink.
 *
 * Raw controller payloads are intentionally translated into these types before they reach state,
 * subscriptions, or client APIs. That keeps controller-specific quirks out of infotainment-facing
 * contracts.
 */
enum class VehicleDomain {
    BATTERY,
    DOORS,
    HVAC,
    LIGHTING,
    CHASSIS,
    THERMAL,
    DIAGNOSTICS
}

enum class VehicleZone {
    FRONT_LEFT,
    FRONT_RIGHT,
    REAR_LEFT,
    REAR_RIGHT,
    CENTER,
    PACK
}

enum class ValueQuality {
    VALID,
    STALE,
    ESTIMATED,
    MISSING,
    MALFORMED,
    UNSUPPORTED
}

enum class ControllerStatus {
    UNKNOWN,
    ONLINE,
    DEGRADED,
    OFFLINE
}

enum class SignalType {
    NUMBER,
    BOOLEAN,
    STRING
}

data class ControllerId(val value: String) {
    init {
        require(value.isNotBlank()) { "controller id must not be blank" }
    }
}

data class ControllerMetadata(
    val controllerId: ControllerId,
    val zone: VehicleZone,
    val domain: VehicleDomain,
    val softwareVersion: String,
    val schemaVersion: Int
)

sealed interface RawSignalValue {
    val unit: String

    data class NumberValue(val value: Double, override val unit: String) : RawSignalValue
    data class BooleanValue(val value: Boolean, override val unit: String = "") : RawSignalValue
    data class StringValue(val value: String, override val unit: String = "") : RawSignalValue
}

data class RawZonalMessage(
    val messageId: String,
    val controller: ControllerMetadata,
    val signalName: String,
    val value: RawSignalValue?,
    val controllerTimestamp: Instant,
    val sequenceNumber: Long
) {
    companion object {
        fun create(
            controller: ControllerMetadata,
            signalName: String,
            value: RawSignalValue?,
            controllerTimestamp: Instant,
            sequenceNumber: Long
        ): RawZonalMessage =
            RawZonalMessage(
                messageId = UUID.randomUUID().toString(),
                controller = controller,
                signalName = signalName,
                value = value,
                controllerTimestamp = controllerTimestamp,
                sequenceNumber = sequenceNumber
            )
    }
}

data class SignalDefinition(
    val name: String,
    val domain: VehicleDomain,
    val expectedType: SignalType,
    val canonicalUnit: String,
    val min: Double? = null,
    val max: Double? = null,
    val allowedStringValues: Set<String> = emptySet(),
    val staleAfter: Duration = Duration.ofSeconds(3)
) {
    val requiresUnit: Boolean = expectedType == SignalType.NUMBER && canonicalUnit.isNotBlank()
}

data class TelemetryEvent(
    val eventId: String,
    val sourceControllerId: ControllerId,
    val domain: VehicleDomain,
    val zone: VehicleZone,
    val signalName: String,
    val value: String,
    val unit: String,
    val quality: ValueQuality,
    val eventTimestamp: Instant,
    val receivedTimestamp: Instant,
    val warnings: List<String> = emptyList()
) {
    val stateKey: SignalKey = SignalKey(domain, zone, signalName)
}

data class SignalKey(
    val domain: VehicleDomain,
    val zone: VehicleZone,
    val signalName: String
)

data class TelemetryValue(
    val signalName: String,
    val domain: VehicleDomain,
    val zone: VehicleZone,
    val value: String,
    val unit: String,
    val quality: ValueQuality,
    val lastUpdatedAt: Instant,
    val staleAt: Instant,
    val sourceControllerId: ControllerId
)

data class VehicleStateSnapshot(
    val generatedAt: Instant,
    val values: List<TelemetryValue>
)

data class ControllerHealth(
    val controller: ControllerMetadata,
    val lastSeenAt: Instant,
    val status: ControllerStatus,
    val messagesAccepted: Long,
    val messagesRejected: Long,
    val lastSequenceNumber: Long?,
    val consecutiveFailures: Int,
    val lastError: String?
)

data class MetricPoint(
    val name: String,
    val value: Double,
    val tags: Map<String, String> = emptyMap()
)

data class DiagnosticsSnapshot(
    val generatedAt: Instant,
    val controllers: List<ControllerHealth>,
    val metrics: List<MetricPoint>
)

data class IngestionResult(
    val accepted: Boolean,
    val event: TelemetryEvent?,
    val quality: ValueQuality,
    val warnings: List<String>
)

data class MessageInspection(
    val accepted: Boolean,
    val quality: ValueQuality,
    val warnings: List<String>
)
