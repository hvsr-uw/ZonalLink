# How It Works

ZonalLink has one central job: turn raw zonal controller messages into trustworthy vehicle telemetry.

The implementation is organized around a core engine. Transports such as CLI and gRPC adapt into that engine. The engine handles sequencing, validation, normalization, state updates, event publication, diagnostics, journaling, and command validation.

## The Happy Path

1. The local simulator emits a `RawZonalMessage`.
2. `ZonalLinkEngine` asks `DiagnosticsRegistry` to inspect the message envelope.
3. The registry rejects duplicates and older sequence numbers.
4. `TelemetryNormalizer` validates schema version, signal name, payload type, unit, bounds, and enum-like string values.
5. A valid message becomes a canonical `TelemetryEvent`.
6. `VehicleStateStore` saves the latest value and computes when it becomes stale.
7. `TelemetryEventBus` publishes the event to subscribers.
8. `TelemetryEventJournal` optionally appends the event to a JSONL audit file.
9. `DiagnosticsRegistry` records accepted/rejected counts and controller health.

## The Signal Catalog

The signal catalog is the project's contract for what telemetry is stable enough to expose.

It defines:

- signal name
- domain
- expected payload type
- canonical unit
- numeric bounds
- allowed string values
- stale timeout

This keeps validation centralized. Adding a new public signal means adding a deliberate catalog entry instead of scattering special cases through parsing code.

## Message Quality

ZonalLink uses `ValueQuality` to make degradation visible:

- `VALID`
- `STALE`
- `ESTIMATED`
- `MISSING`
- `MALFORMED`
- `UNSUPPORTED`

Malformed and unsupported messages are rejected. Stale values can still appear in snapshots, but they are clearly marked as stale.

## Controller Lifecycle

Controllers are tracked independently from signals.

Controller states:

- `UNKNOWN`: known to the registry and awaiting healthy telemetry or heartbeat
- `ONLINE`: recent valid telemetry or heartbeat
- `DEGRADED`: accepted with warnings or repeated rejections
- `OFFLINE`: no recent activity within the configured offline timeout

This distinction matters. A controller can be online while one signal is stale, or degraded because it skipped sequence numbers while still producing some usable telemetry.

## Commands

Commands use a separate model from telemetry:

- `VehicleCommand`
- `VehicleCommandType`
- `CommandPayload`
- `CommandResult`

The current implementation validates commands and reports metrics. Hardware actuation belongs behind a safety-reviewed adapter.

Examples:

- HVAC target temperature must be between `15C` and `32C`.
- Headlight command requires a boolean payload.
- Start charging requires the current state to report `battery.charging_state=PLUGGED_IN`.

## Configuration

`ZonalLinkConfig` owns runtime settings:

- supported schema versions
- event bus buffer capacity
- controller offline timeout
- default signal stale timeout
- retry policy

The sample config lives at:

```text
samples/zonallink.properties
```

## Diagnostics

Diagnostics are meant for humans and machines.

The service reports:

- controller health
- accepted/rejected message counts
- sequence gaps
- journal failures
- command submission/rejection metrics
- aggregate health status

The CLI prints these at the end of a demo run. The gRPC service exposes diagnostics and health through `VehicleTelemetryService`.
