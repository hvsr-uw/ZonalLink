package com.zonallink.core

import kotlinx.coroutines.flow.Flow

/**
 * Narrow integration ports for adapters such as gRPC, Android services, CLI tools, and tests.
 */
interface ZonalIngestionPort {
    fun ingest(message: RawZonalMessage): IngestionResult

    fun heartbeat(controller: ControllerMetadata)
}

interface VehicleTelemetryPort {
    fun snapshot(): VehicleStateSnapshot

    fun diagnostics(): DiagnosticsSnapshot

    fun health(): SystemHealthReport

    fun subscribe(subscription: TelemetrySubscription): Flow<TelemetryEvent>
}

interface VehicleCommandPort {
    fun submitCommand(command: VehicleCommand): CommandResult
}
