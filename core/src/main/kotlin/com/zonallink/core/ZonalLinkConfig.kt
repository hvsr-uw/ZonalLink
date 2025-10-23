package com.zonallink.core

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties

/**
 * Runtime configuration for the core engine.
 *
 * The defaults are conservative for local simulation, while `fromPropertiesFile` gives the service
 * a production-style configuration boundary without binding core logic to a particular framework.
 */
data class ZonalLinkConfig(
    val supportedSchemaVersions: IntRange = 1..1,
    val eventBusBufferCapacity: Int = 256,
    val controllerOfflineAfter: Duration = Duration.ofSeconds(10),
    val defaultSignalStaleAfter: Duration = Duration.ofSeconds(3),
    val retryPolicy: RetryPolicy = RetryPolicy()
) {
    init {
        require(eventBusBufferCapacity > 0) { "eventBusBufferCapacity must be positive" }
        require(!controllerOfflineAfter.isNegative && !controllerOfflineAfter.isZero) {
            "controllerOfflineAfter must be positive"
        }
        require(!defaultSignalStaleAfter.isNegative && !defaultSignalStaleAfter.isZero) {
            "defaultSignalStaleAfter must be positive"
        }
    }

    companion object {
        fun fromPropertiesFile(path: Path): ZonalLinkConfig {
            val properties = Properties()
            Files.newInputStream(path).use { properties.load(it) }

            return ZonalLinkConfig(
                supportedSchemaVersions = parseRange(properties.getProperty("schema.supportedVersions"), 1..1),
                eventBusBufferCapacity = properties.getProperty("eventBus.bufferCapacity")?.toInt() ?: 256,
                controllerOfflineAfter = properties.duration("controller.offlineAfter", Duration.ofSeconds(10)),
                defaultSignalStaleAfter = properties.duration("signal.defaultStaleAfter", Duration.ofSeconds(3)),
                retryPolicy = RetryPolicy(
                    maxAttempts = properties.getProperty("retry.maxAttempts")?.toInt() ?: 4,
                    initialBackoff = properties.duration("retry.initialBackoff", Duration.ofMillis(100)),
                    maxBackoff = properties.duration("retry.maxBackoff", Duration.ofSeconds(2)),
                    multiplier = properties.getProperty("retry.multiplier")?.toDouble() ?: 2.0
                )
            )
        }

        private fun parseRange(raw: String?, default: IntRange): IntRange {
            if (raw.isNullOrBlank()) return default
            val parts = raw.split("..", "-").map { it.trim() }.filter { it.isNotEmpty() }
            return when (parts.size) {
                1 -> parts[0].toInt()..parts[0].toInt()
                2 -> parts[0].toInt()..parts[1].toInt()
                else -> error("schema.supportedVersions must be a single version or range")
            }
        }

        private fun Properties.duration(key: String, default: Duration): Duration {
            val raw = getProperty(key) ?: return default
            return Duration.parse(raw)
        }
    }
}

data class RetryPolicy(
    val maxAttempts: Int = 4,
    val initialBackoff: Duration = Duration.ofMillis(100),
    val maxBackoff: Duration = Duration.ofSeconds(2),
    val multiplier: Double = 2.0
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(!initialBackoff.isNegative && !initialBackoff.isZero) { "initialBackoff must be positive" }
        require(!maxBackoff.isNegative && !maxBackoff.isZero) { "maxBackoff must be positive" }
        require(multiplier >= 1.0) { "multiplier must be at least 1.0" }
    }

    fun delayBeforeAttempt(attempt: Int): Duration {
        require(attempt >= 2) { "attempt is 1-based; first attempt has no delay" }
        val exponential = initialBackoff.toMillis() * Math.pow(multiplier, (attempt - 2).toDouble())
        return Duration.ofMillis(exponential.toLong().coerceAtMost(maxBackoff.toMillis()))
    }
}
