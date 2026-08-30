package com.orchords.orchordsai.ui.context

import androidx.compose.runtime.staticCompositionLocalOf
import com.orchords.orchordsai.data.datastore.Settings

val LocalSettings = staticCompositionLocalOf<Settings> {
    error("No SettingsStore provided")
}
