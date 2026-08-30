package com.orchords.orchordsai.ui.context

import androidx.compose.runtime.compositionLocalOf
import com.orchords.orchordsai.ui.hooks.CustomAsrState

val LocalASRState = compositionLocalOf<CustomAsrState> { error("Not provided yet") }

