package com.techwithkaushik.composeliveeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.techwithkaushik.composeliveeditor.engine.ComposePreviewContent
import com.techwithkaushik.composeliveeditor.engine.ComposeSourceEngine
import com.techwithkaushik.composeliveeditor.engine.PreviewParseResult
import com.techwithkaushik.composeliveeditor.editor.ComposeEditorLanguageTools
import com.techwithkaushik.composeliveeditor.editor.DiagnosticSeverity
import com.techwithkaushik.composeliveeditor.editor.CompletionContext
import com.techwithkaushik.composeliveeditor.editor.SoraCodeEditor
import io.github.rosemoe.sora.widget.CodeEditor

private const val STARTER_SOURCE = """import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text

@Composable
fun MyScreen() {
    var count by remember { mutableIntStateOf(0) }
    Column {
        Text("Count: ${'$'}count")
        Button(onClick = { count++ }) { Text("Increment") }
        Button(onClick = { count-- }) { Text("Decrement") }
    }
}"""

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { ComposeEditorApp() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeEditorApp() {
    var source by rememberSaveable { mutableStateOf(STARTER_SOURCE) }
    var codeEditor by remember { mutableStateOf<CodeEditor?>(null) }
    var cursorOffset by remember { mutableIntStateOf(0) }
    var hasSelection by remember { mutableStateOf(false) }
    val completionPrefix = remember(source, cursorOffset, hasSelection) {
        if (hasSelection) "" else CompletionContext.prefixAt(
            TextFieldValue(source, selection = TextRange(cursorOffset.coerceIn(0, source.length)))
        )
    }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var previewResult by remember { mutableStateOf<PreviewParseResult?>(null) }
    var darkTheme by rememberSaveable { mutableStateOf(false) }
    val diagnostics = remember(source) { ComposeEditorLanguageTools.analyze(source) }
    BackHandler(enabled = showPreview) { showPreview = false }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Compose Editor") },
                navigationIcon = {},
                actions = {
                    androidx.compose.material3.TextButton(onClick = { darkTheme = !darkTheme }) {
                        Text(if (darkTheme) "Light" else "Dark")
                    }
                    Button(
                        onClick = {
                            previewResult = ComposeSourceEngine.parse(source)
                            showPreview = !showPreview
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Preview") }
                },
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider()
                Text(
                    "Kotlin · Compose · ${diagnostics.count { it.severity == DiagnosticSeverity.ERROR }} errors · ${diagnostics.count { it.severity == DiagnosticSeverity.WARNING }} warnings",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                SoraCodeEditor(
                    source = source,
                    onSourceChange = { updated ->
                        source = updated
                        if (showPreview) previewResult = ComposeSourceEngine.parse(updated)
                    },
                    onEditorReady = { codeEditor = it },
                    onSelectionChange = { offset, selected ->
                        cursorOffset = offset
                        hasSelection = selected
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                EditorAssistantPanel(
                    diagnostics = diagnostics,
                    completionPrefix = completionPrefix,
                    onInsert = { snippet ->
                        val editor = codeEditor ?: return@EditorAssistantPanel
                        if (!editor.cursor.isSelected) {
                            val prefixLength = completionPrefix.length
                            val cursor = editor.cursor.right
                            val start = (cursor - prefixLength).coerceAtLeast(0)
                            val startPosition = editor.text.indexer.getCharPosition(start)
                            val endPosition = editor.text.indexer.getCharPosition(cursor)
                            editor.setSelectionRegion(
                                startPosition.line, startPosition.column,
                                endPosition.line, endPosition.column,
                            )
                            editor.commitText(snippet, true, false)
                            source = editor.text.toString()
                            cursorOffset = editor.cursor.right
                            hasSelection = false
                            if (showPreview) previewResult = ComposeSourceEngine.parse(source)
                        }
                    },
                )
            }
            if (showPreview) FloatingPreviewWindow(
                result = previewResult,
                onClose = { showPreview = false },
            )
        }
    }
    }
}

@Composable
private fun EditorAssistantPanel(
    diagnostics: List<com.techwithkaushik.composeliveeditor.editor.EditorDiagnostic>,
    completionPrefix: String,
    onInsert: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Compose", style = MaterialTheme.typography.labelLarge)
            Text("Completion", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (completionPrefix.isEmpty()) {
            Text(
                "Type a Compose component name to see completions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                val suggestions = ComposeEditorLanguageTools.completions(completionPrefix)
                suggestions.forEach { item ->
                    AssistChip(
                        onClick = { onInsert(item.insertText) },
                        label = { Text(item.label) },
                        modifier = Modifier.padding(end = 4.dp),
                        colors = AssistChipDefaults.assistChipColors(),
                    )
                }
                if (suggestions.isEmpty()) {
                    Text("No matching completions", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        val visibleDiagnostics = diagnostics.take(3)
        visibleDiagnostics.forEach { diagnostic ->
            Text(
                "${diagnostic.severity}: ${diagnostic.line}:${diagnostic.column} · ${diagnostic.message}",
                color = if (diagnostic.severity == DiagnosticSeverity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
            )
        }
        if (diagnostics.isEmpty()) Text("No diagnostics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (diagnostics.size > visibleDiagnostics.size) Text("+${diagnostics.size - visibleDiagnostics.size} more diagnostics", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FloatingPreviewWindow(result: PreviewParseResult?, onClose: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableWidth = maxWidth.value
        val availableHeight = maxHeight.value
        var panelWidth by remember { mutableFloatStateOf(minOf(availableWidth * 0.84f, 360f)) }
        var panelHeight by remember { mutableFloatStateOf(minOf(availableHeight * 0.52f, 420f)) }
        var panelX by remember { mutableFloatStateOf(availableWidth - panelWidth - 12f) }
        var panelY by remember { mutableFloatStateOf(12f) }
        val minWidth = minOf(220f, availableWidth)
        val minHeight = minOf(180f, availableHeight)
        panelWidth = panelWidth.coerceIn(minWidth, availableWidth)
        panelHeight = panelHeight.coerceIn(minHeight, availableHeight)
        panelX = panelX.coerceIn(0f, (availableWidth - panelWidth).coerceAtLeast(0f))
        panelY = panelY.coerceIn(0f, (availableHeight - panelHeight).coerceAtLeast(0f))
        Card(
            modifier = Modifier.offset { IntOffset(panelX.dp.roundToPx(), panelY.dp.roundToPx()) }
                .width(panelWidth.dp).height(panelHeight.dp).zIndex(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (result as? PreviewParseResult.Success)?.entryPoint?.let { it + "()" } ?: "Preview",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .pointerInput(availableWidth, availableHeight, panelWidth, panelHeight) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    panelX = (panelX + drag.x / density).coerceIn(0f, (availableWidth - panelWidth).coerceAtLeast(0f))
                                    panelY = (panelY + drag.y / density).coerceIn(0f, (availableHeight - panelHeight).coerceAtLeast(0f))
                                }
                            },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                    Box(
                        Modifier
                            .size(28.dp)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (result) {
                        is PreviewParseResult.Success -> {
                            val states = remember(result.states) {
                                result.states.associate { it.name to mutableIntStateOf(it.initialValue) }
                            }
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ComposePreviewContent(result.content, states)
                            }
                        }
                        is PreviewParseResult.Failure -> Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Preview error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                            Text("Line ${result.diagnostic.line}, column ${result.diagnostic.column}", style = MaterialTheme.typography.labelSmall)
                            Text(result.diagnostic.message, style = MaterialTheme.typography.bodyMedium)
                        }
                        null -> Text("No preview result", Modifier.padding(16.dp))
                    }
                }
                Box(
                    Modifier.align(Alignment.End).size(24.dp).pointerInput(availableWidth, availableHeight) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            panelWidth = (panelWidth + drag.x / density).coerceIn(minWidth, availableWidth)
                            panelHeight = (panelHeight + drag.y / density).coerceIn(minHeight, availableHeight)
                            panelX = panelX.coerceIn(0f, (availableWidth - panelWidth).coerceAtLeast(0f))
                            panelY = panelY.coerceIn(0f, (availableHeight - panelHeight).coerceAtLeast(0f))
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { Text("◢", color = MaterialTheme.colorScheme.outline) }
            }
        }
    }
}
