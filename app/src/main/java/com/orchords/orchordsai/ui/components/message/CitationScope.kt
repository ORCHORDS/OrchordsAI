package com.orchords.orchordsai.ui.components.message

import com.orchords.ai.ui.UIMessagePart
import com.orchords.orchordsai.utils.JsonInstant
import java.net.URI
import java.util.Locale
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class CitationTarget(
    val url: String,
    val label: String,
)

private data class SearchCitationTarget(
    val id: String,
    val target: CitationTarget,
)

private val citationIdPattern = Regex("^[0-9a-fA-F]{6}$")
private val citationMarkdownPattern = Regex("""\[citation,[^\]\r\n]*]\(([^)\r\n]*)\)""")

internal fun buildCitationTargetsByPartIndex(
    parts: List<UIMessagePart>,
): Map<Int, Map<String, CitationTarget>> {
    val parsedSearchTargets = parts.mapIndexedNotNull { index, part ->
        val tool = part as? UIMessagePart.Tool ?: return@mapIndexedNotNull null
        if (tool.toolName != "search_web" || !tool.isExecuted) return@mapIndexedNotNull null
        index to parseSearchCitationTargets(tool)
    }.toMap()

    val ambiguousIds = parsedSearchTargets.values
        .mapNotNull { it }
        .flatten()
        .groupingBy { it.id }
        .eachCount()
        .filterValues { it > 1 }
        .keys

    val targetsByPartIndex = mutableMapOf<Int, Map<String, CitationTarget>>()
    val activeTargets = linkedMapOf<String, CitationTarget>()
    var activeScopeHasText = false

    parts.forEachIndexed { index, part ->
        when (part) {
            is UIMessagePart.Tool -> {
                if (part.toolName == "search_web" && part.isExecuted) {
                    if (activeScopeHasText) activeTargets.clear()
                    val searchTargets = parsedSearchTargets[index]
                    if (searchTargets == null) {
                        activeTargets.clear()
                    } else {
                        searchTargets.forEach { result ->
                            if (result.id !in ambiguousIds) {
                                activeTargets[result.id] = result.target
                            }
                        }
                    }
                    activeScopeHasText = false
                } else {
                    activeTargets.clear()
                    activeScopeHasText = false
                }
            }

            is UIMessagePart.ServerTool -> {
                activeTargets.clear()
                activeScopeHasText = false
            }

            is UIMessagePart.Text -> {
                targetsByPartIndex[index] = activeTargets.toMap()
                activeScopeHasText = true
            }

            is UIMessagePart.Reasoning -> Unit
            else -> Unit
        }
    }

    return targetsByPartIndex
}

internal fun sanitizeCitationMarkdown(
    text: String,
    targets: Map<String, CitationTarget>,
): String {
    val result = StringBuilder(text.length)
    var index = 0
    var fence: String? = null
    var inlineTicks = 0

    while (index < text.length) {
        val atLineStart = index == 0 || text[index - 1] == '\n'
        if (atLineStart && inlineTicks == 0) {
            val delimiter = when {
                text.startsWith("```", index) -> "```"
                text.startsWith("~~~", index) -> "~~~"
                else -> null
            }
            if (delimiter != null && (fence == null || fence == delimiter)) {
                fence = if (fence == null) delimiter else null
                result.append(delimiter)
                index += delimiter.length
                continue
            }
        }

        if (fence == null && text[index] == '`') {
            val end = text.indexOfFirstFrom(index) { it != '`' }
            val runLength = end - index
            inlineTicks = if (inlineTicks == 0) runLength else if (inlineTicks == runLength) 0 else inlineTicks
            result.append(text, index, end)
            index = end
            continue
        }

        if (fence == null && inlineTicks == 0 && text[index] == '[') {
            var slashIndex = index - 1
            while (slashIndex >= 0 && text[slashIndex] == '\\') slashIndex--
            val escaped = (index - slashIndex - 1) % 2 == 1
            if (!escaped) {
                val match = citationMarkdownPattern.find(text, index)
                if (match != null && match.range.first == index) {
                    val id = match.groupValues[1].trim()
                    targets[id]?.let { result.append("[citation,${it.label}]($id)") }
                    index = match.range.last + 1
                    continue
                }
            }
        }

        result.append(text[index++])
    }
    return result.toString()
}

private inline fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
    var index = start
    while (index < length && !predicate(this[index])) index++
    return index
}

private fun parseSearchCitationTargets(tool: UIMessagePart.Tool): List<SearchCitationTarget>? {
    val outputText = tool.output
        .filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }

    if (outputText.isBlank()) return null

    val items = runCatching {
        JsonInstant.parseToJsonElement(outputText)
            .jsonObject["items"]
            ?.jsonArray
    }.getOrNull() ?: return null

    return items.mapNotNull { element ->
        val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
        val id = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val url = item["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

        if (!citationIdPattern.matches(id)) return@mapNotNull null
        val target = citationTargetForUrl(url) ?: return@mapNotNull null
        SearchCitationTarget(id = id, target = target)
    }
}

private fun citationTargetForUrl(url: String): CitationTarget? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme != "http" && scheme != "https") return null

    val host = uri.host
        ?.lowercase(Locale.ROOT)
        ?.trimEnd('.')
        ?.takeIf { it.isNotBlank() }
        ?: return null

    return CitationTarget(
        url = url,
        label = host.removePrefix("www."),
    )
}
