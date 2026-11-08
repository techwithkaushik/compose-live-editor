package com.techwithkaushik.composeliveeditor.editor

import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeEditorLanguageToolsTest {
    @Test
    fun reportsParserErrorsWithSourceLocation() {
        val diagnostics = ComposeEditorLanguageTools.analyze("@Composable fun Screen() { Column { Text( } }")
        assertTrue(diagnostics.any { it.severity == DiagnosticSeverity.ERROR && it.ruleId == "compose-preview" })
    }

    @Test
    fun reportsTrailingWhitespaceAndTodoMarkers() {
        val diagnostics = ComposeEditorLanguageTools.analyze("// TODO: replace\nval title = \"Editor\"  \n")
        assertTrue(diagnostics.any { it.ruleId == "todo-marker" })
        assertTrue(diagnostics.any { it.ruleId == "style-trailing-whitespace" })
    }

    @Test
    fun reportsUnusedImports() {
        val diagnostics = ComposeEditorLanguageTools.analyze(
            "import androidx.compose.material3.Button\n@Composable fun Screen() { Column { Text(\"Hi\") } }",
        )
        assertTrue(diagnostics.any { it.ruleId == "unused-import" && it.message.contains("Button") })
    }

    @Test
    fun filtersCompletionsByPrefix() {
        val completions = ComposeEditorLanguageTools.completions("Out")
        assertTrue(completions.any { it.label == "OutlinedButton" && it.insertText.contains("onClick") })
    }

    @Test
    fun completionSnippetsContainActualNewlines() {
        val column = ComposeEditorLanguageTools.completions("Column").first { it.label == "Column" }
        assertTrue(column.insertText.contains('\n'))
        assertTrue(!column.insertText.contains("\\n"))
    }

    @Test
    fun offersImportsForComposeModifierAndTypeSymbols() {
        val diagnostics = ComposeEditorLanguageTools.analyze(
            "@Composable fun Screen() { Text(\"Hi\", modifier = Modifier.padding(8.dp)) }",
        )
        assertTrue(diagnostics.any { it.ruleId == "missing-import:androidx.compose.ui.Modifier" })
        assertTrue(diagnostics.any { it.ruleId == "missing-import:androidx.compose.ui.unit.dp" })
    }

    @Test
    fun doesNotReportComposeNamesInsideCommentsOrStringLiterals() {
        val diagnostics = ComposeEditorLanguageTools.analyze(
            "import androidx.compose.runtime.Composable\nimport androidx.compose.material3.Text\n@Composable fun Screen() { Text(\"Button( Column {\") } // Modifier.padding()",
        )
        assertTrue(diagnostics.none { it.ruleId.startsWith("missing-import:") })
    }

    @Test
    fun exposesExtendedComposeCompletionCatalog() {
        val labels = ComposeEditorLanguageTools.completions("").map { it.label }
        assertTrue(labels.contains("OutlinedButton"))
        assertTrue(labels.contains("Card"))
        assertTrue(labels.contains("Surface"))
        assertTrue(labels.contains("Spacer"))
        assertTrue(labels.contains("HorizontalDivider"))
        assertTrue(labels.contains("@Composable"))
        assertTrue(labels.contains("LazyColumn"))
        assertTrue(labels.contains("AnimatedVisibility"))
        assertTrue(labels.contains("Icons"))
        assertTrue(labels.contains("mutableStateOf"))
        assertTrue(ComposeEditorLanguageTools.importCandidates("Add").size > 1)
        assertTrue(labels.size > 100)
    }
}
