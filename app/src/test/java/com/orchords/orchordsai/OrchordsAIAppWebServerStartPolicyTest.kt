package com.orchords.orchordsai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrchordsAIAppWebServerStartPolicyTest {
    @Test
    fun `android 13 auto-start is not suppressed by notification permission state`() {
        // POST_NOTIFICATIONS is intentionally not an input to this policy.
        assertTrue(
            webServerNetworkPermissionAllowsAutoStart(
                apiLevel = 33,
                localhostOnly = false,
                localNetworkPermissionGranted = false,
            )
        )
    }

    @Test
    fun `android 17 localhost-only auto-start ignores LAN permission`() {
        assertTrue(
            webServerNetworkPermissionAllowsAutoStart(
                apiLevel = 37,
                localhostOnly = true,
                localNetworkPermissionGranted = false,
            )
        )
    }

    @Test
    fun `android 17 LAN auto-start proceeds when local network permission is granted`() {
        assertTrue(
            webServerNetworkPermissionAllowsAutoStart(
                apiLevel = 37,
                localhostOnly = false,
                localNetworkPermissionGranted = true,
            )
        )
    }

    @Test
    fun `android 17 LAN auto-start is blocked when local network permission is denied`() {
        assertFalse(
            webServerNetworkPermissionAllowsAutoStart(
                apiLevel = 37,
                localhostOnly = false,
                localNetworkPermissionGranted = false,
            )
        )
    }
}
