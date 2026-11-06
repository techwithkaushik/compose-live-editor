package com.techwithkaushik.composeliveeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
        if (showPreview) PreviewScreen(previewResult, Modifier.fillMaxSize().padding(padding))
        else SourceEditor(source, { source = it }, Modifier.fillMaxSize().padding(padding))
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
private fun PreviewScreen(result: PreviewParseResult?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (result) {
            is PreviewParseResult.Success -> {
                val states = remember(result.states) {
                    result.states.associate { it.name to mutableIntStateOf(it.initialValue) }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "PREVIEW CANVAS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "${states.size} states",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                        ComposePreviewContent(result.content, states)
                    }
                }
                Text(
                    "Interactive preview · changes update state immediately",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            is PreviewParseResult.Failure -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Preview error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            "Line ${result.diagnostic.line}, column ${result.diagnostic.column}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            result.diagnostic.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            null -> Text(
                "No preview result. Return to the editor and run Preview.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
