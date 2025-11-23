# Reviewer Guide

This guide is written for a senior engineer reviewing ZonalLink as an engineering project.

## Fastest Way To Understand The Project

Start with the Protobuf contract:

```text
proto/src/main/proto/zonal_link.proto
```

Then inspect the orchestration path:

```text
core/src/main/kotlin/com/zonallink/core/ZonalLinkEngine.kt
```

Then inspect the risky logic:

```text
core/src/main/kotlin/com/zonallink/core/DiagnosticsRegistry.kt
core/src/main/kotlin/com/zonallink/core/TelemetryNormalizer.kt
core/src/main/kotlin/com/zonallink/core/VehicleStateStore.kt
core/src/main/kotlin/com/zonallink/core/VehicleCommand.kt
```

Finally, read the tests:

```text
core/src/test/kotlin/com/zonallink/core
```

## Suggested Demo

Run the degraded scenario:

```bash
gradle :cli:run --args="demo --degraded --seed 7 --config samples/zonallink.properties --journal build/telemetry/events.jsonl"
```

Run the documentation checks:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-docs.ps1
```

Look for:

- accepted messages
- rejected malformed messages
- unsupported signal handling
- warning-bearing accepted messages
- final state snapshot
- controller diagnostics
- aggregate health
- command validation result
- JSONL journal output

## What Should Stand Out

Raw and canonical data are separated.

Controller-specific payloads stay out of client APIs. Raw messages become canonical telemetry events only after validation.

Validation is catalog-driven.

The signal catalog defines expected type, units, bounds, allowed values, and freshness. This is cleaner than scattering validation across transport handlers.

Controller lifecycle is explicit.

The service tracks online, degraded, and offline states. It also tracks sequence numbers, consecutive failures, and last errors.

State is quality-aware.

Snapshots return value quality and `staleAt`, which are essential for vehicle UI behavior.

Failure handling is deliberate.

Bad telemetry is rejected while the service continues processing. Journal failure is reported and ingestion continues. Slow subscribers are bounded.

Commands are modeled separately.

The command path validates requests against typed payloads and current state. Vehicle actuation belongs behind a separate safety-reviewed boundary.

## Tradeoffs To Notice

The state repository is in-memory by default. That keeps the project runnable and testable while preserving a replacement boundary through `TelemetryRepository`.

The JSONL journal is a durable audit boundary that avoids introducing a database dependency.

The gRPC server is included. The CLI provides the fastest local review path and keeps the core independent from transport.

The local simulator is deterministic. Repeatability is more valuable than randomness for review and tests.

## Questions A Reviewer Might Ask

How would this become an Android bound service?

Use the core ports. An Android service would implement permission checks, binder lifecycle handling, and client callbacks around `VehicleTelemetryPort` and `VehicleCommandPort`.

How would persistence be made durable?

Replace `TelemetryRepository` with SQLite or RocksDB for latest state, and replace `TelemetryEventJournal` with a production event recorder.

How would auth work?

The gRPC boundary is the right place for mTLS, caller identity, and authorization. Command submission especially should be gated.

How would high-frequency signals be handled?

The event bus is already bounded. A production version would add per-signal throttling, coalescing, and priority policies.

## Red Flags This Project Avoids

- no one-file local data source presented as the service
- no raw payloads exposed as client API
- no silent acceptance of unsupported units
- no stale data overwriting newer state
- no unbounded subscriber queues
- command validation before any actuation boundary
