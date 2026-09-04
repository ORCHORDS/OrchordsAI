package com.orchords.orchordsai.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.orchords.ai.core.InputSchema
import com.orchords.ai.core.Tool
import com.orchords.ai.ui.UIMessagePart
import com.orchords.orchordsai.data.files.SkillFrontmatterParser
import com.orchords.orchordsai.data.files.SkillMetadata
import com.orchords.orchordsai.data.files.SkillPaths

fun createSkillTools(
    enabledSkills: Set<String>,
    allSkills: List<SkillMetadata>,
): List<Tool> {
    val available = allSkills.filter { it.name in enabledSkills }
    if (available.isEmpty()) return emptyList()

    // Per the Agent Skills spec (https://code.claude.com/docs/en/skills and the
    // disable-model-invocation proposal in agentskills/agentskills#236), skills
    // declaring `disable-model-invocation: true` must remain hidden from the
    // model until the user explicitly invokes them. We honor that contract by:
    //   - excluding those skills from the system-prompt <available_skills>
    //     advertisement so the model never sees their description or name, and
    //   - keeping them only resolvable via direct invocation paths (e.g. a
    //     future UI trigger that names the skill by full name).
    val modelCallable = available.filterNot { it.disableModelInvocation }
    val manualOnly = available.filter { it.disableModelInvocation }
    if (modelCallable.isEmpty()) {
        // All enabled skills are manual-only; do not advertise `use_skill` at
        // all so the model has no way to invoke any skill automatically.
        return emptyList()
    }

    return listOf(
        Tool(
            name = "use_skill",
            description = """
                Load and apply a skill to get specialized instructions or capabilities.
                Call this tool when the user's request matches one of the available skills.
            """.trimIndent(),
            systemPrompt = { _, _ ->
                buildString {
                    appendLine("**Skills**")
                    appendLine("You have access to the following skills. Use the `use_skill` tool to load a skill's instructions when the user's request matches.")
                    appendLine("<available_skills>")
                    modelCallable.forEach { skill ->
                        appendLine("  <skill>")
                        appendLine("    <name>${skill.name}</name>")
                        appendLine("    <description>${skill.description}</description>")
                        appendLine("  </skill>")
                    }
                    append("</available_skills>")
                    if (manualOnly.isNotEmpty()) {
                        appendLine()
                        appendLine(
                            "(${manualOnly.size} additional skill(s) are configured for manual-only " +
                                "invocation and are intentionally not listed here.)"
                        )
                    }
                }
            },
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("name", buildJsonObject {
                            put("type", "string")
                            put("description", "The name of the skill to use")
                        })
                        put("path", buildJsonObject {
                            put("type", "string")
                            put(
                                "description",
                                "Optional relative path to a file inside the skill directory. Omit to read the default SKILL.md instructions. Only use paths extracted from Markdown links in the SKILL.md content. Do NOT guess or infer paths."
                            )
                        })
                    },
                    required = listOf("name")
                )
            },
            execute = {
                val name = it.jsonObject["name"]?.jsonPrimitive?.content
                    ?: error("name is required")
                // The model can only learn names that appear in the system
                // prompt's <available_skills>, but explicit invocation paths
                // (today a code path, soon a UI / slash command) may still
                // call this executor with a manual-only skill name.
                val skill = modelCallable.firstOrNull { it.name == name }
                    ?: manualOnly.firstOrNull { it.name == name }
                    ?: error(
                        "Skill '$name' is not available. Available skills: " +
                            (modelCallable + manualOnly).joinToString { it.name }
                    )
                val path = it.jsonObject["path"]?.jsonPrimitive?.content
                val content = if (path.isNullOrBlank()) {
                    require(skill.skillFile.exists()) { "Skill '$name' not found" }
                    SkillFrontmatterParser.extractBody(skill.skillFile.readText())
                } else {
                    val target = SkillPaths.resolveSkillFile(skill.skillDir, path)
                        ?: error("Path '$path' is outside the skill directory")
                    require(target.exists()) { "File '$path' not found in skill '$name'" }
                    target.readText()
                }
                listOf(UIMessagePart.Text(content))
            }
        )
    )
}
