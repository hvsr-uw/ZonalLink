# ZonalLink

ZonalLink is a vehicle telemetry service layer.

It receives raw messages from zonal vehicle controllers, checks that the data is valid, converts it into a clean vehicle-state format, and exposes that state through a service API. The project includes a local simulator so the system can be run and reviewed without vehicle hardware.

In plain English: ZonalLink sits between vehicle controllers and infotainment features.

```text
Local controller telemetry
        -> validation
        -> normalization
        -> latest vehicle state
        -> diagnostics and gRPC APIs
```

## Why This Project Exists

Modern vehicles use many controllers across different physical zones of the vehicle. A battery controller, door controller, HVAC controller, lighting controller, and chassis controller may all send different kinds of messages.

Infotainment apps should not need to understand every raw controller format. They need a stable service that answers questions like:

- What is the current battery state of charge?
- Is any door open?
- Is this signal fresh or stale?
- Which controller is degraded or offline?
- Was a client command valid for the current vehicle state?

ZonalLink provides that service layer.

## Main Use Case

A vehicle status screen wants live vehicle data.

Instead of reading raw controller messages directly, the screen can use ZonalLink to get:

- normalized telemetry events
- a current vehicle-state snapshot
- signal quality such as `VALID`, `STALE`, `MALFORMED`, or `UNSUPPORTED`
- controller health such as `ONLINE`, `DEGRADED`, or `OFFLINE`
- diagnostics for rejected messages, sequence gaps, and stale data

## What The Project Includes

- Kotlin/JVM core service logic
- Java persistence boundary through `TelemetryRepository`
- Protobuf and gRPC service contracts
- Local deterministic telemetry source
- Signal catalog with validation rules
- Latest-state cache with stale-data handling
- Controller lifecycle and sequence tracking
- Bounded event bus for telemetry subscribers
- JSONL telemetry journal boundary
- Command validation model
- Runtime configuration file
- CLI demo runner
- Unit tests for important behavior
- Practical documentation for reviewers

## Repository Layout

```text
ZonalLink/
  proto/       Protobuf and gRPC contracts
  core/        Domain model, validation, state, diagnostics, commands
  service/     gRPC adapters and server bootstrap
  simulator/   Local deterministic telemetry source
  cli/         Demo runner and service launcher
  samples/     Example config and scenario data
  docs/        Architecture, data flow, testing, and reviewer docs
  scripts/     Local verification helpers
```

## Modules

| Module | What it does |
| --- | --- |
| `proto` | Defines the public Protobuf and gRPC API |
| `core` | Contains the main service logic and domain model |
| `service` | Adapts gRPC requests into the core engine |
| `simulator` | Produces deterministic local telemetry scenarios |
| `cli` | Runs demos and starts the service |
| `samples` | Contains example config and scenario files |
| `docs` | Explains how the project works |

## Requirements

- JDK 21
- Gradle 8.x on `PATH`
- PowerShell for the documentation verification script

Use a local Gradle installation, or add a Gradle wrapper before running the JVM commands.

## Quick Start

Run all tests:

```bash
gradle test
```

Run the normal driving demo:

```bash
gradle :cli:run --args="demo --normal"
```

Run the charging demo:

```bash
gradle :cli:run --args="demo --charging"
```

Run the degraded-network demo:

```bash
gradle :cli:run --args="demo --degraded"
```

Run with explicit config, deterministic seed, and a telemetry journal:

```bash
gradle :cli:run --args="demo --degraded --seed 7 --config samples/zonallink.properties --journal build/telemetry/events.jsonl"
```

Start the gRPC service:

```bash
gradle :cli:run --args="serve --port 7575 --config samples/zonallink.properties"
```

Verify documentation links and cleanup checks:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-docs.ps1
```

## What You Should See In The Demo

The CLI sends local telemetry through the same engine used by the gRPC service.

It prints:

- accepted and rejected messages
- normalized telemetry events
- final vehicle-state snapshot
- controller diagnostics
- aggregate health status
- command validation result

The degraded scenario includes invalid or unsupported telemetry so the failure handling is visible.

## Important Concepts

### Raw Message

A message from a controller. It includes controller metadata, signal name, value, timestamp, and sequence number.

### Telemetry Event

A validated, normalized vehicle signal. This is what downstream services should consume.

### Vehicle Snapshot

The latest known state of the vehicle. Each value includes quality and a `staleAt` timestamp.

### Controller Diagnostics

Health information for each controller, including accepted messages, rejected messages, sequence gaps, and lifecycle state.

### Command Validation

Client commands are checked against payload rules and current vehicle state. For example, `START_CHARGING` is rejected unless the vehicle reports that it is plugged in.

## Key Files To Review

Start here:

1. [proto/src/main/proto/zonal_link.proto](proto/src/main/proto/zonal_link.proto) - public API
2. [core/src/main/kotlin/com/zonallink/core/ZonalLinkEngine.kt](core/src/main/kotlin/com/zonallink/core/ZonalLinkEngine.kt) - main orchestration
3. [core/src/main/kotlin/com/zonallink/core/TelemetryNormalizer.kt](core/src/main/kotlin/com/zonallink/core/TelemetryNormalizer.kt) - validation and normalization
4. [core/src/main/kotlin/com/zonallink/core/DiagnosticsRegistry.kt](core/src/main/kotlin/com/zonallink/core/DiagnosticsRegistry.kt) - controller lifecycle and sequence tracking
5. [core/src/main/kotlin/com/zonallink/core/VehicleCommand.kt](core/src/main/kotlin/com/zonallink/core/VehicleCommand.kt) - command validation
6. [core/src/test/kotlin/com/zonallink/core](core/src/test/kotlin/com/zonallink/core) - unit tests

## Configuration

Sample config:

[samples/zonallink.properties](samples/zonallink.properties)

It controls:

- supported schema versions
- event bus buffer size
- controller offline timeout
- default signal stale timeout
- retry/backoff settings

## Documentation

| Document | Purpose |
| --- | --- |
| [Use Cases](docs/USE_CASES.md) | What the project is for |
| [How It Works](docs/HOW_IT_WORKS.md) | Step-by-step behavior |
| [Architecture Overview](docs/ARCHITECTURE_OVERVIEW.md) | Main modules and boundaries |
| [Data Flow](docs/DATA_FLOW.md) | How telemetry moves through the system |
| [Testing](docs/TESTING.md) | Test strategy and coverage |
| [Reviewer Guide](docs/REVIEWER_GUIDE.md) | Best path for code review |
| [Extension Points](docs/EXTENSION_POINTS.md) | How to add signals, storage, commands, or adapters |

## Current Scope

ZonalLink focuses on the connectivity service layer:

- ingest telemetry
- validate data
- normalize signals
- cache latest state
- publish events
- report diagnostics
- validate commands

Vehicle actuation is intentionally outside this core layer. In a vehicle program, actuation would sit behind a separate safety-reviewed boundary with authentication, authorization, arbitration, and hardware integration.
