package com.orchords.orchordsai.data.extensions

import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.files.SkillFrontmatterParser
import com.orchords.orchordsai.data.files.SkillManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class BuiltInLibraryInstallResult(
    val addedModes: Int,
    val addedLorebooks: Int,
    val addedSkills: Int,
    val preservedSkills: Int,
    val failedSkills: List<String>,
)

/** Explicit, resumable installation; it never selects content or authorizes an account. */
class BuiltInLibraryInstaller(
    private val settingsStore: SettingsStore,
    private val skillManager: SkillManager,
) {
    suspend fun installMissing(): BuiltInLibraryInstallResult = withContext(Dispatchers.IO) {
        val content = settingsStore.installBuiltInLibraryContent()
        var added = 0
        var preserved = 0
        val failed = mutableListOf<String>()
        for (skill in BuiltInLibrary.catalog.skills) {
            currentCoroutineContext().ensureActive()
            try {
                val fileContent = skill.skillFile()
                val frontmatter = SkillFrontmatterParser.parse(fileContent)
                require(frontmatter["name"] == skill.name && frontmatter["description"] == skill.description) {
                    "Bundled skill metadata is invalid"
                }
                require(SkillFrontmatterParser.extractBody(fileContent).isNotBlank())
                when (installNewSkillContent(skillManager.getSkillsDir(), skill.name, fileContent)) {
                    SkillInstallDisposition.INSTALLED -> added++
                    SkillInstallDisposition.ALREADY_PRESENT -> preserved++
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Only a bounded, first-party identifier reaches the UI, never exception contents.
                failed += skill.name
            }
        }
        BuiltInLibraryInstallResult(content.addedModes, content.addedLorebooks, added, preserved, failed)
    }
}
