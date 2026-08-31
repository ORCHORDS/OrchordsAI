package com.orchords.orchordsai.data.favorite

import com.orchords.orchordsai.data.db.entity.FavoriteEntity
import com.orchords.orchordsai.data.model.FavoriteType

interface FavoriteAdapter<T> {
    val type: FavoriteType

    fun buildRefKey(target: T): String

    fun buildFavoriteEntity(
        target: T,
        existing: FavoriteEntity? = null,
        now: Long = System.currentTimeMillis()
    ): FavoriteEntity
}
