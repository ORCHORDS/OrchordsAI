package com.orchords.orchordsai.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.utils.JsonInstant
import kotlin.uuid.Uuid

class PreferenceStoreV3Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 3
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        val (migratedAssistants, extractedQuickMessages) =
            migrateAssistantsQuickMessages(prefs[SettingsStore.ASSISTANTS] ?: "[]")

        prefs[SettingsStore.ASSISTANTS] = migratedAssistants

        val existingQuickMessages = prefs[SettingsStore.QUICK_MESSAGES]?.let { json ->
            runCatching<JsonArray> {
                JsonInstant.parseToJsonElement(json).jsonArray
            }.getOrElse { JsonArray(emptyList()) }
        } ?: JsonArray(emptyList())

        val existingIds = existingQuickMessages.mapNotNull {
            (it as? JsonObject)?.get("id")?.toString()?.trim('"')
        }.toSet()

        val merged = JsonArray(
            existingQuickMessages + extractedQuickMessages.filter { element ->
                val id = (element as? JsonObject)?.get("id")?.toString()?.trim('"')
                id != null && id !in existingIds
            }
        )

        prefs[SettingsStore.QUICK_MESSAGES] = JsonInstant.encodeToString(merged)
        prefs[SettingsStore.VERSION] = 3

        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

/**
 */
internal fun migrateAssistantsQuickMessages(
    assistantsJson: String
): Pair<String, JsonArray> {
    return runCatching {
        val root = JsonInstant.parseToJsonElement(assistantsJson) as? JsonArray
            ?: return@runCatching assistantsJson to JsonArray(emptyList())

        val allQuickMessages = mutableListOf<JsonElement>()

        val migratedAssistants = JsonArray(
            root.map { assistant ->
                val assistantObj = assistant as? JsonObject
                    ?: return@map assistant

                val oldQuickMessages = assistantObj["quickMessages"] as? JsonArray
                    ?: return@map assistant

                val messagesWithIds = oldQuickMessages.map { element ->
                    val obj = element as? JsonObject ?: return@map element
                    val newId = Uuid.random().toString()
                    JsonObject(obj.toMutableMap().apply {
                        put("id", JsonPrimitive(newId))
                    })
                }

                allQuickMessages.addAll(messagesWithIds)

                val ids = JsonArray(
                    messagesWithIds.mapNotNull { element ->
                        (element as? JsonObject)?.get("id")
                    }
                )

                JsonObject(
                    assistantObj.toMutableMap().apply {
                        remove("quickMessages")
                        put("quickMessageIds", ids)
                    }
                )
            }
        )

        JsonInstant.encodeToString(migratedAssistants) to JsonArray(allQuickMessages)
    }.getOrElse { assistantsJson to JsonArray(emptyList()) }
}
