package com.orchords.orchordsai.docs

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins public README links to this repository's canonical GitHub path. */
class ReadmeRepositoryLinksPolicyTest {

    private fun readme(): String {
        val appModule = File(".").canonicalFile
        val repoRoot = appModule.parentFile.canonicalFile
        val readme = repoRoot.resolve("README.md")
        require(readme.isFile) {
            "Expected repository README.md at ${readme.path}; app unit tests must run from the app module"
        }
        return readme.readText()
    }

    @Test
    fun `README uses canonical Studio AI repository for build and provenance links`() {
        val readme = readme()

        assertFalse(
            "README must not reference the legacy ORCHORDS/OrchordsAI repository path",
            readme.contains("ORCHORDS/OrchordsAI"),
        )
        assertTrue(
            "Daily Build badge and workflow link must point at the canonical repository",
            readme.contains("https://github.com/ORCHORDS/OrchordsStudioAi/actions/workflows/daily-build.yml"),
        )
        assertTrue(
            "Dependency Audit badge and workflow link must point at the canonical repository",
            readme.contains("https://github.com/ORCHORDS/OrchordsStudioAi/actions/workflows/dependency-audit.yml"),
        )
        assertTrue(
            "Attestation verification must name the canonical repository",
            readme.contains("--repo ORCHORDS/OrchordsStudioAi"),
        )
        assertTrue(
            "Attestation signer workflow must point at the canonical repository",
            readme.contains("--signer-workflow ORCHORDS/OrchordsStudioAi/.github/workflows/daily-build.yml"),
        )
    }
}
