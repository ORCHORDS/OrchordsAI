package com.orchords.orchordsai.di

import android.content.Context
import com.orchords.orchordsai.data.db.MessageNodePayloadStore
import com.orchords.orchordsai.data.files.FileFolders
import com.orchords.orchordsai.data.files.FilesManager
import com.orchords.orchordsai.data.files.SkillManager
import com.orchords.orchordsai.data.repository.ConversationRepository
import com.orchords.orchordsai.data.repository.FavoriteRepository
import com.orchords.orchordsai.data.repository.FolderRepository
import com.orchords.orchordsai.data.repository.FilesRepository
import com.orchords.orchordsai.data.repository.GenMediaRepository
import com.orchords.orchordsai.data.repository.MemoryRepository
import com.orchords.orchordsai.data.repository.WorkspaceRepository
import com.orchords.workspace.ProotShellRunner
import com.orchords.workspace.RootfsInstaller
import com.orchords.workspace.WorkspaceBindMount
import com.orchords.workspace.WorkspaceManager
import org.koin.dsl.module
import java.io.File

val repositoryModule = module {
    single {
        MessageNodePayloadStore(filesManager = get(), managedFileDao = get())
    }

    single {
        ConversationRepository(get(), get(), get(), get(), get(), get(), get())
    }

    single {
        FolderRepository(get(), get())
    }

    single {
        MemoryRepository(get())
    }

    single {
        GenMediaRepository(get())
    }

    single {
        FilesRepository(get())
    }

    single {
        FavoriteRepository(get())
    }

    single {
        val context: Context = get()
        WorkspaceManager(
            baseDir = File(context.filesDir, "workspaces"),
            shellRunner = ProotShellRunner(
                nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
            ),
            bindMounts = listOf(
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() },
                    target = "/skills",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() },
                    target = "/tool_outputs",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.UPLOAD).apply { mkdirs() },
                    target = "/upload",
                ),
            ),
        )
    }

    single {
        RootfsInstaller(get())
    }

    single {
        WorkspaceRepository(get(), get(), get(), get())
    }

    single {
        FilesManager(get(), get(), get())
    }

    single {
        SkillManager(get(), get())
    }
}
