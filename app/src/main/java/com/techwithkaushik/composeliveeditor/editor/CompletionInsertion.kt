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
}
