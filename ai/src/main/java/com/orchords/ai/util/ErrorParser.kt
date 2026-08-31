package com.orchords.ai.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class HttpException(
    message: String
) : RuntimeException(message)

fun JsonElement.parseErrorDetail(): HttpException {
    return when (this) {
        is JsonObject -> {
            val errorFields = listOf("error", "detail", "message", "description")

            val foundField = errorFields.firstOrNull { this[it] != null }

            if (foundField != null) {
                this[foundField]!!.parseErrorDetail()
            } else {
                HttpException(Json.encodeToString(JsonElement.serializer(), this))
            }
        }

        is JsonArray -> {
            if (this.isEmpty()) {
                HttpException("Unknown error: Empty JSON array")
            } else {
                this.first().parseErrorDetail()
            }
        }

        is JsonPrimitive -> {
            HttpException(this.jsonPrimitive.content)
        }

        else -> {
            HttpException(Json.encodeToString(JsonElement.serializer(), this))
        }
    }
}
