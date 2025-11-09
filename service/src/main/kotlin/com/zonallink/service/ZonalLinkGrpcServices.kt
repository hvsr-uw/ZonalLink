package com.zonallink.service

import com.google.protobuf.Empty
import com.zonallink.core.TelemetrySubscription
import com.zonallink.core.ZonalLinkEngine
import com.zonallink.proto.v1.ControllerHeartbeat
import com.zonallink.proto.v1.IngestionAck
import com.zonallink.proto.v1.RawZonalMessage
import com.zonallink.proto.v1.SubscriptionRequest
import com.zonallink.proto.v1.TelemetryEvent
import com.zonallink.proto.v1.VehicleCommandRequest
import com.zonallink.proto.v1.VehicleCommandResponse
import com.zonallink.proto.v1.VehicleCommandServiceGrpcKt
import com.zonallink.proto.v1.VehicleTelemetryServiceGrpcKt
import com.zonallink.proto.v1.VehicleStateSnapshot
import com.zonallink.proto.v1.DiagnosticsSnapshot
import com.zonallink.proto.v1.SystemHealthSnapshot
import com.zonallink.proto.v1.ZonalIngestionServiceGrpcKt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory

class ZonalIngestionGrpcService(
    private val engine: ZonalLinkEngine
) : ZonalIngestionServiceGrpcKt.ZonalIngestionServiceCoroutineImplBase() {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun streamControllerMessages(requests: Flow<RawZonalMessage>): Flow<IngestionAck> =
        flow {
            requests.collect { request ->
                val result = runCatching { engine.ingest(request.toDomain()) }
                val ack = result.fold(
                    onSuccess = {
                        ingestionAckFor(request.messageId, it.accepted, it.quality, it.warnings)
                    },
                    onFailure = {
                        logger.warn("Failed to ingest message {}", request.messageId, it)
                        ingestionAckFor(request.messageId, false, com.zonallink.core.ValueQuality.MALFORMED, listOf(it.message ?: "ingestion failure"))
                    }
                )
                emit(ack)
            }
        }

    override suspend fun reportHeartbeat(request: ControllerHeartbeat): IngestionAck {
        val controller = request.controller.toDomain()
        engine.heartbeat(controller)
        return ingestionAckFor("heartbeat:${controller.controllerId.value}", true, com.zonallink.core.ValueQuality.VALID, emptyList())
    }
}

class VehicleTelemetryGrpcService(
    private val engine: ZonalLinkEngine
) : VehicleTelemetryServiceGrpcKt.VehicleTelemetryServiceCoroutineImplBase() {
    override fun subscribeTelemetry(request: SubscriptionRequest): Flow<TelemetryEvent> {
        val subscription = TelemetrySubscription(
            domains = request.domainsList.map { it.toDomain() }.toSet(),
            signalNames = request.signalNamesList.toSet()
        )
        return engine.subscribe(subscription).map { it.toProto() }
    }

    override suspend fun getVehicleSnapshot(request: Empty): VehicleStateSnapshot =
        engine.snapshot().toProto()

    override suspend fun getDiagnostics(request: Empty): DiagnosticsSnapshot =
        engine.diagnostics().toProto()

    override suspend fun getHealth(request: Empty): SystemHealthSnapshot =
        engine.health().toProto()
}

class VehicleCommandGrpcService(
    private val engine: ZonalLinkEngine
) : VehicleCommandServiceGrpcKt.VehicleCommandServiceCoroutineImplBase() {
    override suspend fun submitCommand(request: VehicleCommandRequest): VehicleCommandResponse =
        engine.submitCommand(request.toDomain()).toProto()
}
