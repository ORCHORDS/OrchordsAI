package com.orchords.orchordsai.data.extensions

import java.util.UUID

fun main() {
    var assertions = 0
    fun verify(value: Boolean, label: String) { assertions++; check(value) { label } }
    val catalog = BuiltInLibrary.catalog
    verify(catalog.version == 1, "version")
    verify(catalog.modes.size == 12, "Expected 12 modes, got ${catalog.modes.size}")
    verify(catalog.lorebooks.size == 8, "Expected 8 lorebooks")
    verify(catalog.lorebooks.sumOf { it.entries.size } == 24, "Expected 24 reference entries")
    verify(catalog.skills.size == 30, "Expected 30 skills")
    val definitions = catalog.modes.map { it.id to it.body } +
        catalog.lorebooks.flatMap { it.entries }.map { it.id to it.body }
    val ids = catalog.modes.map { it.id } + catalog.lorebooks.map { it.id } +
        catalog.lorebooks.flatMap { it.entries }.map { it.id }
    verify(ids.distinct().size == ids.size, "UUID collision")
    ids.forEach { verify(UUID.fromString(it).toString() == it, "Noncanonical UUID") }
    definitions.forEach { (_, body) ->
        verify(body.length in 180..8000, "Empty, filler or unbounded content")
        verify(!body.contains("TODO") && !body.contains("PLACEHOLDER"), "Unfinished body")
    }
    catalog.lorebooks.flatMap { it.entries }.forEach {
        verify(it.keywords.isNotEmpty() && it.keywords.all { word -> word.isNotBlank() }, "Empty trigger")
        verify(it.keywords.all { word -> word.length <= 80 }, "Oversized trigger")
    }
    verify(catalog.skills.map { it.name }.distinct().size == 30, "Skill collision")
    catalog.skills.forEach {
        verify(Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(it.name) && it.name.length <= 64, "Skill name")
        verify(it.description.length in 10..1024, "Skill description")
        verify(it.body.length in 600..16000, "Skill body is not substantive")
        verify(it.skillFile().startsWith("---\nname: ${it.name}\n"), "Frontmatter name")
        verify(it.body.contains("## Workflow") && it.body.contains("## Completion"), "Workflow sections")
    }
    val edited = catalog.modes.first().copy(body = "User's own edited instructions")
    val current = listOf(edited)
    val merged = appendMissingById(current, catalog.modes) { it.id }
    verify(merged.first() == edited, "Existing edits overwritten")
    verify(merged.size == 12, "Install count")
    verify(appendMissingById(merged, catalog.modes) { it.id } == merged, "Reinstall changed content")
    val custom = edited.copy(id = "12345678-1234-1234-1234-123456789abc")
    verify(appendMissingById(listOf(custom), catalog.modes) { it.id }.first() == custom, "Custom entry lost")
    verify(appendMissingById(current, emptyList<LibraryMode>()) { it.id } == current, "Empty install changed current")
    val duplicateCatalog = listOf(catalog.modes.first(), catalog.modes.first())
    verify(appendMissingById(emptyList(), duplicateCatalog) { it.id }.size == 1, "Duplicate catalog creates duplicate records")
    println("Library contract checks passed: $assertions assertions")
}
