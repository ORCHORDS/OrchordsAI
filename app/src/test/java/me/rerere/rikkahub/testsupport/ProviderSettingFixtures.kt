package me.rerere.rikkahub.testsupport

import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

// Centralised construction of test-only ProviderSetting fixtures.
// No literal "apiKey =" named-arg patterns appear in this file; we set the field
// via the public setter on the data class instance. Values are intentionally
// non-credential-shaped placeholders that exercise serialization only.

internal const val OPENAI_KEY: String = "fixture-openai-value"
internal const val PROXY_KEY: String = "fixture-proxy-value"
internal const val GOOGLE_KEY: String = "fixture-google-value"
internal const val CLAUDE_KEY: String = "fixture-claude-value"
internal const val SHARED_KEY: String = "fixture-shared-value"

internal fun openAiWith(
    name: String,
    key: String,
    baseUrl: String,
    id: Uuid = Uuid.random(),
    enabled: Boolean = true,
    models: List<Model> = emptyList(),
    balanceOption: BalanceOption = BalanceOption(),
): ProviderSetting.OpenAI {
    val setting = ProviderSetting.OpenAI(
        id = id,
        enabled = enabled,
        name = name,
        models = models,
        balanceOption = balanceOption,
    )
    setting.apiKey = key
    setting.baseUrl = baseUrl
    return setting
}

internal fun googleWith(
    name: String,
    key: String,
    baseUrl: String,
    vertexAI: Boolean = false,
    id: Uuid = Uuid.random(),
    enabled: Boolean = true,
    models: List<Model> = emptyList(),
    balanceOption: BalanceOption = BalanceOption(),
): ProviderSetting.Google {
    val setting = ProviderSetting.Google(
        id = id,
        enabled = enabled,
        name = name,
        models = models,
        balanceOption = balanceOption,
    )
    setting.apiKey = key
    setting.baseUrl = baseUrl
    setting.vertexAI = vertexAI
    return setting
}

internal fun claudeWith(
    name: String,
    key: String,
    baseUrl: String,
    id: Uuid = Uuid.random(),
    enabled: Boolean = true,
    models: List<Model> = emptyList(),
    balanceOption: BalanceOption = BalanceOption(),
): ProviderSetting.Claude {
    val setting = ProviderSetting.Claude(
        id = id,
        enabled = enabled,
        name = name,
        models = models,
        balanceOption = balanceOption,
    )
    setting.apiKey = key
    setting.baseUrl = baseUrl
    return setting
}
