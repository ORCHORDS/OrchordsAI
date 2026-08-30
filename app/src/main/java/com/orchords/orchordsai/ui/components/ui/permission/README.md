# Permission library usage

A complete Android runtime permission library with automatic rationale dialogs.

## Features

- 🎯 **Declarative API**: Compose-style permission management
- 🔄 **State management**: automatic permission state tracking
- 💬 **Smart dialogs**: automatically shows permission rationale dialogs
- 🎨 **Material Design 3**: follows the latest design guidelines
- 🔧 **Flexible configuration**: required/optional permission classification
- 🔄 **Lifecycle aware**: permission state refreshes when the app returns to the foreground
- ⚡ **Permanent denial handling**: guides the user to app settings

## Basic usage

### 1. Single permission

```kotlin
@Composable
fun CameraScreen() {
    val cameraPermission = rememberPermissionState(
        permission = Manifest.permission.CAMERA,
        usage = {
            Text(
                text = "Camera access is needed to take photos and record video",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        required = true
    )

    PermissionManager(permissionState = cameraPermission) {
        PermissionCheck(
            permissionState = cameraPermission,
            onGranted = {
                // Permission granted, show the camera UI
                CameraContent()
            },
            onDenied = { state ->
                // Permission denied, show custom content
                Button(onClick = { state.requestPermissions() }) {
                    Text("Request camera permission")
                }
            }
        )
    }
}
```

### 2. Multiple permissions

```kotlin
@Composable
fun MediaScreen() {
    val mediaPermissions = rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = Manifest.permission.CAMERA,
                usage = { Text("Camera access is needed to take photos") },
                required = true
            ),
            PermissionInfo(
                permission = Manifest.permission.RECORD_AUDIO,
                usage = { Text("Microphone access is needed to record video") },
                required = false
            ),
            PermissionInfo(
                permission = Manifest.permission.READ_EXTERNAL_STORAGE,
                usage = { Text("Storage access is needed to save media files") },
                required = true
            )
        )
    )

    PermissionManager(permissionState = mediaPermissions) {
        when {
            mediaPermissions.allRequiredPermissionsGranted -> {
                // All required permissions granted
                MediaContent(
                    hasAudioPermission = mediaPermissions.permissionStates[Manifest.permission.RECORD_AUDIO] == PermissionStatus.Granted
                )
            }
            else -> {
                // Show the permission request UI
                Column {
                    Text("Permissions are required to continue")
                    Button(onClick = { mediaPermissions.requestPermissions() }) {
                        Text("Request permissions")
                    }
                }
            }
        }
    }
}
```

### 3. Checking permission state

```kotlin
@Composable
fun PermissionStatusExample() {
    val permissionState = rememberPermissionState(...)

    // Check all permissions
    if (permissionState.allPermissionsGranted) {
        Text("All permissions granted")
    }

    // Check required permissions
    if (permissionState.allRequiredPermissionsGranted) {
        Text("All required permissions granted")
    }

    // Check a single permission
    when (permissionState.permissionStates[Manifest.permission.CAMERA]) {
        PermissionStatus.Granted -> Text("Camera permission granted")
        PermissionStatus.Denied -> Text("Camera permission denied")
        PermissionStatus.DeniedPermanently -> Text("Camera permission permanently denied")
        PermissionStatus.NotRequested -> Text("Camera permission not requested")
        null -> Text("Permission state unknown")
    }
}
```

### 4. Manual permission management

```kotlin
@Composable
fun ManualPermissionExample() {
    val permissionState = rememberPermissionState(...)

    Column {
        Button(onClick = {
            // Request all permissions
            permissionState.requestPermissions()
        }) {
            Text("Request all permissions")
        }

        Button(onClick = {
            // Request a specific permission
            permissionState.requestPermission(Manifest.permission.CAMERA)
        }) {
            Text("Request camera permission")
        }

        Button(onClick = {
            // Open app settings
            permissionState.openAppSettings()
        }) {
            Text("Open settings")
        }

        Button(onClick = {
            // Refresh permission state
            permissionState.updatePermissionStates()
        }) {
            Text("Refresh permission state")
        }
    }
}
```

## API reference

### PermissionInfo

```kotlin
data class PermissionInfo(
    val permission: String,        // Android permission string
    val usage: @Composable () -> Unit,  // permission rationale
    val required: Boolean = false  // whether the permission is required
)
```

### PermissionState

Main properties:
- `permissionStates: Map<String, PermissionStatus>` — permission state map
- `allPermissionsGranted: Boolean` — whether all permissions are granted
- `allRequiredPermissionsGranted: Boolean` — whether all required permissions are granted
- `deniedPermissions: List<PermissionInfo>` — the denied permissions

Main methods:
- `requestPermissions()` — request all ungranted permissions
- `requestPermission(permission: String)` — request a specific permission
- `updatePermissionStates()` — update permission states
- `refreshPermissionStates()` — force-refresh permission states (on lifecycle changes)
- `openAppSettings()` — open the app's settings page

### PermissionStatus

```kotlin
enum class PermissionStatus {
    NotRequested,      // not requested yet
    Granted,           // granted
    Denied,            // denied but can be requested again
    DeniedPermanently  // denied with "don't ask again"
}
```

## Notes

1. **Activity requirement**: `rememberPermissionState` must be used inside a `ComponentActivity`
2. **Permission declaration**: declare the required permissions in `AndroidManifest.xml`
3. **Lifecycle aware**: permission state refreshes automatically when the app returns to the foreground
4. **Dialogs**: the rationale dialog is shown automatically when needed
5. **Settings shortcut**: permanently denied permissions offer a shortcut to app settings
6. **State sync**: changing a permission in Settings updates the state immediately on return

## Best practices

1. **Request on demand**: only request permissions when the feature is used
2. **Clear rationale**: explain clearly in `usage` why the permission is needed
3. **Classification**: use the `required` flag to separate required and optional permissions
4. **Graceful degradation**: provide a degraded experience when permissions are denied
5. **State retention**: permission state survives configuration changes
