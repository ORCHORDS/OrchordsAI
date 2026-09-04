package com.orchords.ai.provider.providers.claude

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Canonical Anthropic-hosted hostname. Capability gates that decide whether to
 * emit native Anthropic server tools (e.g. `web_search_20250305`) should only
 * pass when the effective route targets this host.
 *
 * Third-party Claude-compatible gateways speak the `/v1/messages` wire format
 * but do not implement Anthropic-hosted server tools. Emitting them there
 * causes the gateway to reject the request and the user silently loses the
 * feature instead of falling back to the client-side search tool.
 *
 * See issue #360.
 */
const val ANTHROPIC_OFFICIAL_HOST = "api.anthropic.com"

/**
 * Returns `true` only when [this] resolves to Anthropic's official host.
 * Unparseable URLs return `false` (conservative — never emit native server
 * tools on a host we cannot verify).
 */
fun String.isAnthropicNativeHost(): Boolean {
    val host = toHttpUrlOrNull()?.host?.lowercase() ?: return false
    return host == ANTHROPIC_OFFICIAL_HOST
}
