package com.orchords.orchordsai.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillFrontmatterParserTest {
    @Test
    fun `getBoolean reads disable-model-invocation when it is true`() {
        val fm = SkillFrontmatterParser.parse(
            """
            ---
            name: manual-only
            description: a skill that the model must not auto-invoke
            disable-model-invocation: true
            ---
            body
            """.trimIndent()
        )
        assertEquals(true, fm.getBoolean("disable-model-invocation"))
        assertEquals("manual-only", fm["name"])
    }

    @Test
    fun `getBoolean reads disable-model-invocation when it is false`() {
        val fm = SkillFrontmatterParser.parse(
            """
            ---
            name: auto
            description: a skill the model can auto-invoke
            disable-model-invocation: false
            ---
            body
            """.trimIndent()
        )
        assertEquals(false, fm.getBoolean("disable-model-invocation"))
    }

    @Test
    fun `getBoolean defaults to null when field is absent`() {
        val fm = SkillFrontmatterParser.parse(
            """
            ---
            name: auto
            description: a skill the model can auto-invoke
            ---
            body
            """.trimIndent()
        )
        assertNull(fm.getBoolean("disable-model-invocation"))
    }

    @Test
    fun `getBoolean accepts boolean-like string forms true, True and TRUE`() {
        listOf("true", "True", "TRUE").forEach { value ->
            val fm = SkillFrontmatterParser.parse(
                "---\nname: a\ndescription: b\ndisable-model-invocation: $value\n---\nbody"
            )
            assertEquals("value=$value", true, fm.getBoolean("disable-model-invocation"))
        }
    }

    @Test
    fun `getBoolean accepts boolean-like string forms false, False and FALSE`() {
        listOf("false", "False", "FALSE").forEach { value ->
            val fm = SkillFrontmatterParser.parse(
                "---\nname: a\ndescription: b\ndisable-model-invocation: $value\n---\nbody"
            )
            assertEquals("value=$value", false, fm.getBoolean("disable-model-invocation"))
        }
    }

    @Test
    fun `getBoolean returns null for non-boolean strings`() {
        val fm = SkillFrontmatterParser.parse(
            "---\nname: a\ndescription: b\ndisable-model-invocation: maybe\n---\nbody"
        )
        assertNull(fm.getBoolean("disable-model-invocation"))
    }

    @Test
    fun `getBoolean returns null for numeric values that are not strict booleans`() {
        val fm = SkillFrontmatterParser.parse(
            "---\nname: a\ndescription: b\ndisable-model-invocation: 1\n---\nbody"
        )
        assertNull(fm.getBoolean("disable-model-invocation"))
    }

    @Test
    fun `extractBody still works alongside new boolean accessor`() {
        val source = "---\nname: a\ndescription: b\ndisable-model-invocation: true\n---\nactual body content\n"
        val fm = SkillFrontmatterParser.parse(source)
        val body = SkillFrontmatterParser.extractBody(source)
        assertEquals("actual body content\n", body)
        assertTrue(fm.getBoolean("disable-model-invocation") == true)
        assertFalse(fm.getBoolean("disable-model-invocation") != true)
    }
}
