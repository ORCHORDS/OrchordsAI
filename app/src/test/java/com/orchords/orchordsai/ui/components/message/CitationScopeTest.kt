package com.orchords.orchordsai.ui.components.message

import com.orchords.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CitationScopeTest {
    private fun searchTool(
        toolCallId: String,
        vararg items: Pair<String, String>,
    ): UIMessagePart.Tool {
        val payload = items.joinToString(",") { (id, url) ->
            """{"id":"$id","url":"$url"}"""
        }
        return UIMessagePart.Tool(
            toolCallId = toolCallId,
            toolName = "search_web",
            input = "{}",
            output = listOf(UIMessagePart.Text("""{"items":[$payload]}""")),
        )
    }

    private fun nonSearchTool(): UIMessagePart.Tool = UIMessagePart.Tool(
        toolCallId = "other-1",
        toolName = "calculator",
        input = "{}",
        output = listOf(UIMessagePart.Text("{}")),
    )

    @Test
    fun consecutiveSearchesAreAvailableToFollowingText() {
        val parts = listOf(
            searchTool("search-1", "aaaaaa" to "https://one.example/a"),
            searchTool("search-2", "bbbbbb" to "https://two.example/b"),
            UIMessagePart.Text("answer"),
        )

        val scopes = buildCitationTargetsByPartIndex(parts)

        assertEquals(setOf("aaaaaa", "bbbbbb"), scopes.getValue(2).keys)
    }

    @Test
    fun aNewSearchAfterTextStartsANewCitationScope() {
        val parts = listOf(
            searchTool("search-1", "aaaaaa" to "https://one.example/a"),
            UIMessagePart.Text("first answer"),
            searchTool("search-2", "bbbbbb" to "https://two.example/b"),
            UIMessagePart.Text("second answer"),
        )

        val scopes = buildCitationTargetsByPartIndex(parts)

        assertTrue(scopes.getValue(1).containsKey("aaaaaa"))
        assertFalse(scopes.getValue(3).containsKey("aaaaaa"))
        assertTrue(scopes.getValue(3).containsKey("bbbbbb"))
    }

    @Test
    fun aNonSearchToolClearsPendingSearchEvidence() {
        val parts = listOf(
            searchTool("search-1", "aaaaaa" to "https://one.example/a"),
            nonSearchTool(),
            UIMessagePart.Text("answer after unrelated tool"),
        )

        val scopes = buildCitationTargetsByPartIndex(parts)

        assertTrue(scopes.getValue(2).isEmpty())
    }

    @Test
    fun malformedUnsafeAndAmbiguousResultsAreRejected() {
        val parts = listOf(
            searchTool(
                "search-1",
                "short" to "https://valid.example/a",
                "cccccc" to "javascript:alert(1)",
                "aaaaaa" to "https://one.example/a",
            ),
            searchTool("search-2", "aaaaaa" to "https://other.example/a"),
            UIMessagePart.Text("answer"),
        )

        val targets = buildCitationTargetsByPartIndex(parts).getValue(2)

        assertFalse(targets.containsKey("short"))
        assertFalse(targets.containsKey("cccccc"))
        assertFalse(targets.containsKey("aaaaaa"))
    }

    @Test
    fun malformedSearchClearsEarlierPendingEvidence() {
        val malformedSearch = UIMessagePart.Tool(
            toolCallId = "search-2",
            toolName = "search_web",
            input = "{}",
            output = listOf(UIMessagePart.Text("{}")),
        )
        val parts = listOf(
            searchTool("search-1", "aaaaaa" to "https://one.example/a"),
            malformedSearch,
            UIMessagePart.Text("answer"),
        )

        assertTrue(buildCitationTargetsByPartIndex(parts).getValue(2).isEmpty())
    }

    @Test
    fun sanitizerUsesTrustedHostDropsUnknownCitationsAndPreservesOrdinaryLinks() {
        val parts = listOf(
            searchTool("search-1", "aaaaaa" to "https://docs.example.com/a"),
            UIMessagePart.Text("answer"),
        )
        val targets = buildCitationTargetsByPartIndex(parts).getValue(1)
        val input = "Claim [citation,evil.example](aaaaaa) unknown [citation,fake.example](bbbbbb) and [ordinary](https://example.org/x)"

        val sanitized = sanitizeCitationMarkdown(input, targets)

        assertTrue(sanitized.contains("[citation,docs.example.com](aaaaaa)"))
        assertFalse(sanitized.contains("evil.example"))
        assertFalse(sanitized.contains("bbbbbb"))
        assertTrue(sanitized.contains("[ordinary](https://example.org/x)"))
    }

    @Test
    fun sanitizerLeavesCodeEscapesAndAdjacentOrdinaryLinksUntouched() {
        val targets = mapOf(
            "aaaaaa" to CitationTarget("https://docs.example.com/a", "docs.example.com"),
        )
        val input = """
            `inline [citation,code.example](aaaaaa)` and \[citation,escaped.example](aaaaaa)
            ~~~text
            [citation,fenced.example](aaaaaa)
            ~~~
            Claim [citation,evil.example](aaaaaa)[ordinary](https://example.org/x).
        """.trimIndent()

        val sanitized = sanitizeCitationMarkdown(input, targets)

        assertTrue(sanitized.contains("`inline [citation,code.example](aaaaaa)`"))
        assertTrue(sanitized.contains("\\[citation,escaped.example](aaaaaa)"))
        assertTrue(sanitized.contains("[citation,fenced.example](aaaaaa)"))
        assertTrue(sanitized.contains("[citation,docs.example.com](aaaaaa)[ordinary](https://example.org/x)"))
    }
}
