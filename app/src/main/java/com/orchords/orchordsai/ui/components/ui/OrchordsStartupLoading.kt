package com.orchords.orchordsai.ui.components.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.orchords.orchordsai.R

private const val ENTER_MILLIS = 450
private const val EXIT_MILLIS = 350

/** Owns the complete wordmark -> dragon -> main-window startup sequence. */
@Composable
fun OrchardsStartupLoadingIndicator(
    modifier: Modifier = Modifier,
    detail: String? = null,
    onFinished: () -> Unit = {},
) {
    @Suppress("UNUSED_VARIABLE") val migrationDetail = detail
    val wordmarkAlpha = remember { Animatable(1f) }
    val dragonAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        dragonAlpha.animateTo(1f, tween(ENTER_MILLIS))
        wordmarkAlpha.animateTo(0f, tween(EXIT_MILLIS))
        dragonAlpha.animateTo(0f, tween(EXIT_MILLIS))
        onFinished()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.orchords_wordmark_blue),
            contentDescription = null,
            modifier = Modifier
                .width(144.dp)
                .graphicsLayer { alpha = wordmarkAlpha.value },
        )
        Image(
            painter = painterResource(R.drawable.orchords_logo_blue),
            contentDescription = null,
            modifier = Modifier
                .size(144.dp)
                .graphicsLayer { alpha = dragonAlpha.value },
        )
    }
}
