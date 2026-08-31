package com.orchords.orchordsai.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.orchords.ai.core.ReasoningLevel
import com.orchords.ai.provider.CustomBody
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ProviderManager
import com.orchords.ai.provider.TextGenerationParams
import com.orchords.ai.registry.ModelRegistry
import com.orchords.ai.ui.StreamChunkHandler
import com.orchords.ai.ui.UIMessage
import com.orchords.orchordsai.data.datastore.Settings
import com.orchords.orchordsai.data.datastore.findModelById
import com.orchords.orchordsai.data.datastore.findProvider
import com.orchords.orchordsai.utils.applyPlaceholders
import java.util.Locale

internal fun translationGenerationParams(
    model: Model,
    reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    temperature: Float? = null,
    topP: Float? = null,
    translationBodies: List<CustomBody> = emptyList(),
): TextGenerationParams {
    val translationKeys = translationBodies.mapTo(HashSet()) { it.key }
    val conflictingKey = model.customBodies.firstOrNull { it.key in translationKeys }?.key
    require(conflictingKey == null) {
        "Translation request body conflicts with reserved translation field"
    }

    return TextGenerationParams(
        model = model,
        reasoningLevel = reasoningLevel,
        temperature = temperature,
        topP = topP,
        customHeaders = model.customHeaders,
        customBody = model.customBodies + translationBodies,
    )
}

class TranslationHandler(
    private val providerManager: ProviderManager,
) {
    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: Locale,
        onStreamUpdate: ((String) -> Unit)? = null,
    ): Flow<String> = flow {
        val model = settings.providers.findModelById(settings.translateModeId)
            ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            // Use regular translation with prompt
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguage.toString(),
            )

            var messages = listOf(UIMessage.user(prompt))
            val streamChunkHandler = StreamChunkHandler(model)

            providerHandler.streamText(
                providerSetting = provider,
                messages = messages,
                params = translationGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
                ),
            ).collect { chunk ->
                messages = streamChunkHandler.handle(messages, chunk)
                val translatedText = messages.lastOrNull()?.toText() ?: ""

                if (translatedText.isNotBlank()) {
                    onStreamUpdate?.invoke(translatedText)
                    emit(translatedText)
                }
            }
        } else {
            // Use Qwen MT model with special translation options
            val messages = listOf(UIMessage.user(sourceText))
            val translationOptions = CustomBody(
                key = "translation_options",
                value = buildJsonObject {
                    put("source_lang", JsonPrimitive("auto"))
                    put(
                        "target_lang",
                        JsonPrimitive(targetLanguage.getDisplayLanguage(Locale.ENGLISH)),
                    )
                },
            )
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = messages,
                params = translationGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    topP = 0.95f,
                    translationBodies = listOf(translationOptions),
                ),
            )
            val translatedText = result.message.toText()

            if (translatedText.isNotBlank()) {
                onStreamUpdate?.invoke(translatedText)
                emit(translatedText)
            }
        }
    }.flowOn(Dispatchers.IO)
}
