package com.orchords.orchordsai.di

import kotlinx.serialization.json.Json
import com.orchords.orchordsai.AppScope
import com.orchords.orchordsai.data.ai.tools.local.LocalTools
import com.orchords.orchordsai.data.event.AppEventBus
import com.orchords.orchordsai.service.ChatNotificationManager
import com.orchords.orchordsai.service.ChatService
import com.orchords.orchordsai.ui.pages.extensions.workspace.WorkspaceTerminalSessionManager
import com.orchords.orchordsai.utils.EmojiData
import com.orchords.orchordsai.utils.EmojiUtils
import com.orchords.orchordsai.utils.JsonInstant
import com.orchords.orchordsai.utils.SoundEffectPlayer
import com.orchords.orchordsai.utils.UpdateChecker
import com.orchords.orchordsai.web.WebServerManager
import com.orchords.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single {
        AppEventBus()
    }

    single {
        LocalTools(get(), get(), get(), get())
    }

    single {
        UpdateChecker(
            client = get(),
            appScope = get(),
        )
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single {
        SoundEffectPlayer(get())
    }

    single {
        WorkspaceTerminalSessionManager(get(), get())
    }

    single(createdAtStart = true) {
        ChatNotificationManager(
            context = get(),
            appScope = get(),
            eventBus = get(),
            settingsStore = get(),
        )
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationHandler = get(),
            translationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            localTools = get(),
            mcpManager = get(),
            filesManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
            folderRepository = get()
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }
}
