package com.techwithkaushik.composeliveeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techwithkaushik.composeliveeditor.engine.ComposePreviewContent
import com.techwithkaushik.composeliveeditor.engine.ComposeSourceEngine
import com.techwithkaushik.composeliveeditor.engine.PreviewParseResult
import com.techwithkaushik.composeliveeditor.editor.ComposeEditorLanguageTools
import com.techwithkaushik.composeliveeditor.editor.DiagnosticSeverity
import com.techwithkaushik.composeliveeditor.editor.ComposeKotlinSyntaxHighlighting

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
    var editorValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(STARTER_SOURCE)) }
    val source = editorValue.text
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
                SourceEditor(
                    value = editorValue,
                    onValueChange = {
                        editorValue = it
                        if (showPreview) previewResult = ComposeSourceEngine.parse(it.text)
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                EditorAssistantPanel(
                    diagnostics = diagnostics,
                    onInsert = { snippet ->
                        val current = editorValue
                        val start = minOf(current.selection.start, current.selection.end)
                        val end = maxOf(current.selection.start, current.selection.end)
                        val prefix = if (start > 0 && current.text[start - 1] != '\n') "\n" else ""
                        val suffix = if (end < current.text.length && current.text[end] != '\n') "\n" else ""
                        val insertion = prefix + snippet + suffix
                        val updated = current.text.replaceRange(start, end, insertion)
                        val cursor = start + prefix.length + snippet.length
                        editorValue = TextFieldValue(updated, selection = TextRange(cursor))
                        if (showPreview) previewResult = ComposeSourceEngine.parse(updated)
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
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            ComposeEditorLanguageTools.completions("").forEach { item ->
                AssistChip(
                    onClick = { onInsert(item.insertText) },
                    label = { Text(item.label) },
                    modifier = Modifier.padding(end = 4.dp),
                    colors = AssistChipDefaults.assistChipColors(),
                )
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
private fun SourceEditor(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier) {
    val editorScrollState = rememberScrollState()
    val source = value.text
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 4.dp)
            .verticalScroll(editorScrollState),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = ComposeKotlinSyntaxHighlighting,
        decorationBox = { innerTextField ->
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(start = 8.dp, end = 8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    (1..maxOf(1, source.count { it == '\n' } + 1)).forEach { line ->
                        Text(
                            line.toString(),
                            modifier = Modifier.width(28.dp),
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.outline,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
                Box(Modifier.weight(1f).padding(start = 10.dp, end = 12.dp)) {
                    innerTextField()
                }
            }
        },
    )
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
