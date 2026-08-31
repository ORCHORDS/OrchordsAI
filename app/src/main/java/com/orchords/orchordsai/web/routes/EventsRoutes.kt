package com.orchords.orchordsai.web.routes

import io.ktor.server.routing.Route
import io.ktor.server.sse.heartbeat
import io.ktor.server.sse.sse
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.repository.ConversationRepository
import com.orchords.orchordsai.data.repository.FolderRepository
import com.orchords.orchordsai.service.ChatService
import com.orchords.orchordsai.utils.JsonInstant
import com.orchords.orchordsai.web.dto.ConversationListInvalidateEvent
import com.orchords.orchordsai.web.dto.FolderListEvent
import com.orchords.orchordsai.web.dto.toDto
import kotlin.time.Duration.Companion.seconds

/**
 * Multiplexed server-sent events stream.
 *
 * A single `/api/events` connection carries several event types, distinguished by the
 * SSE `event:` field, so new event kinds can be added without opening a new connection:
 *  - `settings`                     -> full Settings snapshot
 *  - `conversation_list_invalidate` -> the conversation list for an assistant changed
 *  - `folders`                      -> the folder list for an assistant changed
 *
 * Per-conversation streaming (generation updates) keeps its own dedicated connection
 * at `/api/conversations/{id}/stream`.
 */
fun Route.eventsRoutes(
    chatService: ChatService,
    conversationRepo: ConversationRepository,
    folderRepo: FolderRepository,
    settingsStore: SettingsStore,
) {
    sse("/events") {
        heartbeat {
            period = 15.seconds
        }

        // Full settings snapshot; StateFlow emits the current value immediately on connect.
        val settingsEvents = settingsStore.settingsFlow.map { settings ->
            EventPayload(event = "settings", json = JsonInstant.encodeToString(settings))
        }

        // Conversation list invalidation, scoped to the currently selected assistant.
        val conversationListEvents = settingsStore.settingsFlow
            .map { it.assistantId }
            .distinctUntilChanged()
            .flatMapLatest { assistantId ->
                combine(
                    conversationRepo.getConversationsOfAssistant(assistantId),
                    chatService.getConversationJobs()
                ) { conversations, generationJobs ->
                    // Key per conversation folds in generation state (so start/stop invalidates the
                    // sidebar even when content isn't persisted) and folderId (so moving a
                    // conversation between folders invalidates other clients' folder views).
                    conversations.map { conversation ->
                        buildString {
                            append(conversation.id)
                            append('|')
                            append(conversation.updateAt.toEpochMilli())
                            append('|')
                            append(generationJobs[conversation.id] != null)
                            append('|')
                            append(conversation.folderId?.toString().orEmpty())
                        }
                    }
                }
                    .distinctUntilChanged()
                    .map { assistantId }
            }
            .map { assistantId ->
                EventPayload(
                    event = "conversation_list_invalidate",
                    json = JsonInstant.encodeToString(
                        ConversationListInvalidateEvent(
                            assistantId = assistantId.toString(),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                )
            }

        // Folder list for the currently selected assistant (Room flow emits on any change).
        val folderEvents = settingsStore.settingsFlow
            .map { it.assistantId }
            .distinctUntilChanged()
            .flatMapLatest { assistantId ->
                folderRepo.getFoldersOfAssistant(assistantId).map { folders ->
                    EventPayload(
                        event = "folders",
                        json = JsonInstant.encodeToString(
                            FolderListEvent(
                                assistantId = assistantId.toString(),
                                folders = folders.map { it.toDto() }
                            )
                        )
                    )
                }
            }

        merge(settingsEvents, conversationListEvents, folderEvents).collect { payload ->
            send(data = payload.json, event = payload.event)
        }
    }
}

private data class EventPayload(
    val event: String,
    val json: String,
)
