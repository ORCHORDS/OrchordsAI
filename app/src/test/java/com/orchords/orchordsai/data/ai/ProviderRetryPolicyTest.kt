package com.orchords.orchordsai.data.ai

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRetryPolicyTest {
    @Test
    fun `network failure before first provider event may retry`() {
        assertTrue(
            canAutomaticallyRetryProviderRequest(
                error = IOException("fixture disconnect"),
                retryCount = 0,
                maxRetries = 3,
                hasReceivedProviderEvent = false,
            )
        )
    }

    @Test
    fun `network failure after provider output never retries automatically`() {
        assertFalse(
            canAutomaticallyRetryProviderRequest(
                error = IOException("fixture disconnect after text"),
                retryCount = 0,
                maxRetries = 3,
                hasReceivedProviderEvent = true,
            )
        )
    }

    @Test
    fun `retry limit is enforced before response begins`() {
        assertFalse(
            canAutomaticallyRetryProviderRequest(
                error = IOException("fixture disconnect"),
                retryCount = 3,
                maxRetries = 3,
                hasReceivedProviderEvent = false,
            )
        )
    }

    @Test
    fun `non network failure is never retried`() {
        assertFalse(
            canAutomaticallyRetryProviderRequest(
                error = IllegalStateException("fixture protocol failure"),
                retryCount = 0,
                maxRetries = 3,
                hasReceivedProviderEvent = false,
            )
        )
    }
}
