package com.orchords.orchordsai.ui.hooks

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
@Composable
fun ImeLazyListAutoScroller(
    lazyListState: LazyListState,
    conversationId: String,
    onImeHeightChange: (Int) -> Int,
) {
    val ime = WindowInsets.ime
    val localDensity = LocalDensity.current
    val onImeHeightChangeState by rememberUpdatedState(onImeHeightChange)
    LaunchedEffect(ime, localDensity, conversationId) {
        snapshotFlow { ime.getBottom(localDensity) }.collect { keyboardHeight ->
            val scrollDelta = onImeHeightChangeState(keyboardHeight)
            if (scrollDelta != 0) {
                lazyListState.scrollBy(scrollDelta.toFloat())
            }
        }
    }
}
