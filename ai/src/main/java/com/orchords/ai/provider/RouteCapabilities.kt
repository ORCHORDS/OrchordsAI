package com.orchords.ai.provider

import com.orchords.ai.registry.ModelRegistry

/**
 * Effective per-route capabilities for sampling parameters. Each call site
 * decides whether to emit `temperature` / `top_p` based on these flags.
 *
 * `null` user override → fall back to the ModelRegistry denylist (today's
 * behavior). Non-null user override → force the choice, irrespective of model
 * identity. This lets users strip sampling parameters on a per-route basis
 * (for non-OpenAI gateways that wrap reasoning models and reject the
 * parameters) without forking the model registry.
 *
 * See issue #355.
 */
data class RouteCapabilities(
    val supportsTemperature: Boolean,
    val supportsTopP: Boolean,
)

/**
 * Returns `true` when the effective route's effective model should be denied
 * the `temperature` / `top_p` sampling parameters under today's denylist
 * fallback. Encapsulated here so the OpenAI call sites no longer carry the
 * model-denylist logic themselves.
 */
private fun ProviderSetting.OpenAI.denylistMatch(modelId: String): Boolean {
    val isMoonshotRestricted = ModelRegistry.KIMI_K2_5.match(modelId) ||
            ModelRegistry.KIMI_K2_6.match(modelId) ||
            ModelRegistry.KIMI_K3.match(modelId) ||
            ModelRegistry.KIMI_K3_ALIAS.match(modelId)
    return ModelRegistry.OPENAI_O_MODELS.match(modelId) ||
            ModelRegistry.GPT_5.match(modelId) ||
            isMoonshotRestricted
}

/**
 * OpenAI Chat-Completions / Responses route capabilities.
 *
 * Default (`supportsTemperature == null && supportsTopP == null`): the
 * ModelRegistry denylist decides. Today that list denies temperature/top_p on
 * `o*` reasoning models, `gpt-5*` reasoning models, and `kimi-k2-*` / `kimi-k3`
 * models hosted on Moonshot-compatible gateways.
 *
 * Non-null override: force the choice. For example, a non-OpenAI gateway that
 * wraps `gpt-5` and rejects `temperature` can set `supportsTemperature = false`
 * explicitly; or a gateway that accepts `temperature` on `o1` can set
 * `supportsTemperature = true` to bypass the model denylist.
 */
fun ProviderSetting.OpenAI.resolveRouteCapabilities(model: Model): RouteCapabilities {
    val deny = denylistMatch(model.modelId)
    return RouteCapabilities(
        supportsTemperature = supportsTemperature ?: !deny,
        supportsTopP = supportsTopP ?: !deny,
    )
}

/**
 * Anthropic Messages route capabilities.
 *
 * Anthropic accepts both `temperature` and `top_p` on standard turns, so the
 * default is permissive (`true`). The extended-thinking constraint (rejecting
 * `temperature` while `reasoningLevel.isEnabled`) is a per-call correctness
 * invariant, not a capability, and is enforced at the call site; see
 * `ClaudeProvider.buildMessages` / ClaudeProvider request body. Setting either
 * flag to `false` lets users force-strip the parameter on non-Anthropic
 * Claude-compatible gateways.
 */
fun ProviderSetting.Claude.resolveRouteCapabilities(model: Model): RouteCapabilities {
    return RouteCapabilities(
        supportsTemperature = supportsTemperature ?: true,
        supportsTopP = supportsTopP ?: true,
    )
}

/**
 * Google Gemini route capabilities.
 *
 * Gemini accepts both `temperature` and `topP`, so the default is permissive.
 * The Gemini cross-field constraint ("topP omitted when temperature is set")
 * is not a per-flag capability — see
 * https://ai.google.dev/api/generate-content — and lives at the call site in
 * `GoogleProvider.buildCompletionRequestBody`. Setting either flag to `false`
 * lets users force-strip the parameter on a Google-compatible gateway.
 */
fun ProviderSetting.Google.resolveRouteCapabilities(model: Model): RouteCapabilities {
    return RouteCapabilities(
        supportsTemperature = supportsTemperature ?: true,
        supportsTopP = supportsTopP ?: true,
    )
}
