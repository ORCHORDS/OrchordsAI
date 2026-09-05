package com.orchords.orchordsai.ui.components.ui

/** Presentation time is never evidence that settings or a migration finished. */
internal fun startupCanFinish(
    firstFrameDrawn: Boolean,
    settingsInitializing: Boolean,
    migrationActive: Boolean,
    persistent: Boolean,
): Boolean = firstFrameDrawn && !settingsInitializing && !migrationActive && !persistent
