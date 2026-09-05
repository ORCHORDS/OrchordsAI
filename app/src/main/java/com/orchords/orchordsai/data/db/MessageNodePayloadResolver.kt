package com.orchords.orchordsai.data.db

/**
 * Pure decision helper used to determine whether a [com.orchords.orchordsai.data.db.entity.MessageNodeEntity]
 * should keep its JSON inline in the `messages` column or externalize it via
 * [MessageNodePayloadStore].
 *
 * Kept as a separate object so unit tests can verify the threshold logic
 * without standing up a filesystem or Room.
 */
internal object MessageNodePayloadResolver {

    /** True when [json] exceeds [MessageNodePayloadStore.MAX_INLINE_BYTES] UTF-8 bytes. */
    fun shouldExternalize(json: String): Boolean =
        json.toByteArray(Charsets.UTF_8).size > MessageNodePayloadStore.MAX_INLINE_BYTES
}
