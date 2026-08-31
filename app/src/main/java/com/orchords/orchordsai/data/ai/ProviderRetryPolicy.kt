package com.orchords.orchordsai.data.ai

import java.io.IOException

internal fun canAutomaticallyRetryProviderRequest(
    error: Throwable,
    retryCount: Int,
    maxRetries: Int,
    hasReceivedProviderEvent: Boolean,
): Boolean =
    error is IOException &&
        !hasReceivedProviderEvent &&
        retryCount < maxRetries
