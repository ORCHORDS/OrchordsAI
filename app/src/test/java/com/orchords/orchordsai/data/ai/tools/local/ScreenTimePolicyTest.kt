package com.orchords.orchordsai.data.ai.tools.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimePolicyTest {
    @Test
    fun `screen time read does not require approval when usage access already exists`() {
        assertFalse(screenTimeNeedsApproval(hasUsageAccess = true))
    }

    @Test
    fun `screen time requires approval before opening usage access settings`() {
        assertTrue(screenTimeNeedsApproval(hasUsageAccess = false))
    }
}
