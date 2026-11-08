package com.techwithkaushik.composeliveeditor.editor

import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.diagnostic.Quickfix
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.subscribeAlways

/** Hosts Sora's native editor and native diagnostic tooltip/quick-fix UI. */
@Composable
fun SoraCodeEditor(
    source: String,
    onSourceChange: (String) -> Unit,
    diagnostics: List<EditorDiagnostic>,
    modifier: Modifier = Modifier,
) {
    val currentOnSourceChange = rememberUpdatedState(onSourceChange)
    val currentDiagnostics = rememberUpdatedState(diagnostics)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).apply {
                typefaceText = Typeface.MONOSPACE
                textSizePx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    13f,
                    context.resources.displayMetrics,
                )
                setText(source)
                setEditorLanguage(ComposeLanguage())
                setLineNumberEnabled(true)
                setHighlightCurrentLine(true)
                setHighlightCurrentBlock(true)
                setTabWidth(4)
                setDiagnostics(buildDiagnostics(this, currentDiagnostics.value))
                subscribeAlways<ContentChangeEvent> {
                    currentOnSourceChange.value(text.toString())
                }
            }
        },
        update = { editor ->
            if (editor.text.toString() != source) editor.setText(source)
            editor.setDiagnostics(buildDiagnostics(editor, currentDiagnostics.value))
        },
        onRelease = { editor -> editor.release() },
    )
}

private fun buildDiagnostics(
    editor: CodeEditor,
    diagnostics: List<EditorDiagnostic>,
): DiagnosticsContainer {
    val content = editor.text
    return DiagnosticsContainer().apply {
        val regions = diagnostics.mapNotNull { diagnostic ->
            if (content.lineCount == 0) return@mapNotNull null
            val lineIndex = (diagnostic.line - 1).coerceIn(0, content.lineCount - 1)
            val lineStart = content.getCharIndex(lineIndex, 0)
            val lineLength = content.getColumnCount(lineIndex)
            val start = (lineStart + diagnostic.column - 1).coerceIn(lineStart, lineStart + lineLength)
            val end = (start + 1).coerceAtMost(lineStart + lineLength)
            if (start == end) return@mapNotNull null

            val quickfixes = diagnostic.ruleId
                .takeIf { it.startsWith("missing-import:") }
                ?.removePrefix("missing-import:")
                ?.let { qualifiedName ->
                    listOf(
                        Quickfix(
                            "Import \${qualifiedName.substringAfterLast('.')}",
                            fixAction = Runnable { insertImport(content, qualifiedName) },
                        ),
                    )
                }

            DiagnosticRegion(
                start,
                end,
                when (diagnostic.severity) {
                    DiagnosticSeverity.ERROR -> DiagnosticRegion.SEVERITY_ERROR
                    DiagnosticSeverity.WARNING -> DiagnosticRegion.SEVERITY_WARNING
                    DiagnosticSeverity.INFO -> DiagnosticRegion.SEVERITY_INFO
                },
                diagnostic.ruleId.hashCode().toLong(),
                DiagnosticDetail(diagnostic.message, quickfixes = quickfixes),
            )
        }
        addDiagnostics(regions)
    }
}

private fun insertImport(
    content: io.github.rosemoe.sora.text.Content,
    qualifiedName: String,
) {
    val importLine = "import $qualifiedName"
    val currentSource = content.toString()
    if (Regex("(?m)^\\s*\${Regex.escape(importLine)}\\s*$").containsMatchIn(currentSource)) return

    val lines = currentSource.lines()
    val packageLine = lines.indexOfFirst { it.trimStart().startsWith("package ") }
    val lastImportLine = lines.indexOfLast { it.trimStart().startsWith("import ") }
    val insertionLine = when {
        lastImportLine >= 0 -> lastImportLine + 1
        packageLine >= 0 -> packageLine + 1
        else -> 0
    }
    content.insert(insertionLine, 0, "$importLine\n")
}
