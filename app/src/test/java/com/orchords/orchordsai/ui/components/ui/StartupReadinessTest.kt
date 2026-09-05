package com.orchords.orchordsai.ui.components.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupReadinessTest {
    @Test
    fun `only ready nonpersistent startup can complete after its first frame`() {
        for (frame in listOf(false, true)) for (initializing in listOf(false, true))
            for (migrating in listOf(false, true)) for (persistent in listOf(false, true)) {
                assertEquals(
                    "frame=$frame initializing=$initializing migrating=$migrating persistent=$persistent",
                    frame && !initializing && !migrating && !persistent,
                    startupCanFinish(frame, initializing, migrating, persistent),
                )
            }
    }
}
