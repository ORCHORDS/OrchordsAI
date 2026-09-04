package com.orchords.orchordsai.data.ai.transformers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import com.orchords.ai.core.MessageRole
import com.orchords.ai.provider.Modality
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ProviderManager
import com.orchords.ai.provider.TextGenerationParams
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import com.orchords.common.cache.LruCache
import com.orchords.common.cache.SingleFileCacheStore
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.datastore.findModelById
import com.orchords.orchordsai.data.datastore.findProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal interface ProcessingStatusSink {
    var value: String?
}

/**
 * Pure controller that decides which messages trigger OCR and how the user-visible
 * processing status is reported. Decoupled from Koin so unit tests can drive it
 * with stubbed [ocrProvider] / [cache] dependencies. See issue #67.
 */
internal class OcrTransformController(
    private val cache: LruCache<String, String>,
    private val ocrProvider: suspend (UIMessagePart.Image) -> String,
    private val processingStatus: ProcessingStatusSink,
) {

    suspend fun transform(
        model: Model,
        messages: List<UIMessage>,
        forceReOcr: Boolean,
    ): List<UIMessage> {
        if (model.inputModalities.contains(Modality.IMAGE)) {
            return messages
        }

        val fileImages = collectFileImages(messages)
        if (fileImages.isEmpty()) return messages

        val needsOcr = fileImages.any { image -> forceReOcr || cache.get(image.url) == null }

        if (!needsOcr) {
            return messages.map { message ->
                message.copy(
                    parts = message.parts.map { part ->
                        if (part is UIMessagePart.Image && part.url.startsWith("file:")) {
                            UIMessagePart.Text(cache.get(part.url) ?: return@map part)
                        } else {
                            part
                        }
                    }
                )
            }
        }

        return withContext(Dispatchers.IO) {
            processingStatus.value = "Recognizing image..."
            try {
                messages.map { message ->
                    message.copy(
                        parts = message.parts.map { part ->
                            if (part is UIMessagePart.Image && part.url.startsWith("file:")) {
                                UIMessagePart.Text(resolveOcrText(part, forceReOcr))
                            } else {
                                part
                            }
                        }
                    )
                }
            } finally {
                processingStatus.value = null
            }
        }
    }

    fun fileImageCount(messages: List<UIMessage>): Int = collectFileImages(messages).size

    private fun collectFileImages(messages: List<UIMessage>): List<UIMessagePart.Image> =
        messages.flatMap { message ->
            message.parts.filterIsInstance<UIMessagePart.Image>()
                .filter { it.url.startsWith("file:") }
        }

    private suspend fun resolveOcrText(part: UIMessagePart.Image, forceReOcr: Boolean): String {
        if (!forceReOcr) {
            cache.get(part.url)?.let { return it }
        }
        val fresh = ocrProvider(part)
        cache.put(part.url, fresh)
        return fresh
    }
}

object OcrTransformer : InputMessageTransformer, KoinComponent {
    private const val TAG = "OcrTransformer"

    /**
     * One-shot signal consumed by the next [transform] call. Set via
     * [requestForceReOcr] from regenerate flows where the user has explicitly
     * opted into a fresh OCR for historical images. The flag is cleared after a
     * single consumption so other requests fall back to the cached
     * representation automatically.
     */
    private val pendingForceReOcr = AtomicBoolean(false)

    fun requestForceReOcr() {
        pendingForceReOcr.set(true)
    }

    private fun consumeForceReOcr(): Boolean = pendingForceReOcr.getAndSet(false)

    private val cache by lazy {
        val context = get<Context>()
        val json = Json { allowStructuredMapKeys = true }
        val store = SingleFileCacheStore(
            file = File(context.cacheDir, "ocr_cache.json"),
            keySerializer = String.serializer(),
            valueSerializer = String.serializer(),
            json = json
        )
        // The OCR cache must outlive short conversation windows: once an image
        // has been described, that description remains stable for any future
        // request unless the user explicitly forces a re-OCR. Leaving the
        // entries free of a TTL makes the cache effectively persistent (only
        // bounded by [capacity]); the file-backed [SingleFileCacheStore]
        // survives process restarts. See issue #67.
        LruCache(
            capacity = 64,
            store = store,
            deleteOnEvict = true,
            preloadFromStore = true,
        )
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return OcrTransformController(
            cache = cache,
            ocrProvider = { performOcr(it) },
            processingStatus = MutableStateFlowSink(ctx.processingStatus),
        ).transform(
            model = ctx.model,
            messages = messages,
            forceReOcr = consumeForceReOcr(),
        )
    }

    suspend fun performOcr(part: UIMessagePart.Image): String {
        val settings = get<SettingsStore>().settingsFlow.value
        val model = settings.findModelById(settings.ocrModelId) ?: return "[Image]"
        val providerSetting = model.findProvider(settings.providers) ?: return "[Image]"
        val provider = get<ProviderManager>().getProviderByType(providerSetting)
        val result = runCatching {
            provider.generateText(
                providerSetting = providerSetting,
                messages = listOf(
                    UIMessage.system(settings.ocrPrompt),
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Image(part.url))
                    )
                ),
                params = TextGenerationParams(
                    model = model,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies,
                ),
            )
        }.getOrElse {
            return "[ERROR, OCR failed]"
        }
        val content = result.message.toText().ifBlank { "[ERROR, OCR failed]" }
        return """
            <image_file_ocr>
               $content
            </image_file_ocr>
            * The image_file_ocr tag contains a description of an image that the user uploaded to you, not the user's prompt.
        """.trimIndent()
    }
}

private class MutableStateFlowSink(
    private val flow: MutableStateFlow<String?>,
) : ProcessingStatusSink {
    override var value: String?
        get() = flow.value
        set(actual) {
            flow.value = actual
        }
}
