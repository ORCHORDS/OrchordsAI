package com.orchords.orchordsai.ui.context

import androidx.compose.runtime.compositionLocalOf
import com.orchords.orchordsai.ui.hooks.CustomTtsState

val LocalTTSState = compositionLocalOf<CustomTtsState> { error("Not provided yet") }
