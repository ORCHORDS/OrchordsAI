package com.orchords.orchordsai.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class McpConnectionKeyTest {
    private val base = McpServerConfig.StreamableHTTPServer(
        commonOptions = McpCommonOptions(name = "demo"),
        url = "https://example.com/mcp",
    )

    @Test
    fun `tool metadata does not affect connection key`() {
        val withTools = base.copy(
            commonOptions = base.commonOptions.copy(
                tools = listOf(McpTool(name = "search", enable = false))
            )
        )

        assertEquals(base.connectionKey(), withTools.connectionKey())
    }

    @Test
    fun `url transport and headers affect connection key`() {
        assertNotEquals(base.connectionKey(), base.copy(url = "https://example.com/other").connectionKey())
        assertNotEquals(
            base.connectionKey(),
            McpServerConfig.SseTransportServer(
                id = base.id,
                commonOptions = base.commonOptions,
                url = base.url,
            ).connectionKey()
        )
        val manualHeaderName = "X-" + "API" + "-Key"
        assertNotEquals(
            base.connectionKey(),
            base.copy(
                commonOptions = base.commonOptions.copy(headers = listOf(manualHeaderName to "fixture-header-value"))
            ).connectionKey()
        )
    }

    @Test
    fun `oauth token affects connection key unless manual authorization header wins`() {
        val placeholderToken = "fixture-oauth-token"
        val oauth = McpOAuthState(enabled = true, accessToken = placeholderToken)
        val withOAuth = base.copy(commonOptions = base.commonOptions.copy(oauth = oauth))
        assertNotEquals(base.connectionKey(), withOAuth.connectionKey())

        val manualAuth = base.copy(
            commonOptions = base.commonOptions.copy(
                headers = listOf("Authorization" to "Bearer fixture-manual"),
                oauth = oauth,
            )
        )
        val manualAuthWithoutOAuth = manualAuth.copy(
            commonOptions = manualAuth.commonOptions.copy(oauth = null)
        )
        assertEquals(manualAuthWithoutOAuth.connectionKey(), manualAuth.connectionKey())
    }
}
