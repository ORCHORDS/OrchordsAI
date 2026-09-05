package com.orchords.orchordsai.data.extensions

/** Versioned first-party definitions, deliberately independent of Android and network clients. */
data class LibraryMode(val id: String, val name: String, val body: String)
data class LibraryEntry(val id: String, val name: String, val keywords: List<String>, val body: String)
data class LibraryLorebook(val id: String, val name: String, val description: String, val entries: List<LibraryEntry>)
data class LibrarySkill(val name: String, val description: String, val body: String) {
    fun skillFile(): String {
        require(Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(name) && name.length <= 64)
        require(description.isNotBlank() && description.length <= 1024)
        val quotedDescription = description.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r")
        return "---\nname: $name\ndescription: \"$quotedDescription\"\n---\n\n$body\n"
    }
}
data class LibraryCatalog(
    val version: Int,
    val modes: List<LibraryMode>,
    val lorebooks: List<LibraryLorebook>,
    val skills: List<LibrarySkill>,
)
object BuiltInLibrary {
    val catalog: LibraryCatalog = builtInLibraryCatalog()
}

/** Append missing identities only; existing ordering, duplicates and user edits are untouched. */
fun <T> appendMissingById(current: List<T>, builtIns: List<T>, id: (T) -> String): List<T> {
    val seen = current.mapTo(HashSet()) { id(it) }
    return current + builtIns.filter { seen.add(id(it)) }
}
