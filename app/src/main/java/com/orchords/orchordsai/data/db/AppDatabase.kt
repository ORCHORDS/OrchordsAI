package com.orchords.orchordsai.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.orchords.ai.core.TokenUsage
import com.orchords.orchordsai.data.db.dao.ConversationDAO
import com.orchords.orchordsai.data.db.dao.FavoriteDAO
import com.orchords.orchordsai.data.db.dao.FolderDAO
import com.orchords.orchordsai.data.db.dao.GenMediaDAO
import com.orchords.orchordsai.data.db.dao.ManagedFileDAO
import com.orchords.orchordsai.data.db.dao.MemoryDAO
import com.orchords.orchordsai.data.db.dao.MessageNodeDAO
import com.orchords.orchordsai.data.db.dao.WorkspaceDAO
import com.orchords.orchordsai.data.db.entity.ConversationEntity
import com.orchords.orchordsai.data.db.entity.FavoriteEntity
import com.orchords.orchordsai.data.db.entity.FolderEntity
import com.orchords.orchordsai.data.db.entity.GenMediaEntity
import com.orchords.orchordsai.data.db.entity.ManagedFileEntity
import com.orchords.orchordsai.data.db.entity.MemoryEntity
import com.orchords.orchordsai.data.db.entity.MessageNodeEntity
import com.orchords.orchordsai.data.db.entity.WorkspaceEntity
import com.orchords.orchordsai.data.db.migrations.Migration_16_17
import com.orchords.orchordsai.data.db.migrations.Migration_22_23
import com.orchords.orchordsai.data.db.migrations.Migration_8_9
import com.orchords.orchordsai.utils.JsonInstant

const val APP_DATABASE_SCHEMA_VERSION = 25

@Database(
    entities = [
        ConversationEntity::class,
        MemoryEntity::class,
        GenMediaEntity::class,
        MessageNodeEntity::class,
        ManagedFileEntity::class,
        FavoriteEntity::class,
        WorkspaceEntity::class,
        FolderEntity::class,
    ],
    version = APP_DATABASE_SCHEMA_VERSION,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9, spec = Migration_8_9::class),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 16, to = 17, spec = Migration_16_17::class),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
        AutoMigration(from = 22, to = 23, spec = Migration_22_23::class),
        AutoMigration(from = 23, to = 24),
    ]
)
@TypeConverters(TokenUsageConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDAO

    abstract fun memoryDao(): MemoryDAO

    abstract fun genMediaDao(): GenMediaDAO

    abstract fun messageNodeDao(): MessageNodeDAO

    abstract fun managedFileDao(): ManagedFileDAO

    abstract fun favoriteDao(): FavoriteDAO

    abstract fun workspaceDao(): WorkspaceDAO

    abstract fun folderDao(): FolderDAO
}

object TokenUsageConverter {
    @TypeConverter
    fun fromTokenUsage(usage: TokenUsage?): String {
        return JsonInstant.encodeToString(usage)
    }

    @TypeConverter
    fun toTokenUsage(usage: String): TokenUsage? {
        return JsonInstant.decodeFromString(usage)
    }
}
