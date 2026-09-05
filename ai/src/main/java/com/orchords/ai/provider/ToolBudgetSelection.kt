package com.orchords.ai.provider

/** A local preflight error; do not retry a provider request to discover a smaller budget. */
internal class ToolBudgetExceededException(required: Int, budget: Int) : IllegalArgumentException(
    "The current tool continuation needs $required tools, but the configured budget is $budget. Increase the compatible budget or finish/cancel the pending workflow."
)

/** Stable selection within the supplied authorized registry; never adds absent tool identities. */
internal fun <T> selectBudgetedTools(
    tools: List<T>,
    name: (T) -> String,
    requiredNames: Set<String>,
    hardCap: Int?,
    userCap: Int?,
): List<T> {
    require(hardCap == null || hardCap >= 0) { "Tool hard cap must not be negative" }
    require(userCap == null || userCap >= 0) { "Tool budget must not be negative" }
    val limit = minOf(hardCap ?: Int.MAX_VALUE, userCap ?: Int.MAX_VALUE)
    val unique = tools.distinctBy(name)
    val required = unique.filter { name(it) in requiredNames }
    if (required.size > limit) throw ToolBudgetExceededException(required.size, limit)
    val optional = unique.filterNot { name(it) in requiredNames }
    return required + optional.take(limit - required.size)
}
