# Extension Points

ZonalLink is built around clear replacement boundaries. This document explains where to extend it while preserving the architecture.

## Add A New Signal

Edit:

```text
core/src/main/kotlin/com/zonallink/core/SignalCatalog.kt
```

Add a `SignalDefinition` with:

- name
- domain
- expected type
- canonical unit
- min/max if numeric
- allowed string values if enum-like
- stale timeout

Then add tests in `TelemetryNormalizerTest` for valid and invalid values.

## Add A New Controller Scenario

Edit:

```text
simulator/src/main/kotlin/com/zonallink/simulator/ScenarioProfile.kt
```

Add a new `ScenarioKind` and a scenario step list.

Keep scenarios deterministic. If variation is useful, drive it from the existing seed so demos are repeatable.

## Replace Latest-State Persistence

Implement:

```text
core/src/main/java/com/zonallink/core/TelemetryRepository.java
```

The default implementation is:

```text
core/src/main/kotlin/com/zonallink/core/InMemoryTelemetryRepository.kt
```

Good production candidates:

- SQLite
- RocksDB
- memory-mapped local store
- replicated state cache

## Replace The Event Journal

Implement:

```text
core/src/main/kotlin/com/zonallink/core/TelemetryEventJournal.kt
```

The included `JsonlTelemetryEventJournal` is a local JSONL implementation. Production candidates:

- append-only binary log
- Kafka producer
- cloud event stream
- vehicle diagnostic recorder

## Add A Command Type

Edit:

```text
core/src/main/kotlin/com/zonallink/core/VehicleCommand.kt
proto/src/main/proto/zonal_link.proto
service/src/main/kotlin/com/zonallink/service/ProtoMappers.kt
```

Add:

- enum value
- typed payload if needed
- validation rule
- Protobuf mapping
- unit tests

Keep actuation logic out of the validator. Validation and actuation are separate boundaries.

## Add A New Transport

Use the ports:

```text
ZonalIngestionPort
VehicleTelemetryPort
VehicleCommandPort
```

These are defined in:

```text
core/src/main/kotlin/com/zonallink/core/Ports.kt
```

Possible adapters:

- Android bound service
- REST API
- WebSocket telemetry stream
- embedded diagnostics console
- test harness

## Add gRPC Integration Tests

The current tests focus on core behavior. To add transport tests:

1. Start `ZonalLinkServer` on an ephemeral port.
2. Create gRPC clients from generated stubs.
3. Stream messages through `ZonalIngestionService`.
4. Assert acknowledgements, snapshot values, diagnostics, and health.

This would be a strong next step because it verifies Protobuf mapping and coroutine gRPC behavior.

## Add Observability Export

Current diagnostics are in-memory and API-visible. Production export options:

- OpenTelemetry metrics
- structured log events
- Prometheus scrape endpoint
- Android stats/logcat bridge

The natural source is `DiagnosticsRegistry.snapshot()` and `SystemHealthReporter.report()`.

## Add Security

Security belongs at the integration boundary.

For gRPC:

- mTLS
- caller identity
- command authorization
- rate limiting

For Android:

- signature permissions
- binder death handling
- caller UID/package checks

The core remains policy-aware while platform-specific security stays in the adapter layer.
