# Architecture Overview

ZonalLink is built as a layered JVM service with a transport-independent core.

```text
Local Simulator
        |
        v
RawZonalMessage
        |
        v
ZonalLinkEngine
        |
        +--> DiagnosticsRegistry
        +--> TelemetryNormalizer
        +--> VehicleStateStore
        +--> TelemetryEventBus
        +--> TelemetryEventJournal
        +--> VehicleCommandValidator
        +--> SystemHealthReporter
        |
        v
CLI / gRPC / Android Bound Service Adapter
```

## Module Responsibilities

`proto`

Defines the public gRPC and Protobuf contracts. This is the API a client or remote controller would integrate with.

`core`

Contains the actual service behavior. It has no dependency on gRPC. This is where domain modeling, validation, state, diagnostics, health, config, commands, and ports live.

`simulator`

Provides a local simulator and deterministic data source for normal driving, charging, and degraded network scenarios.

`service`

Adapts gRPC requests into the core engine. It owns Protobuf mapping and server startup.

`cli`

Provides a direct local path for running scenarios and inspecting service behavior.

## Core Boundaries

`ZonalLinkEngine`

The application service. It coordinates ingestion and command validation while delegating focused responsibilities to collaborators.

`DiagnosticsRegistry`

Tracks controller lifecycle, sequence numbers, counters, failures, and degradation reasons. It rejects duplicate or older messages before normalization.

`TelemetryNormalizer`

Converts raw payloads into canonical telemetry. It is intentionally strict about schema versions, units, types, bounds, and supported values.

`VehicleStateStore`

Maintains latest-known state and applies staleness rules at snapshot time.

`TelemetryRepository`

A Java persistence boundary for latest-known state. The default implementation is in-memory, but the interface is narrow enough to replace with SQLite, RocksDB, or another local store.

`TelemetryEventJournal`

An append-only event audit boundary. The JSONL implementation is useful for local review and replay-style workflows.

`TelemetryEventBus`

A bounded fanout mechanism for subscribers. It prevents slow consumers from creating unbounded memory pressure.

`VehicleCommandValidator`

Validates command/action requests against payload rules and current vehicle state.

`SystemHealthReporter`

Summarizes diagnostics and state into a reviewer-friendly health report.

## Ports

The core exposes narrow ports:

- `ZonalIngestionPort`
- `VehicleTelemetryPort`
- `VehicleCommandPort`

These ports make the core easy to adapt behind gRPC, CLI tools, tests, or an Android service.

## Tradeoffs

The project uses an in-memory latest-state repository by default. That keeps the service easy to run and test, while still preserving a persistence abstraction.

The JSONL journal provides a practical audit boundary while keeping storage concerns isolated.

Commands are validated at the service boundary. Hardware actuation belongs behind a separate safety-reviewed integration layer.

The local simulator is deterministic by seed. Repeatable telemetry makes demos, tests, and review easier to reason about than randomized traffic.
