package com.zonallink.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.time.Duration
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class ConfigAndRetryPolicyTest {
    @Test
    fun `retry policy caps exponential backoff`() {
        val policy = RetryPolicy(
            maxAttempts = 5,
            initialBackoff = Duration.ofMillis(100),
            maxBackoff = Duration.ofMillis(250),
            multiplier = 2.0
        )

        assertEquals(Duration.ofMillis(100), policy.delayBeforeAttempt(2))
        assertEquals(Duration.ofMillis(200), policy.delayBeforeAttempt(3))
        assertEquals(Duration.ofMillis(250), policy.delayBeforeAttempt(4))
    }

    @Test
    fun `configuration rejects invalid event bus capacity`() {
        assertFailsWith<IllegalArgumentException> {
            ZonalLinkConfig(eventBusBufferCapacity = 0)
        }
    }

    @Test
    fun `loads runtime configuration from properties file`() {
        val path = createTempFile(prefix = "zonallink", suffix = ".properties")
        path.writeText(
            """
            schema.supportedVersions=1..2
            eventBus.bufferCapacity=128
            controller.offlineAfter=PT5S
            signal.defaultStaleAfter=PT2S
            retry.maxAttempts=3
            retry.initialBackoff=PT0.05S
            retry.maxBackoff=PT1S
            retry.multiplier=3.0
            """.trimIndent()
        )

        val config = ZonalLinkConfig.fromPropertiesFile(path)

        assertEquals(1..2, config.supportedSchemaVersions)
        assertEquals(128, config.eventBusBufferCapacity)
        assertEquals(Duration.ofSeconds(5), config.controllerOfflineAfter)
        assertEquals(3, config.retryPolicy.maxAttempts)
    }
}
