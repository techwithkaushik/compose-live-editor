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
import com.techwithkaushik.composeliveeditor.engine.ComposePreviewContent
import com.techwithkaushik.composeliveeditor.engine.ComposeSourceEngine
import com.techwithkaushik.composeliveeditor.engine.PreviewParseResult
import com.techwithkaushik.composeliveeditor.editor.ComposeEditorLanguageTools
import com.techwithkaushik.composeliveeditor.editor.SoraCodeEditor

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
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var previewResult by remember { mutableStateOf<PreviewParseResult?>(null) }
    var selectedPreviewName by rememberSaveable { mutableStateOf<String?>(null) }
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
                            previewResult = ComposeSourceEngine.parse(source, selectedPreviewName)
                            showPreview = !showPreview
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Preview") }
                },
            )
        },
        bottomBar = {
            HorizontalDivider()
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                SoraCodeEditor(
                    source = source,
                    onSourceChange = { updated ->
                        source = updated
                        if (showPreview) previewResult = ComposeSourceEngine.parse(updated, selectedPreviewName)
                    },
                    diagnostics = diagnostics,
                    darkTheme = darkTheme,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (showPreview) FloatingPreviewWindow(
                result = previewResult,
                onClose = { showPreview = false },
                onSelectPreview = { name ->
                    selectedPreviewName = name
                    previewResult = ComposeSourceEngine.parse(source, name)
                },
            )
        }
    }
    }
}

@Composable
private fun FloatingPreviewWindow(result: PreviewParseResult?, onClose: () -> Unit, onSelectPreview: (String) -> Unit) {
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
                    val success = result as? PreviewParseResult.Success
                    Text(
                        success?.entryPoint?.let { it + "()" } ?: "Preview",
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
                    if (success != null && success.previewEntries.size > 1) {
                        val current = success.previewEntries.indexOf(success.entryPoint).coerceAtLeast(0)
                        androidx.compose.material3.TextButton(
                            onClick = { onSelectPreview(success.previewEntries[(current + 1) % success.previewEntries.size]) },
                        ) { Text("${current + 1}/${success.previewEntries.size} ›") }
                    }
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
