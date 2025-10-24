package com.zonallink.core

/**
 * Application service that coordinates ingestion without owning transport concerns.
 *
 * gRPC, CLI, and Android service adapters can all reuse this engine. It is responsible for
 * controller sequencing, normalization, state updates, event publication, and diagnostics.
 */
class ZonalLinkEngine(
    private val config: ZonalLinkConfig = ZonalLinkConfig(),
    private val normalizer: TelemetryNormalizer = TelemetryNormalizer(supportedSchemaVersions = config.supportedSchemaVersions),
    private val stateStore: VehicleStateStore = VehicleStateStore(defaultStaleAfter = config.defaultSignalStaleAfter),
    private val eventBus: TelemetryEventBus = TelemetryEventBus(extraBufferCapacity = config.eventBusBufferCapacity),
    private val diagnostics: DiagnosticsRegistry = DiagnosticsRegistry(offlineAfter = config.controllerOfflineAfter),
    private val eventJournal: TelemetryEventJournal = TelemetryEventJournal.Noop,
    private val commandValidator: VehicleCommandValidator = VehicleCommandValidator(),
    private val healthReporter: SystemHealthReporter = SystemHealthReporter()
) : ZonalIngestionPort, VehicleTelemetryPort, VehicleCommandPort {
    override fun ingest(message: RawZonalMessage): IngestionResult {
        val inspection = diagnostics.inspect(message)
        if (!inspection.accepted) {
            diagnostics.markRejected(message.controller, inspection.quality, inspection.warnings)
            return IngestionResult(false, null, inspection.quality, inspection.warnings)
        }

        val result = normalizer.normalize(message)
        val warnings = inspection.warnings + result.warnings
        if (result.accepted && result.event != null) {
            val event = if (warnings == result.event.warnings) {
                result.event
            } else {
                result.event.copy(warnings = warnings)
            }
            stateStore.apply(event)
            runCatching { eventJournal.append(event) }
                .onFailure {
                    diagnostics.increment("journal.append_failed", mapOf("exception" to (it::class.simpleName ?: "Unknown")))
                }
            val published = eventBus.publish(event)
            diagnostics.markAccepted(message.controller, message.sequenceNumber, warnings, event.receivedTimestamp)
            if (!published) {
                diagnostics.increment("events.dropped", mapOf("reason" to "subscriber_backpressure"))
            }
            return result.copy(event = event, warnings = warnings)
        } else {
            diagnostics.markRejected(message.controller, result.quality, warnings)
        }
        return result.copy(warnings = warnings)
    }

    override fun heartbeat(controller: ControllerMetadata) {
        diagnostics.markHeartbeat(controller)
    }

    override fun snapshot(): VehicleStateSnapshot = stateStore.snapshot()

    override fun diagnostics(): DiagnosticsSnapshot = diagnostics.snapshot()

    override fun health(): SystemHealthReport = healthReporter.report(diagnostics(), snapshot())

    override fun submitCommand(command: VehicleCommand): CommandResult {
        val result = commandValidator.validate(command, snapshot())
        val statusTag = result.status.name
        diagnostics.increment("commands.submitted", mapOf("type" to command.type.name, "status" to statusTag))
        result.warnings.forEach {
            diagnostics.increment("commands.rejected", mapOf("type" to command.type.name, "reason" to it))
        }
        return result
    }

    override fun subscribe(subscription: TelemetrySubscription) = eventBus.subscribe(subscription)
}
