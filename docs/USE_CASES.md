# Use Cases

ZonalLink models the service layer between zonal vehicle controllers and client-facing vehicle features. The project is useful because it turns noisy, controller-specific signals into stable telemetry and health information that downstream software can trust.

## Primary Users

Platform engineers use ZonalLink to validate how vehicle telemetry should move through a service boundary.

Infotainment engineers use the normalized snapshot and subscription APIs instead of binding directly to controller-specific message formats.

Test engineers use deterministic data-source scenarios to verify behavior under normal, charging, and degraded network conditions.

Reviewers use the CLI, tests, and docs to inspect design tradeoffs from a local development environment.

## Use Case: Vehicle Status Screen

An infotainment UI wants to display battery state of charge, door state, cabin temperature, headlights, speed, and thermal status.

The UI asks for a `VehicleStateSnapshot` and receives normalized values with quality metadata. Raw controller payloads, units, missing values, schema versions, and freshness rules stay inside the service layer.

Important behavior:

- values include `quality`
- values include `staleAt`
- stale values remain visible with explicit quality
- unsupported or malformed controller signals preserve good state

## Use Case: Live Telemetry Subscription

A client subscribes to telemetry updates for selected domains, such as `BATTERY` and `DOORS`.

ZonalLink filters events through `TelemetrySubscription` and publishes through a bounded event bus. Slow clients are isolated from the ingestion path.

## Use Case: Controller Health Review

A diagnostics tool wants to understand whether the telemetry layer is healthy.

ZonalLink reports:

- controller status: `UNKNOWN`, `ONLINE`, `DEGRADED`, `OFFLINE`
- last seen timestamp
- accepted and rejected message counts
- last accepted sequence number
- consecutive failures
- last error or degradation warning

This makes degraded behavior visible through the service API.

## Use Case: Bad Data Rejection

A controller sends `battery.state_of_charge = 149%`.

ZonalLink rejects the message as malformed because the signal catalog defines valid physical bounds. The state store keeps the last valid value, diagnostics records the rejection, and the service continues running.

## Use Case: Duplicate Or Out-Of-Order Messages

A controller sends sequence `10`, then later sends sequence `10` again.

ZonalLink rejects the duplicate as stale before normalization. This prevents old data from overwriting newer state.

If a controller jumps from sequence `7` to `10`, the message is accepted if the payload is valid, but diagnostics records a sequence gap and marks the controller degraded.

## Use Case: Command Validation

A client submits `SET_HVAC_TARGET_TEMPERATURE` with `21C`.

ZonalLink validates the payload and range, records command metrics, and returns an accepted response.

For `START_CHARGING`, ZonalLink checks current vehicle state. The command is rejected unless `battery.charging_state` is valid and reports `PLUGGED_IN`.

The project stops at validation. Actual actuation would belong behind a separate safety-reviewed integration boundary.

## Use Case: Local Audit Journal

During a demo or integration run, a reviewer can write normalized telemetry to a JSONL journal:

```bash
gradle :cli:run --args="demo --degraded --journal build/telemetry/events.jsonl"
```

The journal is a boundary, not the center of the design. It can be replaced by SQLite, RocksDB, Kafka, or a vehicle data recorder.
