package com.orchords.orchordsai.di

import com.orchords.orchordsai.ui.pages.assistant.AssistantVM
import com.orchords.orchordsai.ui.pages.assistant.detail.AssistantDetailVM
import com.orchords.orchordsai.ui.pages.backup.BackupVM
import com.orchords.orchordsai.ui.pages.chat.ChatDrawerVM
import com.orchords.orchordsai.ui.pages.chat.ChatVM
import com.orchords.orchordsai.ui.pages.debug.DebugVM
import com.orchords.orchordsai.ui.pages.favorite.FavoriteVM
import com.orchords.orchordsai.ui.pages.search.SearchVM
import com.orchords.orchordsai.ui.pages.history.HistoryVM
import com.orchords.orchordsai.ui.pages.stats.StatsVM
import com.orchords.orchordsai.ui.pages.imggen.ImgGenVM
import com.orchords.orchordsai.ui.pages.extensions.PromptVM
import com.orchords.orchordsai.ui.pages.extensions.QuickMessagesVM
import com.orchords.orchordsai.ui.pages.extensions.skills.SkillDetailVM
import com.orchords.orchordsai.ui.pages.extensions.skills.SkillsVM
import com.orchords.orchordsai.ui.pages.extensions.workspace.WorkspaceDetailVM
import com.orchords.orchordsai.ui.pages.extensions.workspace.WorkspaceVM
import com.orchords.orchordsai.ui.pages.setting.SettingVM
import com.orchords.orchordsai.ui.pages.share.handler.ShareHandlerVM
import com.orchords.orchordsai.ui.pages.translator.TranslatorVM
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<ChatVM> { params ->
        ChatVM(
            id = params.get(),
            context = get(),
            settingsStore = get(),
            conversationRepo = get(),
            chatService = get(),
            updateChecker = get(),
            filesManager = get(),
            favoriteRepository = get(),
        )
    }
    viewModelOf(::ChatDrawerVM)
    viewModelOf(::SettingVM)
    viewModelOf(::DebugVM)
    viewModelOf(::HistoryVM)
    viewModelOf(::AssistantVM)
    viewModel<AssistantDetailVM> {
        AssistantDetailVM(
            id = it.get(),
            settingsStore = get(),
            memoryRepository = get(),
            filesManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
        )
    }
    viewModelOf(::TranslatorVM)
    viewModel<ShareHandlerVM> {
        ShareHandlerVM(
            text = it.get(),
            settingsStore = get(),
        )
    }
    viewModelOf(::BackupVM)
    viewModelOf(::ImgGenVM)
    viewModelOf(::PromptVM)
    viewModelOf(::QuickMessagesVM)
    viewModelOf(::SkillsVM)
    viewModelOf(::SkillDetailVM)
    viewModelOf(::WorkspaceVM)
    viewModel<WorkspaceDetailVM> {
        WorkspaceDetailVM(
            id = it.get(),
            repository = get(),
            terminalSessionManager = get(),
        )
    }
    viewModelOf(::FavoriteVM)
    viewModelOf(::SearchVM)
    viewModelOf(::StatsVM)
}
