package com.techwithkaushik.composeliveeditor.editor

import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
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
    onEditorReady: (CodeEditor?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSourceChange = rememberUpdatedState(onSourceChange)
    val currentOnEditorReady = rememberUpdatedState(onEditorReady)

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
                subscribeAlways<ContentChangeEvent> {
                    currentOnSourceChange.value(text.toString())
                }
                currentOnEditorReady.value(this)
            }
        },
        update = { editor ->
            if (editor.text.toString() != source) {
                editor.setText(source)
            }
            currentOnEditorReady.value(editor)
        },
        onRelease = { editor ->
            currentOnEditorReady.value(null)
            editor.release()
        },
    )
}

