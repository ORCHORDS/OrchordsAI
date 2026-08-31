package com.orchords.orchordsai.data.ai.transformers

import kotlinx.serialization.json.JsonPrimitive

internal fun encodeWorkspacePromptMetadata(value: String): String = JsonPrimitive(value).toString()
