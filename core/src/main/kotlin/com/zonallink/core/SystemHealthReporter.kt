package com.zonallink.core

enum class SystemHealthStatus {
    OK,
    DEGRADED,
    CRITICAL
}

data class SystemHealthReport(
    val status: SystemHealthStatus,
    val generatedAt: java.time.Instant,
    val controllerCount: Int,
    val offlineControllers: Int,
    val degradedControllers: Int,
    val staleSignals: Int,
    val rejectedMessages: Long,
    val reasons: List<String>
)

/**
 * Converts detailed diagnostics and state freshness into one operator-facing health status.
 */
class SystemHealthReporter {
    fun report(diagnostics: DiagnosticsSnapshot, snapshot: VehicleStateSnapshot): SystemHealthReport {
        val offline = diagnostics.controllers.count { it.status == ControllerStatus.OFFLINE }
        val degraded = diagnostics.controllers.count { it.status == ControllerStatus.DEGRADED }
        val staleSignals = snapshot.values.count { it.quality == ValueQuality.STALE }
        val rejected = diagnostics.controllers.sumOf { it.messagesRejected }

        val reasons = buildList {
            if (offline > 0) add("$offline controller(s) offline")
            if (degraded > 0) add("$degraded controller(s) degraded")
            if (staleSignals > 0) add("$staleSignals stale signal(s)")
            if (rejected > 0) add("$rejected rejected message(s)")
        }

        val status = when {
            offline > 0 || staleSignals >= 3 -> SystemHealthStatus.CRITICAL
            degraded > 0 || staleSignals > 0 || rejected > 0 -> SystemHealthStatus.DEGRADED
            else -> SystemHealthStatus.OK
        }

        return SystemHealthReport(
            status = status,
            generatedAt = diagnostics.generatedAt,
            controllerCount = diagnostics.controllers.size,
            offlineControllers = offline,
            degradedControllers = degraded,
            staleSignals = staleSignals,
            rejectedMessages = rejected,
            reasons = reasons
        )
    }
}
