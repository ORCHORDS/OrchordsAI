package com.orchords.orchordsai.data.ai.tools.local

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalToolTimeGuidanceTest {
    @Test
    fun `time parsing guidance is stable and execution-time based`() {
        assertEquals(
            "Times without an explicit offset are interpreted using the device's current timezone at execution time.",
            localToolTimeParsingGuidance(),
        )
    }
}
