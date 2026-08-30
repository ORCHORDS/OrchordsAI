package com.orchords.orchordsai.ui.components.ui.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat

/**
 */
@Stable
class PermissionState internal constructor(
    private val permissions: Set<PermissionInfo>,
    private val context: Context,
    private val activity: ComponentActivity
) {
    private val _permissionStates = mutableStateMapOf<String, PermissionStatus>()
    val permissionStates: Map<String, PermissionStatus> = _permissionStates

    var showRationaleDialog by mutableStateOf(false)
        private set

    var currentRationalePermissions by mutableStateOf<List<PermissionInfo>>(emptyList())
        private set

    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    private var singlePermissionLauncher: ActivityResultLauncher<String>? = null

    init {
        updatePermissionStates()
    }

    /**
     */
    internal fun setPermissionLaunchers(
        multiplePermissionLauncher: ActivityResultLauncher<Array<String>>,
        singlePermissionLauncher: ActivityResultLauncher<String>
    ) {
        this.permissionLauncher = multiplePermissionLauncher
        this.singlePermissionLauncher = singlePermissionLauncher
    }

    /**
     */
    fun updatePermissionStates() {
        permissions.forEach { permissionInfo ->
            val oldStatus = _permissionStates[permissionInfo.permission]
            val newStatus = getPermissionStatus(permissionInfo.permission, oldStatus)
            _permissionStates[permissionInfo.permission] = newStatus
        }
    }

    /**
     */
    private fun getPermissionStatus(permission: String, oldStatus: PermissionStatus? = null): PermissionStatus {
        return when {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> {
                PermissionStatus.Granted
            }
            activity.shouldShowRequestPermissionRationale(permission) -> {
                PermissionStatus.Denied
            }
            (oldStatus == PermissionStatus.Denied || oldStatus == PermissionStatus.DeniedPermanently) -> {
                PermissionStatus.DeniedPermanently
            }
            else -> {
                PermissionStatus.NotRequested
            }
        }
    }

    /**
     */
    val allPermissionsGranted: Boolean
        get() = permissions.all { permissionStates[it.permission] == PermissionStatus.Granted }

    /**
     */
    val allRequiredPermissionsGranted: Boolean
        get() = permissions.filter { it.required }.all { permissionStates[it.permission] == PermissionStatus.Granted }

    /**
     */
    val deniedPermissions: List<PermissionInfo>
        get() = permissions.filter { permissionStates[it.permission] != PermissionStatus.Granted }

    /**
     */
    private val permissionsNeedRationale: List<PermissionInfo>
        get() = permissions.filter {
            val status = permissionStates[it.permission]
            status == PermissionStatus.Denied && activity.shouldShowRequestPermissionRationale(it.permission) ||
            status == PermissionStatus.DeniedPermanently
        }

    /**
     */
    val permanentlyDeniedPermissions: List<PermissionInfo>
        get() = permissions.filter { permissionStates[it.permission] == PermissionStatus.DeniedPermanently }

    /**
     */
    fun requestPermissions() {
        val deniedPerms = deniedPermissions
        if (deniedPerms.isEmpty()) return

        val rationalePerms = permissionsNeedRationale
        if (rationalePerms.isNotEmpty()) {
            currentRationalePermissions = rationalePerms
            showRationaleDialog = true
        } else {
            launchPermissionRequest(deniedPerms)
        }
    }

    /**
     */
    fun requestPermission(permission: String) {
        val permissionInfo = permissions.find { it.permission == permission } ?: return
        val status = permissionStates[permission] ?: return

        if (status == PermissionStatus.Granted) return

        when (status) {
            PermissionStatus.Denied -> {
                if (activity.shouldShowRequestPermissionRationale(permission)) {
                    currentRationalePermissions = listOf(permissionInfo)
                    showRationaleDialog = true
                } else {
                    singlePermissionLauncher?.launch(permission)
                }
            }
            PermissionStatus.DeniedPermanently -> {
                currentRationalePermissions = listOf(permissionInfo)
                showRationaleDialog = true
            }
            else -> {
                singlePermissionLauncher?.launch(permission)
            }
        }
    }

    /**
     */
    fun proceedFromRationale() {
        showRationaleDialog = false

        val permanentlyDenied = currentRationalePermissions.filter {
            permissionStates[it.permission] == PermissionStatus.DeniedPermanently
        }

        if (permanentlyDenied.isNotEmpty()) {
            openAppSettings()
        } else {
            launchPermissionRequest(currentRationalePermissions)
        }

        currentRationalePermissions = emptyList()
    }

    /**
     */
    fun cancelPermissionRequest() {
        showRationaleDialog = false
        currentRationalePermissions = emptyList()
    }

    /**
     */
    private fun launchPermissionRequest(permissionInfos: List<PermissionInfo>) {
        val permissionsToRequest = permissionInfos.map { it.permission }.toTypedArray()
        if (permissionsToRequest.size == 1) {
            singlePermissionLauncher?.launch(permissionsToRequest[0])
        } else {
            permissionLauncher?.launch(permissionsToRequest)
        }
    }

    /**
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        activity.startActivity(intent)
    }

    /**
     */
    fun refreshPermissionStates() {
        permissions.forEach { permissionInfo ->
            val currentSystemStatus = ContextCompat.checkSelfPermission(context, permissionInfo.permission)
            val oldStatus = _permissionStates[permissionInfo.permission]

            val newStatus = when {
                currentSystemStatus == PackageManager.PERMISSION_GRANTED -> {
                    PermissionStatus.Granted
                }
                activity.shouldShowRequestPermissionRationale(permissionInfo.permission) -> {
                    PermissionStatus.Denied
                }
                else -> {
                    if (oldStatus == PermissionStatus.NotRequested || oldStatus == null) {
                        PermissionStatus.NotRequested
                    } else {
                        PermissionStatus.DeniedPermanently
                    }
                }
            }

            _permissionStates[permissionInfo.permission] = newStatus
        }
    }

    /**
     */
    internal fun handlePermissionResult(results: Map<String, Boolean>) {
        results.forEach { (permission, granted) ->
            _permissionStates[permission] = if (granted) {
                PermissionStatus.Granted
            } else {
                if (activity.shouldShowRequestPermissionRationale(permission)) {
                    PermissionStatus.Denied
                } else {
                    PermissionStatus.DeniedPermanently
                }
            }
        }
    }

    /**
     */
    internal fun handleSinglePermissionResult(permission: String, granted: Boolean) {
        handlePermissionResult(mapOf(permission to granted))
    }

    /**
     */
    fun getPermissionResults(): MultiplePermissionResult {
        val results = permissions.associate { permissionInfo ->
            val status = permissionStates[permissionInfo.permission] ?: PermissionStatus.NotRequested
            permissionInfo.permission to PermissionResult(
                permission = permissionInfo.permission,
                status = status
            )
        }

        return MultiplePermissionResult(
            results = results,
            allRequiredGranted = permissions.filter { it.required }
                .all { permissionStates[it.permission] == PermissionStatus.Granted }
        )
    }
}
