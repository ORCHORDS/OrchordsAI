package com.orchords.orchordsai.data.extensions

import com.orchords.ai.core.MessageRole
import com.orchords.orchordsai.data.datastore.Settings
import com.orchords.orchordsai.data.model.InjectionPosition
import com.orchords.orchordsai.data.model.Lorebook
import com.orchords.orchordsai.data.model.PromptInjection
import kotlin.uuid.Uuid

/** Native definitions for the existing editors and transformer; no second prompt model/store. */
fun LibraryMode.toModeInjection(): PromptInjection.ModeInjection = PromptInjection.ModeInjection(
    id = Uuid.parse(id),
    name = name,
    content = body,
    role = MessageRole.USER,
    position = InjectionPosition.BOTTOM_OF_CHAT,
)

fun LibraryLorebook.toLorebook(): Lorebook = Lorebook(
    id = Uuid.parse(id),
    name = name,
    description = description,
    entries = entries.map { entry ->
        PromptInjection.RegexInjection(
            id = Uuid.parse(entry.id),
            name = entry.name,
            content = entry.body,
            keywords = entry.keywords,
            role = MessageRole.USER,
            position = InjectionPosition.BOTTOM_OF_CHAT,
            scanDepth = 4,
            useRegex = false,
            constantActive = false,
        )
    },
)

/**
 * Pure candidate transformation, NOT persistence or an install receipt. Apply through the
 * canonical SettingsStore transaction once its narrow merge/commit boundary is available.
 * Existing content and all assistant selections/credentials remain unchanged.
 */
fun Settings.withMissingBuiltInLibraryContent(): Settings {
    check(!init) { "Settings have not finished loading" }
    return copy(
        modeInjections = appendMissingById(modeInjections, BuiltInLibrary.catalog.modes.map { it.toModeInjection() }) { it.id.toString() },
        lorebooks = appendMissingById(lorebooks, BuiltInLibrary.catalog.lorebooks.map { it.toLorebook() }) { it.id.toString() },
    )
}
