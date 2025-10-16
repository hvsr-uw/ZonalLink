package com.zonallink.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

/**
 * Append-only audit boundary for normalized telemetry.
 *
 * Production users can replace this with SQLite, Kafka, or a vehicle data recorder while tests use
 * fake implementations.
 */
interface TelemetryEventJournal {
    fun append(event: TelemetryEvent)

    object Noop : TelemetryEventJournal {
        override fun append(event: TelemetryEvent) = Unit
    }
}

/**
 * Local JSONL journal used for demos, audit trails, and replay-friendly development runs.
 */
class JsonlTelemetryEventJournal(
    private val path: Path
) : TelemetryEventJournal {
    init {
        path.parent?.let { Files.createDirectories(it) }
    }

    override fun append(event: TelemetryEvent) {
        val line = buildString {
            append("{")
            append("\"recordedAt\":\"").append(Instant.now()).append("\",")
            append("\"eventId\":\"").append(event.eventId).append("\",")
            append("\"controllerId\":\"").append(event.sourceControllerId.value).append("\",")
            append("\"domain\":\"").append(event.domain).append("\",")
            append("\"zone\":\"").append(event.zone).append("\",")
            append("\"signal\":\"").append(event.signalName).append("\",")
            append("\"value\":\"").append(escape(event.value)).append("\",")
            append("\"unit\":\"").append(escape(event.unit)).append("\",")
            append("\"quality\":\"").append(event.quality).append("\",")
            append("\"receivedTimestamp\":\"").append(event.receivedTimestamp).append("\"")
            append("}")
        }
        Files.writeString(path, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
