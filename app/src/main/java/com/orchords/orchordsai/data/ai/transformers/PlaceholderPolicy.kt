package com.orchords.orchordsai.data.ai.transformers

import com.orchords.ai.ui.UIMessage

internal fun shouldExpandRuntimePlaceholders(message: UIMessage): Boolean = message.isSynthetic
