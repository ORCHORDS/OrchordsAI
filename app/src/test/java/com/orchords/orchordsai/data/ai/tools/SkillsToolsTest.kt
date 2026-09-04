package com.orchords.orchordsai.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.orchords.ai.provider.Model
import com.orchords.ai.ui.UIMessagePart
import com.orchords.orchordsai.data.files.SkillMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkillsToolsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `use_skill reads metadata directory when display name differs`() = runBlocking {
        val skillDir = tempFolder.newFolder("directory-name")
        skillDir.resolve("SKILL.md").writeText(
            """
                ---
                name: Display Name
                description: Test skill
                ---
                Skill instructions
            """.trimIndent()
        )
        val tool = createSkillTools(
            enabledSkills = setOf("Display Name"),
            allSkills = listOf(
                SkillMetadata(
                    name = "Display Name",
                    description = "Test skill",
                    skillDir = skillDir,
                )
            ),
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("name", "Display Name")
            }
        )

        assertEquals("Skill instructions", (result.single() as UIMessagePart.Text).text)
    }

    @Test
    fun `manual-only skills are excluded from the system prompt available_skills list`() = runBlocking {
        val autoDir = tempFolder.newFolder("auto-skill")
        autoDir.resolve("SKILL.md").writeText(
            "---\nname: auto\ndescription: Auto skill\n---\nbody"
        )
        val manualDir = tempFolder.newFolder("manual-skill")
        manualDir.resolve("SKILL.md").writeText(
            "---\nname: manual\ndescription: Manual-only skill\ndisable-model-invocation: true\n---\nbody"
        )

        val tools = createSkillTools(
            enabledSkills = setOf("auto", "manual"),
            allSkills = listOf(
                SkillMetadata(name = "auto", description = "Auto skill", skillDir = autoDir),
                SkillMetadata(
                    name = "manual",
                    description = "Manual-only skill",
                    disableModelInvocation = true,
                    skillDir = manualDir,
                ),
            ),
        )

        assertEquals(1, tools.size)
        val prompt = tools.single().systemPrompt(Model(), emptyList())
        assertTrue(
            "expected the system prompt to list the automatic skill, got: $prompt",
            prompt.contains("<name>auto</name>"),
        )
        assertFalse(
            "expected the system prompt to exclude the manual-only skill name, got: $prompt",
            prompt.contains("<name>manual</name>"),
        )
        assertFalse(
            "expected the system prompt to hide the manual-only skill description, got: $prompt",
            prompt.contains("Manual-only skill"),
        )
    }

    @Test
    fun `createSkillTools returns no tools when every enabled skill is manual-only`() = runBlocking {
        val manualDir = tempFolder.newFolder("manual-only")
        manualDir.resolve("SKILL.md").writeText(
            "---\nname: manual\ndescription: Manual-only skill\ndisable-model-invocation: true\n---\nbody"
        )

        val tools = createSkillTools(
            enabledSkills = setOf("manual"),
            allSkills = listOf(
                SkillMetadata(
                    name = "manual",
                    description = "Manual-only skill",
                    disableModelInvocation = true,
                    skillDir = manualDir,
                ),
            ),
        )

        assertTrue(
            "expected no tools when every enabled skill is manual-only, got: $tools",
            tools.isEmpty(),
        )
    }

    @Test
    fun `use_skill executor still resolves manual-only skills when called by exact name`() = runBlocking {
        val manualDir = tempFolder.newFolder("manual-only")
        manualDir.resolve("SKILL.md").writeText(
            "---\nname: manual\ndescription: Manual-only skill\ndisable-model-invocation: true\n---\nmanual body content"
        )

        val tools = createSkillTools(
            enabledSkills = setOf("manual"),
            allSkills = listOf(
                SkillMetadata(
                    name = "manual",
                    description = "Manual-only skill",
                    disableModelInvocation = true,
                    skillDir = manualDir,
                ),
            ),
        )

        assertTrue(
            "expected the manual-only skill to be unroutable by the model, got: $tools",
            tools.isEmpty(),
        )

        // When the executor IS available (mixed automatic + manual scenario)
        // and is invoked with the exact name of a manual-only skill it still
        // resolves to the underlying instructions.
        val autoDir = tempFolder.newFolder("auto")
        autoDir.resolve("SKILL.md").writeText("---\nname: auto\ndescription: Auto skill\n---\nauto body")

        val mixedTools = createSkillTools(
            enabledSkills = setOf("auto", "manual"),
            allSkills = listOf(
                SkillMetadata(name = "auto", description = "Auto skill", skillDir = autoDir),
                SkillMetadata(
                    name = "manual",
                    description = "Manual-only skill",
                    disableModelInvocation = true,
                    skillDir = manualDir,
                ),
            ),
        )
        val tool = mixedTools.single()
        val result = tool.execute(buildJsonObject { put("name", "manual") })
        assertEquals("manual body content", (result.single() as UIMessagePart.Text).text)
    }
}
