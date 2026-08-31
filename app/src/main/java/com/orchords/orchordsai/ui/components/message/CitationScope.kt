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
                    parsedSearchTargets[index].orEmpty().forEach { result ->
                        if (result.id !in ambiguousIds) {
                            activeTargets[result.id] = result.target
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
): String = citationMarkdownPattern.replace(text) { match ->
    val id = match.groupValues[1].trim()
    val target = targets[id] ?: return@replace ""
    "[citation,${target.label}]($id)"
}

private fun parseSearchCitationTargets(tool: UIMessagePart.Tool): List<SearchCitationTarget> {
    val outputText = tool.output
        .filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }

    if (outputText.isBlank()) return emptyList()

    val items = runCatching {
        JsonInstant.parseToJsonElement(outputText)
            .jsonObject["items"]
            ?.jsonArray
    }.getOrNull() ?: return emptyList()

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
