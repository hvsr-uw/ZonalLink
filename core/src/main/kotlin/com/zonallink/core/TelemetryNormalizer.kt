package com.zonallink.core

import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * Converts controller-facing raw payloads into canonical telemetry events.
 *
 * This class is deliberately strict about schema versions, units, physical ranges, and enum-like
 * string values. Guessing at vehicle data is more dangerous than rejecting it with diagnostics.
 */
class TelemetryNormalizer(
    private val catalog: SignalCatalog = SignalCatalog(),
    private val clock: Clock = Clock.systemUTC(),
    private val supportedSchemaVersions: IntRange = 1..1
) {
    fun normalize(message: RawZonalMessage): IngestionResult {
        val receivedAt = Instant.now(clock)
        val warnings = mutableListOf<String>()

        if (message.controller.schemaVersion !in supportedSchemaVersions) {
            return rejected(ValueQuality.UNSUPPORTED, "unsupported schema version ${message.controller.schemaVersion}")
        }

        if (message.signalName.isBlank()) {
            return rejected(ValueQuality.MALFORMED, "signal name is blank")
        }

        if (message.value == null) {
            return rejected(ValueQuality.MISSING, "message has no signal payload")
        }

        val definition = catalog.definitionFor(message.signalName)
            ?: return rejected(ValueQuality.UNSUPPORTED, "unsupported signal '${message.signalName}'")

        if (definition.domain != message.controller.domain) {
            warnings += "controller domain ${message.controller.domain} does not match catalog domain ${definition.domain}"
        }

        val typed = coerceValue(message.value, definition)
            ?: return rejected(ValueQuality.MALFORMED, "payload type does not match ${definition.expectedType}")

        if (message.controllerTimestamp.isAfter(receivedAt.plusSeconds(30))) {
            warnings += "controller timestamp is more than 30 seconds in the future"
        }

        val quality = validateValue(typed, definition, warnings)
        val event = TelemetryEvent(
            eventId = UUID.randomUUID().toString(),
            sourceControllerId = message.controller.controllerId,
            domain = definition.domain,
            zone = message.controller.zone,
            signalName = definition.name,
            value = typed.value,
            unit = typed.unit,
            quality = quality,
            eventTimestamp = message.controllerTimestamp,
            receivedTimestamp = receivedAt,
            warnings = warnings.toList()
        )

        return IngestionResult(
            accepted = quality != ValueQuality.MALFORMED,
            event = event,
            quality = quality,
            warnings = warnings.toList()
        )
    }

    private fun rejected(quality: ValueQuality, warning: String): IngestionResult =
        IngestionResult(false, null, quality, listOf(warning))

    private fun coerceValue(value: RawSignalValue, definition: SignalDefinition): NormalizedValue? =
        when (definition.expectedType) {
            SignalType.NUMBER -> {
                val numeric = value as? RawSignalValue.NumberValue ?: return null
                if (!numeric.value.isFinite()) return null
                if (definition.requiresUnit && numeric.unit.isBlank()) return null
                val canonical = convertNumber(numeric.value, numeric.unit, definition.canonicalUnit) ?: return null
                NormalizedValue(formatNumber(canonical), definition.canonicalUnit)
            }
            SignalType.BOOLEAN -> {
                val bool = value as? RawSignalValue.BooleanValue ?: return null
                NormalizedValue(bool.value.toString(), "")
            }
            SignalType.STRING -> {
                val text = value as? RawSignalValue.StringValue ?: return null
                if (text.value.isBlank()) return null
                NormalizedValue(text.value.trim().uppercase(Locale.US), "")
            }
        }

    private fun convertNumber(value: Double, fromUnit: String, toUnit: String): Double? =
        when {
            fromUnit == toUnit -> value
            fromUnit.isBlank() && toUnit.isBlank() -> value
            fromUnit == "F" && toUnit == "C" -> (value - 32.0) * 5.0 / 9.0
            fromUnit == "mph" && toUnit == "km/h" -> value * 1.609344
            fromUnit == "mV" && toUnit == "V" -> value / 1000.0
            else -> null
        }

    private fun validateValue(
        value: NormalizedValue,
        definition: SignalDefinition,
        warnings: MutableList<String>
    ): ValueQuality {
        val numeric = value.value.toDoubleOrNull()
        if (numeric != null) {
            if (definition.min != null && numeric < definition.min) {
                warnings += "value $numeric is below expected minimum ${definition.min}"
                return ValueQuality.MALFORMED
            }
            if (definition.max != null && numeric > definition.max) {
                warnings += "value $numeric is above expected maximum ${definition.max}"
                return ValueQuality.MALFORMED
            }
        }
        if (definition.allowedStringValues.isNotEmpty() && value.value !in definition.allowedStringValues) {
            warnings += "value '${value.value}' is not one of ${definition.allowedStringValues.sorted()}"
            return ValueQuality.MALFORMED
        }
        return ValueQuality.VALID
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
        }

    private data class NormalizedValue(val value: String, val unit: String)
}
