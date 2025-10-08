package com.zonallink.core

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryTelemetryRepository : TelemetryRepository {
    private val values = ConcurrentHashMap<SignalKey, TelemetryValue>()

    override fun save(value: TelemetryValue) {
        values[SignalKey(value.domain, value.zone, value.signalName)] = value
    }

    override fun findAll(): List<TelemetryValue> = values.values.toList()

    override fun snapshot(generatedAt: Instant): VehicleStateSnapshot =
        VehicleStateSnapshot(generatedAt, findAll())
}
