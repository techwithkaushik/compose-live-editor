package com.techwithkaushik.composeliveeditor.editor

import com.techwithkaushik.composeliveeditor.engine.ComposeSourceEngine
import com.techwithkaushik.composeliveeditor.engine.PreviewParseResult

enum class DiagnosticSeverity { ERROR, WARNING, INFO }

data class EditorDiagnostic(
    val message: String,
    val line: Int,
    val column: Int,
    val severity: DiagnosticSeverity,
    val ruleId: String,
)

data class CompletionItem(
    val label: String,
    val insertText: String,
    val detail: String,
)

object ComposeEditorLanguageTools {
    private val importPattern = Regex("(?m)^\\s*import\\s+([\\w.]+)(?:\\s+as\\s+\\w+)?\\s*$")
    private val composablePattern = Regex("@Composable\\s+fun\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val trailingWhitespace = Regex("[ \\t]+$", RegexOption.MULTILINE)
    private val todoPattern = Regex("\\b(TODO|FIXME)\\b")
    private val componentNames = setOf(
        "Column", "Row", "Box", "Text", "Button", "OutlinedButton",
        "TextButton", "Card", "Surface", "Spacer", "Divider",
        "Checkbox", "Switch", "CircularProgressIndicator", "LinearProgressIndicator",
    )

    fun analyze(source: String): List<EditorDiagnostic> {
        if (source.isEmpty()) return emptyList()
        val diagnostics = ArrayList<EditorDiagnostic>()
        val parsed = ComposeSourceEngine.parse(source)
        if (parsed is PreviewParseResult.Failure) {
            diagnostics += EditorDiagnostic(
                parsed.diagnostic.message,
                parsed.diagnostic.line,
                parsed.diagnostic.column,
                DiagnosticSeverity.ERROR,
                "compose-preview",
            )
        }
        trailingWhitespace.findAll(source).forEach { match ->
            val line = source.substring(0, match.range.first).count { it == '\n' } + 1
            diagnostics += EditorDiagnostic("Trailing whitespace", line, match.range.first - source.lastIndexOf('\n', match.range.first - 1), DiagnosticSeverity.WARNING, "style-trailing-whitespace")
        }
        todoPattern.findAll(source).forEach { match ->
            val line = source.substring(0, match.range.first).count { it == '\n' } + 1
            val column = match.range.first - source.lastIndexOf('\n', match.range.first - 1)
            diagnostics += EditorDiagnostic("${match.value} marker should be resolved before release", line, column, DiagnosticSeverity.INFO, "todo-marker")
        }
        importPattern.findAll(source).forEach { match ->
            val importedName = match.groupValues[1].substringAfterLast('.')
            val body = source.substring(match.range.last + 1)
            if (importedName !in body) {
                val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                diagnostics += EditorDiagnostic("Unused import: $importedName", line, 1, DiagnosticSeverity.WARNING, "unused-import")
            }
        }
        composablePattern.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            if (source.substring(match.range.last + 1).contains("@Composable")) {
                val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                diagnostics += EditorDiagnostic("Only one preview entry point is supported; found additional composable declarations", line, 1, DiagnosticSeverity.WARNING, "multiple-entry-points")
            }
            if (name.isBlank()) diagnostics += EditorDiagnostic("Composable name is required", 1, 1, DiagnosticSeverity.ERROR, "missing-entry-name")
        }
        return diagnostics.distinct().sortedWith(compareBy<EditorDiagnostic>({ it.line }, { it.column }, { it.severity.ordinal }))
    }

    fun completions(prefix: String): List<CompletionItem> {
        val query = prefix.trim()
        return listOf(
            CompletionItem("Column", "Column {\\n    \\n}", "Vertical layout"),
            CompletionItem("Row", "Row {\\n    \\n}", "Horizontal layout"),
            CompletionItem("Box", "Box {\\n    \\n}", "Stack layout"),
            CompletionItem("Text", "Text(\\\"Hello\\\")", "Display text"),
            CompletionItem("Button", "Button(onClick = { }) {\\n    Text(\\\"Click\\\")\\n}", "Clickable action"),
            CompletionItem("OutlinedButton", "OutlinedButton(onClick = { }) {\\n    Text(\\\"Action\\\")\\n}", "Outlined action"),
            CompletionItem("TextButton", "TextButton(onClick = { }) {\\n    Text(\\\"Action\\\")\\n}", "Text action"),
            CompletionItem("Card", "Card {\\n    Text(\\\"Card content\\\")\\n}", "Material card"),
            CompletionItem("Surface", "Surface {\\n    Text(\\\"Content\\\")\\n}", "Material surface"),
            CompletionItem("Spacer", "Spacer(modifier = Modifier.height(8.dp))", "Layout spacing"),
            CompletionItem("Divider", "HorizontalDivider()", "Visual separator"),
            CompletionItem("@Composable", "@Composable\\nfun Screen() {\\n    Column {\\n        Text(\\\"Hello\\\")\\n    }\\n}", "Composable entry point"),
        ).filter { query.isEmpty() || it.label.startsWith(query, ignoreCase = true) || it.detail.contains(query, ignoreCase = true) }
            .take(8)
    }

    fun supportedComponents(): Set<String> = componentNames
}
