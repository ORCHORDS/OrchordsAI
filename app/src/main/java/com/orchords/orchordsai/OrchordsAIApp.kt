package com.orchords.orchordsai

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.orchords.orchordsai.data.files.FileFolders
import java.io.File
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.orchords.common.android.appTempFolder
import com.whl.quickjs.android.QuickJSLoader
import com.orchords.orchordsai.di.appModule
import com.orchords.orchordsai.di.dataSourceModule
import com.orchords.orchordsai.di.repositoryModule
import com.orchords.orchordsai.di.viewModelModule
import com.orchords.orchordsai.data.files.FilesManager
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.service.WebServerService
import com.orchords.orchordsai.utils.CrashHandler
import com.orchords.orchordsai.utils.StartupSoundPlayer
import com.orchords.orchordsai.data.repository.WorkspaceRepository
import com.orchords.workspace.WorkspaceManager
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

private const val TAG = "OrchordsAIApp"

const val CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID = "chat_completed"
const val CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID = "chat_live_update"
const val WEB_SERVER_NOTIFICATION_CHANNEL_ID = "web_server"

internal fun webServerNetworkPermissionAllowsAutoStart(
    apiLevel: Int,
    localhostOnly: Boolean,
    localNetworkPermissionGranted: Boolean,
): Boolean = apiLevel < 37 || localhostOnly || localNetworkPermissionGranted

class OrchordsAIApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StartupSoundPlayer(this).playOnce(R.raw.loader_start)

        startKoin {
            androidLogger()
            androidContext(this@OrchordsAIApp)
            workManagerFactory()
            modules(appModule, viewModelModule, dataSourceModule, repositoryModule)
        }

        this.createNotificationChannel()

        // install crash handler
        CrashHandler.install(this)

        // Init QuickJS native library
        QuickJSLoader.init()

        // delete temp files
        deleteTempFiles()

        // cleanup stale tool output files
        cleanupToolOutputs()

        // cleanup workspace temp dirs (proot + rootfs /tmp)
        cleanupWorkspaceTempDirs()

        // check workspace integrity (mark workspaces with missing files as broken after backup restore)
        checkWorkspaceIntegrity()

        // sync upload files to DB
        syncManagedFiles()

        // Start WebServer if enabled in settings
        startWebServerIfEnabled()

        // Increment launch count
        incrementLaunchCount()
    }

    private fun incrementLaunchCount() {
        get<AppScope>().launch {
            runCatching {
                val store = get<SettingsStore>()
                val current = store.settingsFlowRaw.first()
                store.update(current.copy(launchCount = current.launchCount + 1))
                Log.i(TAG, "incrementLaunchCount: ${store.settingsFlowRaw.first().launchCount}")
            }.onFailure {
                Log.e(TAG, "incrementLaunchCount failed", it)
            }
        }
    }

    private fun cleanupWorkspaceTempDirs() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                get<WorkspaceManager>().cleanupAllTempDirs()
            }.onFailure {
                Log.e(TAG, "cleanupWorkspaceTempDirs failed", it)
            }
        }
    }

    private fun checkWorkspaceIntegrity() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                get<WorkspaceRepository>().checkIntegrity()
            }.onFailure {
                Log.e(TAG, "checkWorkspaceIntegrity failed", it)
            }
        }
    }

    private fun deleteTempFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            val dir = appTempFolder
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    private fun cleanupToolOutputs() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                val dir = File(filesDir, FileFolders.TOOL_OUTPUTS)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }
        }
    }

    private fun syncManagedFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                get<FilesManager>().syncFolder()
            }.onFailure {
                Log.e(TAG, "syncManagedFiles failed", it)
            }
        }
    }

    private fun startWebServerIfEnabled() {
        get<AppScope>().launch {
            runCatching {
                delay(500)
                val settings = get<SettingsStore>().settingsFlowRaw.first()
                if (settings.webServerEnabled) {
                    val localNetworkPermissionGranted = Build.VERSION.SDK_INT < 37 ||
                        ContextCompat.checkSelfPermission(
                            this@OrchordsAIApp,
                            android.Manifest.permission.ACCESS_LOCAL_NETWORK
                        ) == PackageManager.PERMISSION_GRANTED
                    if (!webServerNetworkPermissionAllowsAutoStart(
                            apiLevel = Build.VERSION.SDK_INT,
                            localhostOnly = settings.webServerLocalhostOnly,
                            localNetworkPermissionGranted = localNetworkPermissionGranted,
                        )
                    ) {
                        Log.w(TAG, "startWebServerIfEnabled: local network permission not granted, skipping")
                        return@launch
                    }
                    val intent = Intent(this@OrchordsAIApp, WebServerService::class.java).apply {
                        action = WebServerService.ACTION_START
                        putExtra(WebServerService.EXTRA_PORT, settings.webServerPort)
                        putExtra(WebServerService.EXTRA_LOCALHOST_ONLY, settings.webServerLocalhostOnly)
                    }
                    startForegroundService(intent)
                }
            }.onFailure {
                Log.e(TAG, "startWebServerIfEnabled failed", it)
            }
        }
    }

    private fun createNotificationChannel() {
        val notificationManager = NotificationManagerCompat.from(this)
        val chatCompletedChannel = NotificationChannelCompat
            .Builder(
                CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
            .setName(getString(R.string.notification_channel_chat_completed))
            .setVibrationEnabled(true)
            .build()
        notificationManager.createNotificationChannel(chatCompletedChannel)

        val chatLiveUpdateChannel = NotificationChannelCompat
            .Builder(
                CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
            .setName(getString(R.string.notification_channel_chat_live_update))
            .setVibrationEnabled(false)
            .build()
        notificationManager.createNotificationChannel(chatLiveUpdateChannel)

        val webServerChannel = NotificationChannelCompat
            .Builder(WEB_SERVER_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.notification_channel_web_server))
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(webServerChannel)
    }

    override fun onTerminate() {
        super.onTerminate()
        get<AppScope>().cancel()
        stopService(Intent(this, WebServerService::class.java))
    }
}

class AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob()
        + Dispatchers.Main
        + CoroutineName("AppScope")
        + CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "AppScope exception", e)
    }
)
