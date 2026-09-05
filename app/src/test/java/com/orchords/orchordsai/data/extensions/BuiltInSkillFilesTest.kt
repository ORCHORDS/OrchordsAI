package com.orchords.orchordsai.data.extensions

import java.nio.file.Files
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInSkillFilesTest {
    @Test fun `install preserves edits and malformed existing directories`() {
        val base = Files.createTempDirectory("bundled-skill-test-").toFile()
        try {
            val root = base.resolve("skills")
            assertEquals(SkillInstallDisposition.INSTALLED, installNewSkillContent(root, "test-skill", "original"))
            root.resolve("test-skill/SKILL.md").writeText("user edit")
            assertEquals(SkillInstallDisposition.ALREADY_PRESENT, installNewSkillContent(root, "test-skill", "replacement"))
            assertEquals("user edit", root.resolve("test-skill/SKILL.md").readText())
            root.resolve("unreadable-skill").mkdir()
            assertEquals(SkillInstallDisposition.ALREADY_PRESENT, installNewSkillContent(root, "unreadable-skill", "body"))
            assertTrue(root.resolve("unreadable-skill").listFiles()!!.isEmpty())
        } finally { base.deleteRecursively() }
    }
    @Test fun `concurrent create-only installation publishes one complete bundle`() {
        val base = Files.createTempDirectory("bundled-skill-race-").toFile()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val root = base.resolve("skills")
            val results = executor.invokeAll((0 until 24).map {
                java.util.concurrent.Callable { installNewSkillContent(root, "same-skill", "complete") }
            }).map { it.get() }
            assertEquals(1, results.count { it == SkillInstallDisposition.INSTALLED })
            assertEquals(23, results.count { it == SkillInstallDisposition.ALREADY_PRESENT })
            assertEquals("complete", root.resolve("same-skill/SKILL.md").readText())
            assertEquals(listOf("skills"), base.listFiles()!!.map { it.name })
        } finally { executor.shutdownNow(); base.deleteRecursively() }
    }
}
