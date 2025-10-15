package com.zonallink.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter

data class TelemetrySubscription(
    val domains: Set<VehicleDomain> = emptySet(),
    val signalNames: Set<String> = emptySet()
) {
    fun matches(event: TelemetryEvent): Boolean {
        val domainMatch = domains.isEmpty() || event.domain in domains
        val signalMatch = signalNames.isEmpty() || event.signalName in signalNames
        return domainMatch && signalMatch
    }
}

/**
 * Bounded in-process telemetry fanout.
 *
 * Slow subscribers are isolated by the shared-flow buffer policy; the ingestion path should not
 * grow memory indefinitely just because a client stops draining updates.
 */
class TelemetryEventBus(
    replay: Int = 0,
    extraBufferCapacity: Int = 256
) {
    private val events = MutableSharedFlow<TelemetryEvent>(
        replay = replay,
        extraBufferCapacity = extraBufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun publish(event: TelemetryEvent): Boolean = events.tryEmit(event)

    fun subscribe(subscription: TelemetrySubscription): Flow<TelemetryEvent> =
        events.filter { subscription.matches(it) }
}
