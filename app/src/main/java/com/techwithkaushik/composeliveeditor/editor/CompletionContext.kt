package com.techwithkaushik.composeliveeditor.editor

import androidx.compose.ui.text.input.TextFieldValue

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
