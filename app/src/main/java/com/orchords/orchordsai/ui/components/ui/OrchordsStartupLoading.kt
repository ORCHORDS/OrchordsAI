package com.orchords.orchordsai.ui.components.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orchords.orchordsai.R
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.db.DatabaseMigrationTracker
import com.orchords.orchordsai.data.db.MigrationState
import org.koin.compose.koinInject

/** Both existing hosts observe the same readiness; decorative timing cannot hide active work. */
@Composable
fun OrchardsStartupLoadingIndicator(
    modifier: Modifier = Modifier,
    detail: String? = null,
    onFinished: () -> Unit = {},
) {
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val migrationState by DatabaseMigrationTracker.state.collectAsStateWithLifecycle()
    val migration = migrationState as? MigrationState.Migrating
    val currentOnFinished by rememberUpdatedState(onFinished)
    var firstFrameDrawn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        firstFrameDrawn = true
    }
    val canFinish = startupCanFinish(
        firstFrameDrawn = firstFrameDrawn,
        settingsInitializing = settings.init,
        migrationActive = migration != null,
        persistent = detail != null,
    )
    LaunchedEffect(canFinish) {
        if (canFinish) currentOnFinished()
    }

    val status = detail ?: migration?.let {
        stringResource(R.string.startup_migration_progress, it.from, it.to)
    } ?: stringResource(R.string.startup_loading)

    // A Column owns distinct bounds; the dragon never fades on top of the wordmark.
    // Scrolling keeps the status readable on compact displays and at large font scales.
    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Final).changes.forEach { it.consume() }
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(R.drawable.orchords_wordmark_blue),
            contentDescription = null,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        Image(
            painter = painterResource(R.drawable.orchords_logo_blue),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
        Text(
            text = status,
            modifier = Modifier.widthIn(max = 360.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
