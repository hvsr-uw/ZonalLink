package com.zonallink.core

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks controller lifecycle and ingestion counters.
 *
 * The registry doubles as the sequencing gate for each controller: duplicate or older messages are
 * rejected before normalization so stale data cannot overwrite newer vehicle state.
 */
class DiagnosticsRegistry(
    private val clock: Clock = Clock.systemUTC(),
    private val offlineAfter: Duration = Duration.ofSeconds(10)
) {
    private val controllers = ConcurrentHashMap<ControllerId, ControllerCounters>()
    private val counters = ConcurrentHashMap<MetricKey, AtomicLong>()

    fun inspect(message: RawZonalMessage): MessageInspection {
        val existing = controllers[message.controller.controllerId]
        val lastSequence = existing?.lastSequenceNumber

        if (message.sequenceNumber <= 0) {
            return MessageInspection(false, ValueQuality.MALFORMED, listOf("sequence number must be positive"))
        }

        if (lastSequence != null && message.sequenceNumber <= lastSequence) {
            return MessageInspection(
                accepted = false,
                quality = ValueQuality.STALE,
                warnings = listOf("sequence ${message.sequenceNumber} is not newer than last accepted sequence $lastSequence")
            )
        }

        val warnings = mutableListOf<String>()
        if (lastSequence != null && message.sequenceNumber > lastSequence + 1) {
            val skipped = message.sequenceNumber - lastSequence - 1
            warnings += "detected gap of $skipped message(s) after sequence $lastSequence"
            increment("messages.sequence_gap", mapOf("controller_id" to message.controller.controllerId.value), skipped)
        }

        return MessageInspection(true, ValueQuality.VALID, warnings)
    }

    fun markHeartbeat(controller: ControllerMetadata, at: Instant = Instant.now(clock)) {
        controllers.compute(controller.controllerId) { _, existing ->
            val base = existing ?: ControllerCounters(controller)
            base.copy(controller = controller, lastSeenAt = at, status = ControllerStatus.ONLINE)
        }
        increment("controller.heartbeat", mapOf("controller_id" to controller.controllerId.value))
    }

    fun markAccepted(
        controller: ControllerMetadata,
        sequenceNumber: Long,
        warnings: List<String>,
        at: Instant = Instant.now(clock)
    ) {
        controllers.compute(controller.controllerId) { _, existing ->
            val base = existing ?: ControllerCounters(controller)
            base.copy(
                controller = controller,
                lastSeenAt = at,
                status = if (warnings.isEmpty()) ControllerStatus.ONLINE else ControllerStatus.DEGRADED,
                accepted = base.accepted + 1,
                lastSequenceNumber = sequenceNumber,
                consecutiveFailures = 0,
                lastError = warnings.lastOrNull()
            )
        }
        increment("messages.accepted", mapOf("domain" to controller.domain.name))
        if (warnings.isNotEmpty()) {
            increment("messages.accepted_with_warning", mapOf("domain" to controller.domain.name))
        }
    }

    fun markRejected(controller: ControllerMetadata?, quality: ValueQuality, warnings: List<String> = emptyList()) {
        if (controller != null) {
            controllers.compute(controller.controllerId) { _, existing ->
                val base = existing ?: ControllerCounters(controller)
                val failures = base.consecutiveFailures + 1
                base.copy(
                    controller = controller,
                    lastSeenAt = Instant.now(clock),
                    status = if (failures >= 3) ControllerStatus.DEGRADED else base.status,
                    rejected = base.rejected + 1,
                    consecutiveFailures = failures,
                    lastError = warnings.lastOrNull() ?: quality.name
                )
            }
        }
        increment("messages.rejected", mapOf("quality" to quality.name))
    }

    fun increment(name: String, tags: Map<String, String> = emptyMap(), amount: Long = 1) {
        counters.computeIfAbsent(MetricKey(name, tags.toSortedMap())) { AtomicLong(0) }.addAndGet(amount)
    }

    fun snapshot(): DiagnosticsSnapshot {
        val now = Instant.now(clock)
        val controllerHealth = controllers.values.map { counter ->
            val status = if (now.minus(offlineAfter).isAfter(counter.lastSeenAt)) {
                ControllerStatus.OFFLINE
            } else {
                counter.status
            }
            ControllerHealth(
                controller = counter.controller,
                lastSeenAt = counter.lastSeenAt,
                status = status,
                messagesAccepted = counter.accepted,
                messagesRejected = counter.rejected,
                lastSequenceNumber = counter.lastSequenceNumber,
                consecutiveFailures = counter.consecutiveFailures,
                lastError = counter.lastError
            )
        }.sortedBy { it.controller.controllerId.value }

        val metricPoints = counters.map { (key, value) ->
            MetricPoint(key.name, value.get().toDouble(), key.tags)
        }.sortedWith(compareBy<MetricPoint> { it.name }.thenBy { it.tags.toString() })

        return DiagnosticsSnapshot(now, controllerHealth, metricPoints)
    }

    private data class ControllerCounters(
        val controller: ControllerMetadata,
        val lastSeenAt: Instant = Instant.EPOCH,
        val status: ControllerStatus = ControllerStatus.UNKNOWN,
        val accepted: Long = 0,
        val rejected: Long = 0,
        val lastSequenceNumber: Long? = null,
        val consecutiveFailures: Int = 0,
        val lastError: String? = null
    )

    private data class MetricKey(
        val name: String,
        val tags: Map<String, String>
    )
}
