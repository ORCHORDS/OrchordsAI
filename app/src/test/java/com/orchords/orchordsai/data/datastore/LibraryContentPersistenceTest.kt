package com.orchords.orchordsai.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orchords.orchordsai.data.extensions.BuiltInLibrary
import com.orchords.orchordsai.data.extensions.toLorebook
import com.orchords.orchordsai.data.extensions.toModeInjection
import com.orchords.orchordsai.data.model.PromptInjection
import com.orchords.orchordsai.utils.JsonInstant
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryContentPersistenceTest {
    @Test
    fun `concurrent library installs preserve edits and unrelated preferences`() = runBlocking {
        val root = Files.createTempDirectory("library-content-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            root.resolve("settings.preferences_pb")
        }
        try {
            val modes = BuiltInLibrary.catalog.modes.map { it.toModeInjection() }
            val books = BuiltInLibrary.catalog.lorebooks.map { it.toLorebook() }
            val edited = modes.first().copy(content = "User-authored content must survive")
            val sentinel = stringPreferencesKey("unrelated-test-preference")
            store.edit {
                it[SettingsStore.MODE_INJECTIONS] = JsonInstant.encodeToString(listOf(edited))
                it[sentinel] = "unchanged"
                it[SettingsStore.ASSISTANTS] = "user-selection-sentinel"
            }
            val receipts = (1..8).map {
                async { appendLibraryContent(store, modes, books) }
            }.awaitAll()
            assertEquals(modes.size - 1, receipts.sumOf { it.addedModes })
            assertEquals(books.size, receipts.sumOf { it.addedLorebooks })
            val result = store.data.first()
            assertEquals("unchanged", result[sentinel])
            assertEquals("user-selection-sentinel", result[SettingsStore.ASSISTANTS])
            val persisted = JsonInstant.decodeFromString<List<PromptInjection.ModeInjection>>(
                requireNotNull(result[SettingsStore.MODE_INJECTIONS])
            )
            assertEquals(modes.size, persisted.size)
            assertEquals(edited, persisted.first())
            assertEquals(0, appendLibraryContent(store, modes, books).addedModes)
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            scope.coroutineContext[kotlinx.coroutines.Job]?.join()
            root.deleteRecursively()
        }
    }

    @Test
    fun `malformed existing content is not replaced by an empty library`() = runBlocking {
        val root = Files.createTempDirectory("library-corruption-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            root.resolve("settings.preferences_pb")
        }
        try {
            store.edit { it[SettingsStore.MODE_INJECTIONS] = "not valid JSON" }
            var rejected = false
            try {
                appendLibraryContent(store, BuiltInLibrary.catalog.modes.map { it.toModeInjection() }, emptyList())
            } catch (_: kotlinx.serialization.SerializationException) {
                rejected = true
            }
            assertTrue(rejected)
            assertEquals("not valid JSON", store.data.first()[SettingsStore.MODE_INJECTIONS])
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            scope.coroutineContext[kotlinx.coroutines.Job]?.join()
            root.deleteRecursively()
        }
    }
}
