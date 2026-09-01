package com.orchords.orchordsai.ui.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CameraShortcutPolicyTest {
    @Test
    fun `only exact static camera shortcut shape is accepted`() {
        assertTrue(isTrustedCameraShortcutInvocation("android.intent.action.VIEW", "orchordsai://shortcut"))

        assertFalse(isTrustedCameraShortcutInvocation(null, "orchordsai://shortcut"))
        assertFalse(isTrustedCameraShortcutInvocation("android.intent.action.SEND", "orchordsai://shortcut"))
        assertFalse(isTrustedCameraShortcutInvocation("android.intent.action.VIEW", null))
        assertFalse(isTrustedCameraShortcutInvocation("android.intent.action.VIEW", "orchordsai://shortcut/extra"))
        assertFalse(isTrustedCameraShortcutInvocation("android.intent.action.VIEW", "orchordsai://shortcut?source=external"))
        assertFalse(isTrustedCameraShortcutInvocation("android.intent.action.VIEW", "https://shortcut"))
    }

    @Test
    fun `camera shortcut handler is not an exported deep link`() {
        val moduleDir = File(".").canonicalFile
        require(moduleDir.resolve("src/main").isDirectory) {
            "Unexpected working directory ${moduleDir.path}: unit tests must run from the app module"
        }
        val manifest = moduleDir.resolve("src/main/AndroidManifest.xml").readText()
        val shortcuts = moduleDir.resolve("src/main/res/xml/shortcuts.xml").readText()

        val activityBlock = Regex(
            "<activity\\s+android:name=\\\"com\\.orchords\\.orchordsai\\.ui\\.activity\\.ShortcutHandlerActivity\\\"[\\s\\S]*?(?:/>|</activity>)"
        ).find(manifest)?.value ?: error("ShortcutHandlerActivity manifest entry missing")

        assertTrue(activityBlock.contains("android:exported=\"false\""))
        assertFalse(activityBlock.contains("<intent-filter>"))
        assertTrue(shortcuts.contains("android:targetClass=\"com.orchords.orchordsai.ui.activity.ShortcutHandlerActivity\""))
        assertTrue(shortcuts.contains("android:data=\"orchordsai://shortcut\""))
    }
}
