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
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.subscribeAlways

/**
 * Hosts the production Sora editor inside the Compose screen.
 *
 * Sora owns editing, selection, scrolling, undo/redo, text actions and input
 * handling. Compose receives document changes for diagnostics and preview.
 */
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
                registerDiagnosticProvider { container ->
                    val text = this.text
                    val regions = currentDiagnostics.value.mapNotNull { diagnostic ->
                        val lineIndex = (diagnostic.line - 1).coerceIn(0, text.lineCount - 1)
                        val lineStart = text.getCharIndex(lineIndex, 0)
                        val lineLength = text.getColumnCount(lineIndex)
                        val start = (lineStart + diagnostic.column - 1).coerceIn(lineStart, lineStart + lineLength)
                        val end = (start + 1).coerceAtMost(lineStart + lineLength)
                        if (start == end) null else DiagnosticRegion(
                            start,
                            end,
                            if (diagnostic.severity == DiagnosticSeverity.ERROR) DiagnosticRegion.SEVERITY_ERROR else DiagnosticRegion.SEVERITY_WARNING,
                            diagnostic.ruleId.hashCode().toLong(),
                            DiagnosticDetail(diagnostic.message),
                        )
                    }
                    container.setDiagnostics(regions)
                }
                subscribeAlways<ContentChangeEvent> {
                    currentOnSourceChange.value(text.toString())
                }
            }
        },
        update = { editor ->
            if (editor.text.toString() != source) {
                editor.setText(source)
            }
            editor.invalidateDiagnostics()
        },
        onRelease = { editor ->
            editor.release()
        },
    )
}

