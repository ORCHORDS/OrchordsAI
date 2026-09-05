package com.orchords.orchordsai.data.extensions

import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption

internal enum class SkillInstallDisposition { INSTALLED, ALREADY_PRESENT }
private val bundledSkillInstallLock = Any()
private const val MAX_BUNDLED_SKILL_BYTES = 256 * 1024

/**
 * Create-only publication into SkillManager's existing root. Never replace an existing
 * directory, including a malformed/user-edited bundle that listSkills cannot parse.
 * Staging is outside the discoverable skills root and contains only this operation's file.
 */
internal fun installNewSkillContent(
    skillsRoot: File,
    name: String,
    content: String,
): SkillInstallDisposition = synchronized(bundledSkillInstallLock) {
    require(name.length <= 64 && Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(name))
    val bytes = content.toByteArray(Charsets.UTF_8)
    require(bytes.isNotEmpty() && bytes.size <= MAX_BUNDLED_SKILL_BYTES)
    require(!Files.isSymbolicLink(skillsRoot.toPath())) { "Skill root must not be a symlink" }
    check(skillsRoot.isDirectory || skillsRoot.mkdirs()) { "Skill storage is unavailable" }
    val root = skillsRoot.canonicalFile
    val target = root.resolve(name).toPath()
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
        return@synchronized SkillInstallDisposition.ALREADY_PRESENT
    }
    val staging = Files.createTempDirectory(root.parentFile.toPath(), ".orchords-skill-install-").toFile()
    try {
        FileOutputStream(staging.resolve("SKILL.md")).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        // No REPLACE_EXISTING and no ATOMIC_MOVE: the latter can replace an existing target.
        // A racing pre-existing bundle must win, not be overwritten by a stock definition.
        Files.move(staging.toPath(), target)
        SkillInstallDisposition.INSTALLED
    } catch (_: FileAlreadyExistsException) {
        SkillInstallDisposition.ALREADY_PRESENT
    } finally {
        if (staging.exists()) staging.deleteRecursively()
    }
}
