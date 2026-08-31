package com.orchords.orchordsai.ui.activity

internal const val CAMERA_SHORTCUT_ACTION = "android.intent.action.VIEW"
internal const val CAMERA_SHORTCUT_URI = "orchordsai://shortcut"

internal fun isTrustedCameraShortcutInvocation(action: String?, data: String?): Boolean =
    action == CAMERA_SHORTCUT_ACTION && data == CAMERA_SHORTCUT_URI
