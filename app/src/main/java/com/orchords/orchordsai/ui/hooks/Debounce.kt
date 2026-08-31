package com.orchords.orchordsai.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 *
 */
@Composable
fun <T> useDebounce(
    delayMillis: Long = 300,
    function: (T) -> Unit
): (T) -> Unit {
    val scope = rememberCoroutineScope()
    val debounceJob = remember { mutableStateOf<Job?>(null) }

    return remember {
        { param: T ->
            debounceJob.value?.cancel()
            debounceJob.value = scope.launch {
                delay(delayMillis)
                function(param)
            }
        }
    }
}

/**
 *
 */
@Composable
fun <T> useThrottle(
    intervalMillis: Long = 300,
    function: (T) -> Unit
): (T) -> Unit {
    val scope = rememberCoroutineScope()
    val isThrottling = remember { AtomicBoolean(false) }
    val latestParam = remember { mutableStateOf<T?>(null) }

    return remember {
        { param: T ->
            latestParam.value = param

            if (!isThrottling.getAndSet(true)) {
                function(param)

                scope.launch {
                    delay(intervalMillis)
                    isThrottling.set(false)

                    latestParam.value?.let { latestValue ->
                        latestParam.value = null
                        function(latestValue)
                    }
                }
            }
        }
    }
}
