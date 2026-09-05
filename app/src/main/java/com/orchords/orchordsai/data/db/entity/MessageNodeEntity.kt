package com.orchords.orchordsai.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_node",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("conversation_id"),
        Index("payload_blob_id"),
    ]
)
data class MessageNodeEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("node_index")
    val nodeIndex: Int,
    @ColumnInfo("messages")
    val messages: String,  // JSON serialized List<UIMessage>; empty when payloadBlobId != null
    @ColumnInfo("select_index")
    val selectIndex: Int,
    /**
     * Foreign key to [ManagedFileEntity.id] when the JSON payload is externalized to
     * `filesDir/managed/payloads/`. `null` means the [messages] column holds the JSON inline.
     * Lookup goes through the [MessageNodePayloadStore] / [MessageNodePayloadResolver].
     */
    @ColumnInfo("payload_blob_id")
    val payloadBlobId: Long? = null,
)
