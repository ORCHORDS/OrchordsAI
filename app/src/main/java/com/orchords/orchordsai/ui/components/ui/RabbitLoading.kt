package com.orchords.orchordsai.ui.components.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.orchords.orchordsai.R
import com.orchords.orchordsai.ui.context.LocalSettings

@Composable
fun RabbitLoadingIndicator(modifier: Modifier = Modifier) {
    val useAppIconStyleLoadingIndicator = LocalSettings.current.displaySetting.useAppIconStyleLoadingIndicator

    if (useAppIconStyleLoadingIndicator) {
        val logoRes = if (isSystemInDarkTheme()) {
            R.drawable.orchords_logo_white
        } else {
            R.drawable.orchords_logo_blue
        }
        val pulse = rememberInfiniteTransition(label = "OrchordsLoadingPulse")
        val alpha by pulse.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "OrchordsLoadingAlpha",
        )
        Image(
            painter = painterResource(logoRes),
            contentDescription = null,
            modifier = modifier.graphicsLayer {
                this.alpha = alpha
            },
        )
    } else {
        ContainedLoadingIndicator(
            modifier = modifier,
        )
    }
}
