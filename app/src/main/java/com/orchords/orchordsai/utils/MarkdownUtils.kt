package com.orchords.orchordsai.utils

/**
 */
fun String.stripMarkdown(): String {
    return this
        .replace(Regex("```[\\s\\S]*?```|`[^`]*?`"), "")
        .replace(Regex("!?\\[([^\\]]+)\\]\\([^\\)]*\\)"), "$1")
        .replace(Regex("\\*\\*([^*]+?)\\*\\*"), "$1")
        .replace(Regex("\\*([^*]+?)\\*"), "$1")
        .replace(Regex("__([^_]+?)__"), "$1")
        .replace(Regex("_([^_]+?)_"), "$1")
        .replace(Regex("~~([^~]+?)~~"), "$1")
        .replace(Regex("(?m)^#+\\s*"), "")
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        .replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
        .replace(Regex("(?m)^>\\s*"), "")
        .replace(Regex("(?m)^(\\s*[-*_]){3,}\\s*$"), "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

fun String.extractThinkingTitle(): String? {
    val lines = this.lines()

    for (i in lines.indices.reversed()) {
        val line = lines[i].trim()

        val boldPattern = Regex("^\\*\\*(.+?)\\*\\*$")
        val match = boldPattern.find(line)

        if (match != null) {
            return match.groupValues[1].trim().takeUnless { it.isBlank() }
        }
    }

    return null
}
