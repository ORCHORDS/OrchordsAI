package com.orchords.orchordsai.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebServerPendingIntentPolicyTest {
    @Test
    fun `notification launch pending intent uses explicit immutable activity intent`() {
        val source = File(
            "src/main/java/com/orchords/orchordsai/service/WebServerService.kt"
        ).readText()

        assertTrue(source.contains("Intent(this, OrchordsAiActivity::class.java)"))
        assertTrue(source.contains("PendingIntent.FLAG_IMMUTABLE"))
        assertFalse(source.contains("getLaunchIntentForPackage"))
    }
}
