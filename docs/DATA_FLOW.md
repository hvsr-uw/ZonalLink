# Data Flow

This document follows data through ZonalLink from raw controller input to client-facing telemetry, diagnostics, and commands.

## Ingestion Flow

```text
ScenarioProfile
  -> RawZonalMessage
  -> ZonalLinkEngine.ingest
  -> DiagnosticsRegistry.inspect
  -> TelemetryNormalizer.normalize
  -> VehicleStateStore.apply
  -> TelemetryEventJournal.append
  -> TelemetryEventBus.publish
  -> DiagnosticsRegistry.markAccepted
```

## Step By Step

1. `ScenarioProfile` emits a `RawZonalMessage`.

The local simulator includes controller metadata, signal name, payload, timestamp, and sequence number.

2. `DiagnosticsRegistry.inspect` validates the envelope.

It checks sequence numbers before payload normalization. Duplicate and older messages are rejected as stale. Sequence gaps are accepted with warnings and recorded as degraded behavior.

3. `TelemetryNormalizer` validates the payload.

It checks:

- schema version
- known signal name
- expected payload type
- required unit
- supported unit conversion
- numeric bounds
- allowed string values
- suspicious future timestamps

4. A `TelemetryEvent` is created.

The event is canonical. It has a stable domain, zone, signal name, value, unit, quality, timestamps, source controller, and warnings.

5. `VehicleStateStore` updates latest-known state.

The store persists a `TelemetryValue` and computes `staleAt` based on the signal catalog.

6. The journal records the event.

If a JSONL journal is configured, accepted telemetry is appended. Journal failure is reported as a diagnostic metric while ingestion continues.

7. Subscribers receive the event.

`TelemetryEventBus` publishes to matching subscriptions with bounded buffering.

8. Diagnostics are updated.

The registry records accepted messages, rejected messages, sequence gaps, controller status, and failure details.

## Snapshot Flow

```text
Client
  -> VehicleTelemetryPort.snapshot
  -> VehicleStateStore.snapshot
  -> VehicleStateSnapshot
```

Snapshot reads apply staleness at read time. This is important because a value can become stale even when no new message arrives.

## Diagnostics Flow

```text
Client
  -> VehicleTelemetryPort.diagnostics
  -> DiagnosticsRegistry.snapshot
  -> DiagnosticsSnapshot
```

Diagnostics are controller-oriented. They answer questions such as:

- Which controllers are offline?
- Which controllers are degraded?
- What was the last accepted sequence?
- How many messages were rejected?
- What was the last error?

## Health Flow

```text
Client
  -> VehicleTelemetryPort.health
  -> SystemHealthReporter.report
  -> SystemHealthReport
```

Health is an aggregate view over diagnostics and state. It reports:

- `OK`
- `DEGRADED`
- `CRITICAL`

The report includes counts and human-readable reasons.

## Command Flow

```text
Client
  -> VehicleCommandPort.submitCommand
  -> VehicleCommandValidator.validate
  -> CommandResult
  -> Diagnostics metrics
```

Commands are validated against payload rules and, where needed, current state.

Example: `START_CHARGING` is rejected unless the latest valid `battery.charging_state` is `PLUGGED_IN`.

## gRPC Flow

The gRPC layer maps Protobuf messages into domain models and delegates to the core.

Services:

- `ZonalIngestionService`
- `VehicleTelemetryService`
- `VehicleCommandService`

The mapping code lives in:

```text
service/src/main/kotlin/com/zonallink/service/ProtoMappers.kt
```

Keeping mapping out of the core prevents transport concerns from leaking into domain logic.
