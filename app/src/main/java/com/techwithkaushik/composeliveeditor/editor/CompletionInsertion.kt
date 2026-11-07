package com.techwithkaushik.composeliveeditor.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Inserts a completion at the current selection while keeping adjacent source
 * lines separated from the inserted snippet.
 */
object CompletionInsertion {
    fun insert(value: TextFieldValue, snippet: String): TextFieldValue {
        if (snippet.isEmpty()) return value

        val start = minOf(value.selection.start, value.selection.end)
        val end = maxOf(value.selection.start, value.selection.end)
        val prefix = if (start > 0 && value.text[start - 1] != '\n') "\n" else ""
        val suffix = if (end < value.text.length && value.text[end] != '\n') "\n" else ""
        val updatedText = value.text.replaceRange(start, end, prefix + snippet + suffix)
        val cursor = start + prefix.length + snippet.length

        return TextFieldValue(updatedText, selection = TextRange(cursor))
    }

    fun complete(value: TextFieldValue, snippet: String, prefixLength: Int): TextFieldValue {
        require(prefixLength >= 0) { "prefixLength must not be negative" }
        require(value.selection.collapsed) { "Completion requires a collapsed selection" }
        require(prefixLength <= value.selection.start) { "prefixLength exceeds text before the cursor" }

        val cursor = value.selection.start
        val replacement = value.copy(selection = TextRange(cursor - prefixLength, cursor))
        return insert(replacement, snippet)
    }
}

/**
 * Finds the identifier fragment immediately before the caret. Suggestions are
 * suppressed for selected text and when the caret is not after an identifier.
 */
object CompletionContext {
    fun prefixAt(value: TextFieldValue): String {
        if (!value.selection.collapsed) return ""

        val cursor = value.selection.start
        if (cursor == 0) return ""

        var start = cursor
        while (start > 0) {
            val character = value.text[start - 1]
            if (character.isLetterOrDigit() || character == '_') {
                start--
            } else {
                break
            }
        }

        if (start > 0 && value.text[start - 1] == '@') start--
        return value.text.substring(start, cursor)
    }
}
