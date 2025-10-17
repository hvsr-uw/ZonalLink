package com.zonallink.core

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Maintains latest-known vehicle state and applies signal-specific freshness windows at read time.
 *
 * Staleness is computed when snapshots are requested so clients see degradation even if no new
 * controller messages arrive.
 */
class VehicleStateStore(
    private val catalog: SignalCatalog = SignalCatalog(),
    private val clock: Clock = Clock.systemUTC(),
    private val repository: TelemetryRepository = InMemoryTelemetryRepository(),
    private val defaultStaleAfter: Duration = Duration.ofSeconds(3)
) {
    fun apply(event: TelemetryEvent) {
        val definition = catalog.definitionFor(event.signalName)
        val staleAt = event.receivedTimestamp.plus(definition?.staleAfter ?: defaultStaleAfter)

        repository.save(TelemetryValue(
            signalName = event.signalName,
            domain = event.domain,
            zone = event.zone,
            value = event.value,
            unit = event.unit,
            quality = event.quality,
            lastUpdatedAt = event.receivedTimestamp,
            staleAt = staleAt,
            sourceControllerId = event.sourceControllerId
        ))
    }

    fun snapshot(now: Instant = Instant.now(clock)): VehicleStateSnapshot {
        val aged = repository.findAll().map { value ->
            if (now.isAfter(value.staleAt)) {
                value.copy(quality = ValueQuality.STALE)
            } else {
                value
            }
        }.sortedWith(compareBy<TelemetryValue> { it.domain.name }.thenBy { it.signalName }.thenBy { it.zone.name })

        return VehicleStateSnapshot(now, aged)
    }
}
