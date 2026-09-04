package com.orchords.orchordsai.data.ai.transformers

import com.orchords.ai.core.MessageRole
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.Modality
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import com.orchords.common.cache.CacheEntry
import com.orchords.common.cache.CacheStore
import com.orchords.common.cache.LruCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class OcrTransformControllerTest {

    private val textOnlyModel = Model(
        modelId = "text-only",
        displayName = "Text Only",
        inputModalities = listOf(Modality.TEXT),
        outputModalities = listOf(Modality.TEXT),
    )

    private val visionModel = Model(
        modelId = "vision",
        displayName = "Vision",
        inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
        outputModalities = listOf(Modality.TEXT),
    )

    private fun userImageMessage(url: String, text: String? = null): UIMessage = UIMessage(
        role = MessageRole.USER,
        parts = buildList {
            add(UIMessagePart.Image(url))
            if (text != null) add(UIMessagePart.Text(text))
        },
    )

    private fun userTextMessage(text: String): UIMessage = UIMessage(
        role = MessageRole.USER,
        parts = listOf(UIMessagePart.Text(text)),
    )

    private class RecordingStatus : ProcessingStatusSink {
        val invocations = mutableListOf<String?>()
        override var value: String?
            get() = invocations.lastOrNull()
            set(actual) { invocations.add(actual) }
    }

    private class RecordingStore : CacheStore<String, String> {
        val readKeys = mutableListOf<String>()
        val written = mutableMapOf<String, String>()
        override fun loadEntry(key: String): CacheEntry<String>? {
            readKeys.add(key)
            val v = written[key] ?: return null
            return CacheEntry(value = v, expiresAt = null)
        }
        override fun saveEntry(key: String, entry: CacheEntry<String>) {
            written[key] = entry.value
        }
        override fun remove(key: String) {
            written.remove(key)
        }
        override fun clear() { written.clear() }
        override fun loadAllEntries(): Map<String, CacheEntry<String>> =
            written.mapValues { CacheEntry(value = it.value, expiresAt = null) }
        override fun keys(): Set<String> = written.keys.toSet()
    }

    private fun newController(
        ocrProvider: suspend (UIMessagePart.Image) -> String,
        status: RecordingStatus = RecordingStatus(),
        capacity: Int = 32,
    ): Triple<OcrTransformController, RecordingStatus, RecordingStore> {
        val store = RecordingStore()
        val cache = LruCache<String, String>(
            capacity = capacity,
            store = store,
            deleteOnEvict = false,
            preloadFromStore = true,
        )
        return Triple(OcrTransformController(cache, ocrProvider, status), status, store)
    }

    // --- Vision model short-circuit ----------------------------------------

    @Test
    fun `vision model bypasses OCR entirely`() = runBlocking {
        val callCount = AtomicInteger(0)
        val (controller, status, _) = newController(
            ocrProvider = { callCount.incrementAndGet(); "should not run" },
        )
        val messages = listOf(userImageMessage("file:///tmp/foo.jpg"))
        val result = controller.transform(visionModel, messages, forceReOcr = false)
        assertEquals(messages, result)
        assertEquals(0, callCount.get())
        assertTrue("status never set for vision path: $status", status.invocations.isEmpty())
    }

    // --- Text-only history with no images: zero touches --------------------

    @Test
    fun `conversation without local-file images returns messages untouched`() = runBlocking {
        val callCount = AtomicInteger(0)
        val (controller, status, _) = newController(
            ocrProvider = { callCount.incrementAndGet(); "should not run" },
        )
        val messages = listOf(
            userTextMessage("hello"),
            userTextMessage("world"),
        )
        val result = controller.transform(textOnlyModel, messages, forceReOcr = false)
        assertEquals(messages, result)
        assertEquals(0, callCount.get())
        assertTrue("status never set when no images: $status", status.invocations.isEmpty())
    }

    @Test
    fun `non-file URL images are ignored by OCR pipeline`() = runBlocking {
        val callCount = AtomicInteger(0)
        val (controller, status, _) = newController(
            ocrProvider = { callCount.incrementAndGet(); "should not run" },
        )
        val messages = listOf(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Image("https://example.com/x.jpg"))),
        )
        val result = controller.transform(textOnlyModel, messages, forceReOcr = false)
        assertEquals(messages, result)
        assertEquals(0, callCount.get())
        assertTrue("status never set for non-file URLs: $status", status.invocations.isEmpty())
    }

    // --- Cache miss triggers OCR once, status set + cleared ----------------

    @Test
    fun `cache miss triggers exactly one OCR call and toggles processing status`() = runBlocking {
        val callCount = AtomicInteger(0)
        val (controller, status, _) = newController(
            ocrProvider = {
                callCount.incrementAndGet()
                "<image_file_ocr>fresh</image_file_ocr>"
            },
        )
        val messages = listOf(userImageMessage("file:///tmp/miss.jpg"))
        val out = controller.transform(textOnlyModel, messages, forceReOcr = false)

        assertEquals(1, callCount.get())
        assertTrue(out.first().parts.first() is UIMessagePart.Text)
        val text = (out.first().parts.first() as UIMessagePart.Text).text
        assertTrue("expected persisted OCR text in result, got: $text", "fresh" in text)
        // processingStatus should be set then cleared (null at end)
        assertEquals(listOf("Recognizing image...", null), status.invocations)
    }

    // --- Cache hit: zero OCR invocations across many text turns ------------

    @Test
    fun `cached images are not re-OCRd across repeated text-only turns`() = runBlocking {
        val callCount = AtomicInteger(0)
        val (controller, status, store) = newController(
            ocrProvider = {
                callCount.incrementAndGet()
                "cached text v${callCount.get()}"
            },
        )

        // 1) First non-vision request with a fresh historical image triggers OCR
        val firstMessages = listOf(
            userImageMessage("file:///tmp/historical.png"),
            userTextMessage("describe please"),
        )
        controller.transform(textOnlyModel, firstMessages, forceReOcr = false)
        assertEquals(1, callCount.get())
        assertEquals(setOf("file:///tmp/historical.png"), store.written.keys)

        // 2) Followed by a series of pure-text turns that still reference the historical image
        // (this is the defect scenario described in RikkaHub #1736 / OrchordsAI #67)
        repeat(5) {
            val out = controller.transform(
                textOnlyModel,
                listOf(userTextMessage("text turn only")),
                forceReOcr = false,
            )
            // No image -> returns untouched, no status flicker
            assertEquals(1, out.size)
        }

        // 3) A later request that again carries the image should reuse the cache
        val later = controller.transform(
            textOnlyModel,
            listOf(
                userImageMessage("file:///tmp/historical.png"),
                userTextMessage("another prompt"),
            ),
            forceReOcr = false,
        )
        assertEquals("only the first request invoked OCR", 1, callCount.get())

        val imagePartText = (later.first().parts.first() as UIMessagePart.Text).text
        assertTrue("reused cache text without new OCR: $imagePartText", "v1" in imagePartText)
        // Status flow: only the first call triggered "Recognizing image..."+null
        val statusTransitions = status.invocations
        assertEquals(
            "status must only flicker on the cold call, got: $statusTransitions",
            listOf("Recognizing image...", null),
            statusTransitions,
        )
    }

    // --- forceReOcr opt-in bypasses cache but only sets status when needed --

    @Test
    fun `forceReOcr flag re-invokes OCR even when cache has a value`() = runBlocking {
        val callCount = AtomicInteger(0)
        val (controller, status, store) = newController(
            ocrProvider = {
                callCount.incrementAndGet()
                "ocr text v${callCount.get()}"
            },
        )

        // Cold path populates cache
        val cold = controller.transform(
            textOnlyModel,
            listOf(userImageMessage("file:///tmp/foo.png")),
            forceReOcr = false,
        )
        assertEquals(1, callCount.get())
        assertTrue((cold.first().parts.first() as UIMessagePart.Text).text.contains("v1"))
        assertTrue(store.written.containsKey("file:///tmp/foo.png"))

        // Force-reOCR call: bumps to v2 and rewrites cache entry
        val warm = controller.transform(
            textOnlyModel,
            listOf(userImageMessage("file:///tmp/foo.png")),
            forceReOcr = true,
        )
        assertEquals(2, callCount.get())
        val warmText = (warm.first().parts.first() as UIMessagePart.Text).text
        assertTrue("expected refreshed text on forced re-OCR, got: $warmText", "v2" in warmText)
        assertEquals("cache updated to new result", "ocr text v2", store.written["file:///tmp/foo.png"])

        // Status toggled for both calls (each invocation actually OCRs)
        assertEquals(
            listOf("Recognizing image...", null, "Recognizing image...", null),
            status.invocations,
        )
    }

    // --- fileImageCount helper -------------------------------------------------

    @Test
    fun `fileImageCount reports only file-prefix images`() {
        val (controller, _, _) = newController(ocrProvider = { "" })
        val msgs = listOf(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Image("file:///a.png"))),
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Image("https://x/y.jpg"))),
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("ignore me"))),
        )
        assertEquals(1, controller.fileImageCount(msgs))
    }

    // --- Sanity: new status effects do not leak between calls ---------------

    @Test
    fun `processingStatus ends in null after every transform`() = runBlocking {
        val (controller, status, _) = newController(
            ocrProvider = { "<image_file_ocr>v</image_file_ocr>" },
        )
        // Cache miss - status set, then cleared
        controller.transform(
            textOnlyModel,
            listOf(userImageMessage("file:///tmp/reset.png")),
            forceReOcr = false,
        )
        assertNull(status.value)
        // Cache hit - never enters OCR, status never set, still null
        controller.transform(
            textOnlyModel,
            listOf(userImageMessage("file:///tmp/reset.png")),
            forceReOcr = false,
        )
        assertNull(status.value)
    }
}
