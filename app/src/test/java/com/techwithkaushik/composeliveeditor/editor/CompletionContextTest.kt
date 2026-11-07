package com.techwithkaushik.composeliveeditor.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionContextTest {
    @Test
    fun findsIdentifierPrefixAtCaret() {
        assertEquals("Col", CompletionContext.prefixAt(TextFieldValue("Column", selection = TextRange(3))))
    }

    @Test
    fun includesComposableAnnotationMarkerInPrefix() {
        assertEquals("@Comp", CompletionContext.prefixAt(TextFieldValue("@Comp", selection = TextRange(5))))
    }

    @Test
    fun findsPrefixAfterMemberAccess() {
        assertEquals("Tex", CompletionContext.prefixAt(TextFieldValue("Modifier.Tex", selection = TextRange(12))))
    }

    @Test
    fun suppressesPrefixForSelectedText() {
        assertEquals("", CompletionContext.prefixAt(TextFieldValue("Column", selection = TextRange(0, 3))))
    }

    @Test
    fun returnsEmptyPrefixAtDocumentStart() {
        assertEquals("", CompletionContext.prefixAt(TextFieldValue("", selection = TextRange(0))))
    }
}
