package com.orchords.orchordsai.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Remembers the current Lifecycle.State of the application's LifecycleOwner
 * (usually the Activity or Fragment hosting the Compose UI).
 *
 * The returned State object will update whenever the lifecycle state changes
 * (e.g., from STARTED to RESUMED).
 *
 * @return A State object holding the current Lifecycle.State.
 */
@Composable
fun rememberAppLifecycleState(): State<Lifecycle.State> {
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState = remember { mutableStateOf(lifecycleOwner.lifecycle.currentState) }
    DisposableEffect(lifecycleOwner.lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            lifecycleState.value = lifecycleOwner.lifecycle.currentState
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return lifecycleState
}
