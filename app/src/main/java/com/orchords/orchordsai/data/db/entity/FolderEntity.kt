package com.orchords.orchordsai.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 *
 */
@Entity(
    tableName = "conversation_folder",
    indices = [Index(value = ["assistant_id"])]
)
data class FolderEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("sort_index", defaultValue = "0")
    val sortIndex: Int = 0,
    @ColumnInfo("create_at")
    val createAt: Long,
)
