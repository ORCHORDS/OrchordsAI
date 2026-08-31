package com.orchords.material3

import androidx.compose.ui.graphics.Color
import hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import scheme.SchemeTonalSpot

class DynamicSchemeExtTest {
    private val source = Hct.fromInt(0xFF6750A4.toInt())

    @Test
    fun `light scheme preserves semantic error roles`() {
        assertErrorRoles(isDark = false)
    }

    @Test
    fun `dark scheme preserves semantic error roles`() {
        assertErrorRoles(isDark = true)
    }

    private fun assertErrorRoles(isDark: Boolean) {
        val scheme = SchemeTonalSpot(
            sourceColorHct = source,
            isDark = isDark,
            contrastLevel = 0.0,
        )
        val colorScheme = scheme.toColorScheme()

        assertEquals(Color(scheme.error), colorScheme.error)
        assertEquals(Color(scheme.onError), colorScheme.onError)
        assertEquals(Color(scheme.errorContainer), colorScheme.errorContainer)
        assertEquals(Color(scheme.onErrorContainer), colorScheme.onErrorContainer)

        assertNotEquals(colorScheme.error, colorScheme.onError)
        assertNotEquals(colorScheme.errorContainer, colorScheme.onErrorContainer)
    }
}
