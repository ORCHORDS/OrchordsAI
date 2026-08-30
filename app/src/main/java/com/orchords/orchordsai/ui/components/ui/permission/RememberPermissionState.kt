package com.orchords.orchordsai.ui.components.ui.permission

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 *
 *
 * ```
 * val permissionState = rememberPermissionState(
 *     permissions = setOf(
 *         PermissionInfo(
 *             permission = Manifest.permission.CAMERA,
 *             required = true
 *         ),
 *         PermissionInfo(
 *             permission = Manifest.permission.RECORD_AUDIO,
 *             required = false
 *         )
 *     )
 * )
 *
 * Button(onClick = { permissionState.requestPermissions() }) {
 * }
 *
 * if (permissionState.allRequiredPermissionsGranted) {
 * }
 * ```
 */
@Composable
fun rememberPermissionState(
    permissions: Set<PermissionInfo>
): PermissionState {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
        ?: throw IllegalStateException("rememberPermissionState must be used inside a ComponentActivity")

    val permissionState = remember(permissions) {
        PermissionState(permissions, context, activity)
    }

    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionState.handlePermissionResult(results)
    }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val lastRequestedPermission = permissionState.currentRationalePermissions.firstOrNull()?.permission
            ?: permissionState.deniedPermissions.firstOrNull()?.permission

        lastRequestedPermission?.let { permission ->
            permissionState.handleSinglePermissionResult(permission, granted)
        }
    }

    LaunchedEffect(multiplePermissionLauncher, singlePermissionLauncher) {
        permissionState.setPermissionLaunchers(multiplePermissionLauncher, singlePermissionLauncher)
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    permissionState.refreshPermissionStates()
                }

                Lifecycle.Event.ON_RESUME -> {
                    permissionState.refreshPermissionStates()
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        permissionState.updatePermissionStates()
    }

    return permissionState
}

/**
 *
 */
@Composable
fun rememberPermissionState(
    permission: String,
    displayName: @Composable () -> Unit,
    usage: @Composable () -> Unit,
    required: Boolean = false
): PermissionState {
    return rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = permission,
                displayName = displayName,
                usage = usage,
                required = required,
            )
        )
    )
}

@Composable
fun rememberPermissionState(
    info: PermissionInfo
): PermissionState {
    return rememberPermissionState(
        permissions = setOf(info)
    )
}
