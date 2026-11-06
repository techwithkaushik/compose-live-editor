package com.techwithkaushik.composeliveeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techwithkaushik.composeliveeditor.engine.ComposePreviewContent
import com.techwithkaushik.composeliveeditor.engine.ComposeSourceEngine
import com.techwithkaushik.composeliveeditor.engine.PreviewParseResult

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
    BackHandler(enabled = showPreview) { showPreview = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showPreview) {
                        val entryPoint = (previewResult as? PreviewParseResult.Success)?.entryPoint
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Text(
                                entryPoint?.let { "$it()" } ?: "Live Preview",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "LIVE PREVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Text("Compose Editor")
                    }
                },
                navigationIcon = {
                    if (showPreview) TextButton(onClick = { showPreview = false }) { Text("‹ Editor") }
                },
                actions = {
                    if (!showPreview) {
                        Button(
                            onClick = {
                                previewResult = ComposeSourceEngine.parse(source)
                                showPreview = true
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text("▶ Run Preview") }
                    } else {
                        TextButton(onClick = { previewResult = ComposeSourceEngine.parse(source) }) { Text("Refresh") }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider()
                Text(
                    if (showPreview) "On-device Compose interpreter · interactive state preview"
                    else "Kotlin · Compose · in-memory editor",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            SourceEditor(
                source = source,
                onSourceChange = {
                    source = it
                    if (showPreview) previewResult = ComposeSourceEngine.parse(it)
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (showPreview) FloatingPreviewWindow(
                result = previewResult,
                onClose = { showPreview = false },
                onRefresh = { previewResult = ComposeSourceEngine.parse(source) },
            )
        }
    }
}

@Composable
private fun SourceEditor(source: String, onSourceChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Edit the Kotlin source, then tap Run Preview.", style = MaterialTheme.typography.bodySmall)
        BasicTextField(
            value = source,
            onValueChange = onSourceChange,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            decorationBox = { innerTextField ->
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(end = 12.dp)) {
                        (1..maxOf(1, source.count { it == '\n' } + 1)).forEach { line ->
                            Text(
                                line.toString(),
                                color = MaterialTheme.colorScheme.outline,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun FloatingPreviewWindow(result: PreviewParseResult?, onClose: () -> Unit, onRefresh: () -> Unit) {
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
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f).pointerInput(availableWidth, availableHeight, panelWidth, panelHeight) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                panelX = (panelX + drag.x / density).coerceIn(0f, (availableWidth - panelWidth).coerceAtLeast(0f))
                                panelY = (panelY + drag.y / density).coerceIn(0f, (availableHeight - panelHeight).coerceAtLeast(0f))
                            }
                        },
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            (result as? PreviewParseResult.Success)?.entryPoint?.let { it + "()" } ?: "Live Preview",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                        )
                        Text("LIVE · DRAG TO MOVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = onRefresh) { Text("↻") }
                    TextButton(onClick = onClose) { Text("×") }
                }
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (result) {
                        is PreviewParseResult.Success -> {
                            val states = remember(result.states) {
                                result.states.associate { it.name to mutableIntStateOf(it.initialValue) }
                            }
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("PREVIEW CANVAS · ${states.size} states", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                HorizontalDivider()
                                ComposePreviewContent(result.content, states)
                            }
                        }
                        is PreviewParseResult.Failure -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Preview error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                            Text("Line ${result.diagnostic.line}, column ${result.diagnostic.column}", style = MaterialTheme.typography.labelSmall)
                            Text(result.diagnostic.message, style = MaterialTheme.typography.bodyMedium)
                        }
                        null -> Text("No preview result", Modifier.padding(16.dp))
                    }
                }
                Box(
                    Modifier.align(Alignment.End).size(32.dp).pointerInput(availableWidth, availableHeight) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            panelWidth = (panelWidth + drag.x / density).coerceIn(minWidth, availableWidth)
                            panelHeight = (panelHeight + drag.y / density).coerceIn(minHeight, availableHeight)
                            panelX = panelX.coerceIn(0f, (availableWidth - panelWidth).coerceAtLeast(0f))
                            panelY = panelY.coerceIn(0f, (availableHeight - panelHeight).coerceAtLeast(0f))
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { Text("◢", color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
