package com.orchords.orchordsai.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.orchords.ai.ui.UIMessage
import com.orchords.orchordsai.data.db.AppDatabase
import com.orchords.orchordsai.data.db.fts.MessageFtsManager
import com.orchords.orchordsai.data.db.fts.MessageSearchSort
import com.orchords.orchordsai.data.db.dao.ConversationDAO
import com.orchords.orchordsai.data.db.dao.FavoriteDAO
import com.orchords.orchordsai.data.db.dao.MessageNodeDAO
import com.orchords.orchordsai.data.db.entity.ConversationEntity
import com.orchords.orchordsai.data.db.entity.MessageNodeEntity
import com.orchords.orchordsai.data.files.FilesManager
import com.orchords.orchordsai.data.model.Conversation
import com.orchords.orchordsai.data.model.ConversationLoadState
import com.orchords.orchordsai.data.model.MessageNode
import com.orchords.orchordsai.utils.JsonInstant
import java.time.Instant
import kotlin.uuid.Uuid

class ConversationRepository(
    private val conversationDAO: ConversationDAO,
    private val messageNodeDAO: MessageNodeDAO,
    private val favoriteDAO: FavoriteDAO,
    private val database: AppDatabase,
    private val filesManager: FilesManager,
    private val messageFtsManager: MessageFtsManager,
) {
    companion object {
        private const val PAGE_SIZE = 20
        private const val INITIAL_LOAD_SIZE = 40
    }

    suspend fun getRecentConversations(assistantId: Uuid, limit: Int = 10): List<Conversation> =
        conversationDAO.getRecentConversationsOfAssistant(assistantId.toString(), limit).map { entity ->
            val loaded = loadMessageNodes(entity.id)
            conversationEntityToConversation(entity, loaded.nodes, loaded.loadState, loaded.corruptNodeIds)
        }

    fun getConversationsOfAssistant(assistantId: Uuid): Flow<List<Conversation>> =
        conversationDAO.getConversationsOfAssistant(assistantId.toString()).map { list ->
            list.map { conversationEntityToConversation(it, emptyList()) }
        }

    fun getConversationsOfAssistantPaging(assistantId: Uuid): Flow<PagingData<Conversation>> = Pager(
        PagingConfig(PAGE_SIZE, initialLoadSize = INITIAL_LOAD_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { conversationDAO.getConversationsOfAssistantPaging(assistantId.toString()) }
    ).flow.map { data -> data.map(::conversationSummaryToConversation) }

    fun getUnfiledConversationsOfAssistantPaging(assistantId: Uuid): Flow<PagingData<Conversation>> = Pager(
        PagingConfig(PAGE_SIZE, initialLoadSize = INITIAL_LOAD_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { conversationDAO.getUnfiledConversationsOfAssistantPaging(assistantId.toString()) }
    ).flow.map { data -> data.map(::conversationSummaryToConversation) }

    fun getConversationsOfFolderPaging(folderId: Uuid): Flow<PagingData<Conversation>> = Pager(
        PagingConfig(PAGE_SIZE, initialLoadSize = INITIAL_LOAD_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { conversationDAO.getConversationsOfFolderPaging(folderId.toString()) }
    ).flow.map { data -> data.map(::conversationSummaryToConversation) }

    suspend fun getConversationsOfAssistantPage(assistantId: Uuid, offset: Int, limit: Int): ConversationPageResult =
        loadConversationPage(conversationDAO.getConversationsOfAssistantPaging(assistantId.toString()), offset, limit)

    suspend fun searchConversationsOfAssistantPage(
        assistantId: Uuid,
        titleKeyword: String,
        offset: Int,
        limit: Int,
    ): ConversationPageResult = loadConversationPage(
        conversationDAO.searchConversationsOfAssistantPaging(assistantId.toString(), titleKeyword), offset, limit
    )

    suspend fun getUnfiledConversationsOfAssistantPage(
        assistantId: Uuid,
        offset: Int,
        limit: Int,
    ): ConversationPageResult = loadConversationPage(
        conversationDAO.getUnfiledConversationsOfAssistantPaging(assistantId.toString()), offset, limit
    )

    suspend fun getConversationsOfFolderPage(folderId: Uuid, offset: Int, limit: Int): ConversationPageResult =
        loadConversationPage(conversationDAO.getConversationsOfFolderPaging(folderId.toString()), offset, limit)

    private suspend fun loadConversationPage(
        pagingSource: PagingSource<Int, LightConversationEntity>,
        offset: Int,
        limit: Int,
    ): ConversationPageResult = try {
        when (val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = if (offset == 0) null else offset,
                loadSize = limit,
                placeholdersEnabled = false
            )
        )) {
            is PagingSource.LoadResult.Page -> ConversationPageResult(
                result.data.map(::conversationSummaryToConversation), result.nextKey
            )
            is PagingSource.LoadResult.Error -> throw result.throwable
            is PagingSource.LoadResult.Invalid -> ConversationPageResult(emptyList(), null)
        }
    } finally {
        pagingSource.invalidate()
    }

    fun searchConversations(titleKeyword: String): Flow<List<Conversation>> =
        conversationDAO.searchConversations(titleKeyword).map { list ->
            list.map { conversationEntityToConversation(it, emptyList()) }
        }

    fun searchConversationsPaging(titleKeyword: String): Flow<PagingData<Conversation>> = Pager(
        PagingConfig(PAGE_SIZE, initialLoadSize = INITIAL_LOAD_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { conversationDAO.searchConversationsPaging(titleKeyword) }
    ).flow.map { data -> data.map(::conversationSummaryToConversation) }

    fun searchConversationsOfAssistant(assistantId: Uuid, titleKeyword: String): Flow<List<Conversation>> =
        conversationDAO.searchConversationsOfAssistant(assistantId.toString(), titleKeyword).map { list ->
            list.map { conversationEntityToConversation(it, emptyList()) }
        }

    fun searchConversationsOfAssistantPaging(assistantId: Uuid, titleKeyword: String): Flow<PagingData<Conversation>> =
        Pager(
            PagingConfig(PAGE_SIZE, initialLoadSize = INITIAL_LOAD_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                conversationDAO.searchConversationsOfAssistantPaging(assistantId.toString(), titleKeyword)
            }
        ).flow.map { data -> data.map(::conversationSummaryToConversation) }

    suspend fun getConversationById(uuid: Uuid): Conversation? {
        val entity = conversationDAO.getConversationById(uuid.toString()) ?: return null
        val loaded = loadMessageNodes(entity.id)
        return conversationEntityToConversation(entity, loaded.nodes, loaded.loadState, loaded.corruptNodeIds)
    }

    suspend fun existsConversationById(uuid: Uuid): Boolean = conversationDAO.existsById(uuid.toString())
    suspend fun countConversations(): Int = conversationDAO.countAll()

    suspend fun insertConversation(conversation: Conversation) {
        requireCompleteConversationForRewrite(conversation)
        database.withTransaction {
            conversationDAO.insert(conversationToConversationEntity(conversation))
            saveMessageNodes(conversation.id.toString(), conversation.messageNodes)
        }
        messageFtsManager.indexConversation(conversation)
    }

    suspend fun updateConversation(conversation: Conversation) {
        requireCompleteConversationForRewrite(conversation)
        database.withTransaction {
            conversationDAO.update(conversationToConversationEntity(conversation))
            messageNodeDAO.deleteByConversation(conversation.id.toString())
            saveMessageNodes(conversation.id.toString(), conversation.messageNodes)
        }
        messageFtsManager.indexConversation(conversation)
    }

    suspend fun deleteConversation(conversation: Conversation) {
        val fullConversation = if (conversation.messageNodes.isEmpty()) {
            getConversationById(conversation.id) ?: conversation
        } else conversation
        messageFtsManager.deleteConversation(conversation.id.toString())
        database.withTransaction { conversationDAO.delete(conversationToConversationEntity(conversation)) }
        filesManager.deleteChatFiles(fullConversation.files)
    }

    suspend fun searchMessages(
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
    ) = messageFtsManager.search(keyword, sort)

    suspend fun searchMessagesOfAssistant(
        assistantId: Uuid,
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
    ) = messageFtsManager.searchForAssistant(keyword, assistantId.toString(), sort)

    suspend fun rebuildAllIndexes(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }) {
        messageFtsManager.deleteAll()
        val allIds = conversationDAO.getAllIds()
        val total = allIds.size
        allIds.forEachIndexed { index, id ->
            val entity = conversationDAO.getConversationById(id) ?: return@forEachIndexed
            val loaded = loadMessageNodes(entity.id)
            if (loaded.corruptNodeIds.isEmpty()) {
                messageFtsManager.indexConversation(
                    conversationEntityToConversation(entity, loaded.nodes, loaded.loadState, loaded.corruptNodeIds)
                )
            }
            onProgress(index + 1, total)
        }
    }

    suspend fun deleteConversationOfAssistant(assistantId: Uuid) {
        getConversationsOfAssistant(assistantId).first().forEach { deleteConversation(it) }
    }

    fun conversationToConversationEntity(conversation: Conversation): ConversationEntity {
        require(conversation.messageNodes.none { node -> node.messages.any { it.hasBase64Part() } })
        return ConversationEntity(
            id = conversation.id.toString(),
            title = conversation.title,
            nodes = "[]",
            createAt = conversation.createAt.toEpochMilli(),
            updateAt = conversation.updateAt.toEpochMilli(),
            assistantId = conversation.assistantId.toString(),
            chatSuggestions = JsonInstant.encodeToString(conversation.chatSuggestions),
            isPinned = conversation.isPinned,
            customSystemPrompt = conversation.customSystemPrompt ?: "",
            modeInjectionIds = JsonInstant.encodeToString(conversation.modeInjectionIds),
            lorebookIds = JsonInstant.encodeToString(conversation.lorebookIds),
            workspaceCwd = conversation.workspaceCwd ?: "",
            folderId = conversation.folderId?.toString() ?: "",
        )
    }

    fun conversationEntityToConversation(
        entity: ConversationEntity,
        messageNodes: List<MessageNode>,
        loadState: ConversationLoadState = ConversationLoadState.COMPLETE,
        corruptNodeIds: Set<String> = emptySet(),
    ): Conversation = Conversation(
        id = Uuid.parse(entity.id),
        title = entity.title,
        messageNodes = messageNodes.filter { it.messages.isNotEmpty() },
        createAt = Instant.ofEpochMilli(entity.createAt),
        updateAt = Instant.ofEpochMilli(entity.updateAt),
        assistantId = Uuid.parse(entity.assistantId),
        chatSuggestions = JsonInstant.decodeFromString(entity.chatSuggestions),
        isPinned = entity.isPinned,
        customSystemPrompt = entity.customSystemPrompt.ifEmpty { null },
        modeInjectionIds = JsonInstant.decodeFromString(entity.modeInjectionIds),
        lorebookIds = JsonInstant.decodeFromString(entity.lorebookIds),
        workspaceCwd = entity.workspaceCwd.ifEmpty { null },
        folderId = entity.folderId.ifEmpty { null }?.let(Uuid::parse),
        loadState = loadState,
        corruptNodeIds = corruptNodeIds,
    )

    fun getPinnedConversations(): Flow<List<Conversation>> = conversationDAO.getPinnedConversations().map { list ->
        list.map { conversationEntityToConversation(it, emptyList()) }
    }

    suspend fun togglePinStatus(conversationId: Uuid) {
        conversationDAO.updatePinStatus(
            conversationId.toString(), !(getConversationById(conversationId)?.isPinned ?: false)
        )
    }

    suspend fun updateConversationFolderId(conversationId: Uuid, folderId: Uuid?) {
        conversationDAO.updateFolderId(conversationId.toString(), folderId?.toString() ?: "")
    }

    private fun conversationSummaryToConversation(entity: LightConversationEntity): Conversation = Conversation(
        id = Uuid.parse(entity.id),
        assistantId = Uuid.parse(entity.assistantId),
        title = entity.title,
        isPinned = entity.isPinned,
        createAt = Instant.ofEpochMilli(entity.createAt),
        updateAt = Instant.ofEpochMilli(entity.updateAt),
        messageNodes = emptyList(),
        folderId = entity.folderId.ifEmpty { null }?.let(Uuid::parse),
    )

    private suspend fun loadMessageNodes(conversationId: String): MessageNodeLoadResult {
        val favoriteNodeIds = favoriteDAO.getFavoriteNodeIdsOfConversation(conversationId)
            .mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
            .toSet()
        return database.withTransaction {
            loadConversationNodesSafely(messageNodeDAO, conversationId, favoriteNodeIds)
        }
    }

    private suspend fun saveMessageNodes(conversationId: String, nodes: List<MessageNode>) {
        messageNodeDAO.insertAll(nodes.mapIndexed { index, node ->
            MessageNodeEntity(
                id = node.id.toString(),
                conversationId = conversationId,
                nodeIndex = index,
                messages = JsonInstant.encodeToString(node.messages),
                selectIndex = node.selectIndex
            )
        })
    }
}

data class LightConversationEntity(
    val id: String,
    val assistantId: String,
    val title: String,
    val isPinned: Boolean,
    val createAt: Long,
    val updateAt: Long,
    val folderId: String = "",
)

data class ConversationPageResult(
    val items: List<Conversation>,
    val nextOffset: Int?,
)
