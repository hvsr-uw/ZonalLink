package com.zonallink.core;

import java.time.Instant;
import java.util.List;

/**
 * Persistence boundary for latest-known vehicle state.
 *
 * <p>The core intentionally depends on this narrow Java interface instead of a concrete map. A
 * production deployment can replace the in-memory implementation with SQLite, RocksDB, or a
 * replicated event log without changing normalization, transport, or client subscription code.
 */
public interface TelemetryRepository {
    void save(TelemetryValue value);

    List<TelemetryValue> findAll();

    VehicleStateSnapshot snapshot(Instant generatedAt);
}
