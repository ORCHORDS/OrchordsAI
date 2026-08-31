package com.orchords.orchordsai.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidBackupPolicyTest {
    private val domains = listOf("root", "file", "database", "sharedpref", "external")

    private fun moduleFile(relative: String): File {
        val moduleDir = File(".").canonicalFile
        require(moduleDir.resolve("src/main/res").isDirectory) {
            "Unexpected working directory ${moduleDir.path}: unit tests must run from the app module"
        }
        val file = moduleDir.resolve(relative).canonicalFile
        require(file.toPath().startsWith(moduleDir.toPath())) { "Path escapes app module: $relative" }
        return file
    }

    private fun source(relative: String): String = moduleFile(relative).readText()

    private fun assertAllDomainsExcluded(xml: String, section: String? = null) {
        val target = if (section == null) {
            xml
        } else {
            Regex("<$section[^>]*>[\\s\\S]*?</$section>")
                .find(xml)?.value ?: error("Missing <$section> backup policy")
        }
        domains.forEach { domain ->
            val rule = Regex("<exclude\\s+domain=\\\"$domain\\\"\\s+path=\\\"\\.\\\"\\s*/>")
            assertTrue("$section must explicitly exclude $domain storage", rule.containsMatchIn(target))
        }
    }

    @Test
    fun `android 12 plus cloud and device transfer are deny all until secret stores are split`() {
        val xml = source("src/main/res/xml/data_extraction_rules.xml")

        assertAllDomainsExcluded(xml, "cloud-backup")
        assertAllDomainsExcluded(xml, "device-transfer")
        assertFalse("Android OS backup must not include mixed-secret app state", xml.contains("<include"))
        assertFalse("Generated TODO backup template must not return", xml.contains("TODO", ignoreCase = true))
    }

    @Test
    fun `legacy auto backup follows the same deny all policy`() {
        val xml = source("src/main/res/xml/backup_rules.xml")

        assertAllDomainsExcluded(xml)
        assertFalse("Legacy Auto Backup must not include upload files without their owning database", xml.contains("<include"))
    }

    @Test
    fun `manifest keeps both versioned policy resources explicit`() {
        val manifest = source("src/main/AndroidManifest.xml")

        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
    }
}
