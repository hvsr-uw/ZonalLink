package com.zonallink.core

import java.time.Instant

class RecordingTelemetryEventJournal : TelemetryEventJournal {
    val events = mutableListOf<TelemetryEvent>()

    override fun append(event: TelemetryEvent) {
        events += event
    }
}

class FailingTelemetryEventJournal : TelemetryEventJournal {
    override fun append(event: TelemetryEvent) {
        error("journal unavailable")
    }
}

class FakeTelemetryRepository : TelemetryRepository {
    private val values = linkedMapOf<SignalKey, TelemetryValue>()

    override fun save(value: TelemetryValue) {
        values[SignalKey(value.domain, value.zone, value.signalName)] = value
    }

    override fun findAll(): List<TelemetryValue> = values.values.toList()

    override fun snapshot(generatedAt: Instant): VehicleStateSnapshot =
        VehicleStateSnapshot(generatedAt, findAll())
}
