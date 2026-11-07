package com.techwithkaushik.composeliveeditor.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionInsertionTest {
    @Test
    fun insertsAtCaretAndPlacesCursorAfterSnippet() {
        val result = CompletionInsertion.insert(
            TextFieldValue("beforeafter", selection = TextRange(6)),
            "Column {\n}",
        )

        assertEquals("before\nColumn {\n}\nafter", result.text)
        assertEquals(6 + 1 + "Column {\n}".length, result.selection.start)
        assertEquals(result.selection.start, result.selection.end)
    }

    @Test
    fun replacesSelectedText() {
        val result = CompletionInsertion.insert(
            TextFieldValue("beforeREMOVEafter", selection = TextRange(6, 12)),
            "Text(\"Hello\")",
        )

        assertEquals("before\nText(\"Hello\")\nafter", result.text)
        assertEquals(7 + "Text(\"Hello\")".length, result.selection.start)
    }

    @Test
    fun insertsAtBeginningWithoutLeadingNewline() {
        val result = CompletionInsertion.insert(
            TextFieldValue("existing", selection = TextRange(0)),
            "Column",
        )

        assertEquals("Column\nexisting", result.text)
        assertEquals("Column".length, result.selection.start)
    }

    @Test
    fun insertsAtEndWithLeadingNewline() {
        val result = CompletionInsertion.insert(
            TextFieldValue("existing", selection = TextRange(8)),
            "Column",
        )

        assertEquals("existing\nColumn", result.text)
        assertEquals("existing\nColumn".length, result.selection.start)
    }

    @Test
    fun preservesExistingNewlineBoundaries() {
        val result = CompletionInsertion.insert(
            TextFieldValue("first\nlast", selection = TextRange(6)),
            "middle",
        )

        assertEquals("first\nmiddle\nlast", result.text)
        assertEquals("first\nmiddle".length, result.selection.start)
    }

    @Test
    fun supportsReversedSelectionDirection() {
        val result = CompletionInsertion.insert(
            TextFieldValue("beforeREMOVEafter", selection = TextRange(12, 6)),
            "X",
        )

        assertEquals("before\nX\nafter", result.text)
        assertEquals(8, result.selection.start)
    }

    @Test
    fun emptySnippetLeavesValueUnchanged() {
        val original = TextFieldValue("source", selection = TextRange(2, 4))

        assertEquals(original, CompletionInsertion.insert(original, ""))
    }

    @Test
    fun completionReplacesTheTypedPrefix() {
        val result = CompletionInsertion.complete(
            TextFieldValue("Col", selection = TextRange(3)),
            "Column",
            prefixLength = 3,
        )

        assertEquals("Column", result.text)
        assertEquals(6, result.selection.start)
    }

    @Test
    fun completionReplacesPrefixWithoutDeletingFollowingText() {
        val result = CompletionInsertion.complete(
            TextFieldValue("ColAfter", selection = TextRange(3)),
            "Column",
            prefixLength = 3,
        )

        assertEquals("Column\\nAfter", result.text)
        assertEquals(6, result.selection.start)
    }

    @Test(expected = IllegalArgumentException::class)
    fun completionRejectsPrefixLongerThanTextBeforeCursor() {
        CompletionInsertion.complete(
            TextFieldValue("Col", selection = TextRange(2)),
            "Column",
            prefixLength = 3,
        )
    }

    @Test
    fun findsIdentifierPrefixAtCaret() {
        assertEquals(
            "Col",
            CompletionContext.prefixAt(TextFieldValue("Column", selection = TextRange(3))),
        )
    }

    @Test
    fun includesComposableAnnotationMarkerInPrefix() {
        assertEquals(
            "@Comp",
            CompletionContext.prefixAt(TextFieldValue("@Comp", selection = TextRange(5))),
        )
    }

    @Test
    fun findsPrefixAfterMemberAccess() {
        assertEquals(
            "Tex",
            CompletionContext.prefixAt(TextFieldValue("Modifier.Tex", selection = TextRange(12))),
        )
    }

    @Test
    fun suppressesPrefixForSelectedText() {
        assertEquals(
            "",
            CompletionContext.prefixAt(TextFieldValue("Column", selection = TextRange(0, 3))),
        )
    }

    @Test
    fun returnsEmptyPrefixAtDocumentStart() {
        assertEquals("", CompletionContext.prefixAt(TextFieldValue("", selection = TextRange(0))))
    }

}
