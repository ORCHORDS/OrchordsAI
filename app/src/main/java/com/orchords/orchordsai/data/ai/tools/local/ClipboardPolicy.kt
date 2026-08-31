package com.orchords.orchordsai.data.ai.tools.local

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun clipboardNeedsApproval(args: JsonElement): Boolean {
    val action = args.jsonObject["action"]?.jsonPrimitive?.contentOrNull
    return action != "read"
}
