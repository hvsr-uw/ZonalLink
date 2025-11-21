# Testing

ZonalLink's tests focus on core behavior rather than superficial coverage. The important risks are validation, stale state, controller lifecycle, command rejection, config parsing, and failure recovery.

Run the full suite:

```bash
gradle test
```

Run documentation and link checks:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-docs.ps1
```

## Test Areas

`TelemetryNormalizerTest`

Covers signal normalization and rejection:

- Fahrenheit to Celsius conversion
- unsupported signal names
- physically impossible values
- unsupported schema versions
- unknown units

`VehicleStateStoreTest`

Covers latest-state behavior:

- values remain valid inside their freshness window
- values become stale after `staleAt`

`ZonalLinkEngineTest`

Covers end-to-end ingestion effects:

- accepted telemetry updates state
- diagnostics count accepted messages

`ControllerLifecycleTest`

Covers controller sequencing and lifecycle:

- duplicate sequence rejection
- stale messages preserve newer state
- sequence gaps produce degraded diagnostics
- repeated rejected messages degrade a controller

`VehicleCommandValidatorTest`

Covers command/action validation:

- valid HVAC command acceptance
- charging command rejection when required state is missing

`HealthAndJournalTest`

Covers production-style failure handling:

- accepted telemetry is appended to the journal
- journal failure records diagnostics while telemetry continues
- rejected messages degrade aggregate health

`ConfigAndRetryPolicyTest`

Covers runtime configuration:

- capped exponential backoff
- invalid config rejection
- property-file loading

## Test Fakes

The test suite includes fakes in:

```text
core/src/test/kotlin/com/zonallink/core/TestFakes.kt
```

Current fakes:

- `RecordingTelemetryEventJournal`
- `FailingTelemetryEventJournal`
- `FakeTelemetryRepository`

These make failure recovery and persistence boundaries testable in-process.

## Integration Coverage Roadmap

The next coverage layer is transport-level integration testing. A practical approach is to start an in-process gRPC server and verify:

- streaming ingestion acknowledgements
- telemetry subscription delivery
- health endpoint
- command submission endpoint

The CLI is a review and operations aid. Core behavior is covered below it, and CLI output can be snapshot-tested if output stability becomes part of the contract.

## Environment Note

This repo expects Gradle to be available. If `gradle test` is unavailable on the local machine, install Gradle or add a Gradle wrapper.
