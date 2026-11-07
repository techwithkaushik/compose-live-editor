package com.techwithkaushik.composeliveeditor.editor

import android.os.Bundle
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference

/**
 * Compose-aware Sora language adapter.
 *
 * Completion items are published through Sora's native completion window,
 * so suggestions remain anchored to the caret and participate in editor
 * selection, keyboard navigation and commit behavior.
 */
class ComposeLanguage : EmptyLanguage() {

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        val line = content.getLine(position.line)
        val prefix = identifierPrefix(line, position.column)
        if (prefix.isEmpty()) return

        ComposeEditorLanguageTools.completions(prefix).forEach { item ->
            publisher.addItem(
                SimpleCompletionItem(
                    item.label,
                    item.detail,
                    prefix.length,
                    item.insertText,
                ),
            )
        }
    }

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        val beforeCursor = content.getLine(line).subSequence(0, column.coerceAtMost(content.getLine(line).length))
        val opens = beforeCursor.count { it == '{' }
        val closes = beforeCursor.count { it == '}' }
        return ((opens - closes).coerceAtLeast(0)) * INDENT_WIDTH
    }

    override fun useTab(): Boolean = false

    private fun identifierPrefix(line: CharSequence, column: Int): String {
        var start = column.coerceIn(0, line.length)
        while (start > 0) {
            val character = line[start - 1]
            if (!character.isLetterOrDigit() && character != '_' && character != '@') break
            start--
        }
        return line.subSequence(start, column.coerceIn(start, line.length)).toString()
    }

    private companion object {
        const val INDENT_WIDTH = 4
    }
}
