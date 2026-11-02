package com.techwithkaushik.composeliveeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val STARTER_SOURCE = """@Composable
fun MyScreen() {
    Column {
        Text("Hello, Compose!")
        Button(onClick = { }) {
            Text("Click me")
        }
    }
}"""

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ComposeEditorApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeEditorApp() {
    var source by rememberSaveable { mutableStateOf(STARTER_SOURCE) }
    var showPreview by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showPreview) {
        showPreview = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showPreview) "Preview" else "Compose Editor") },
                navigationIcon = {
                    if (showPreview) {
                        TextButton(onClick = { showPreview = false }) {
                            Text("‹ Editor")
                        }
                    }
                },
                actions = {
                    if (!showPreview) {
                        Button(
                            onClick = { showPreview = true },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("▶ Run Preview")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider()
                Text(
                    text = if (showPreview) "Preview engine: not connected" else "Kotlin · Compose · In-memory editor",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    ) { innerPadding ->
        if (showPreview) {
            PreviewPlaceholder(
                source = source,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        } else {
            SourceEditor(
                source = source,
                onSourceChange = { source = it },
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        }
    }
}

@Composable
private fun SourceEditor(
    source: String,
    onSourceChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Edit the sample code, then tap Run Preview.",
            style = MaterialTheme.typography.bodySmall
        )
        BasicTextField(
            value = source,
            onValueChange = onSourceChange,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            decorationBox = { innerTextField ->
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(end = 12.dp)) {
                        (1..maxOf(1, source.count { it == '\n' } + 1)).forEach { line ->
                            Text(
                                text = line.toString(),
                                color = MaterialTheme.colorScheme.outline,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun PreviewPlaceholder(
    source: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Preview is not available yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "The editor and navigation are ready. The next milestone is an embedded Kotlin + Compose compiler; until it is integrated, this app will not pretend that your source code has been compiled or rendered.",
            style = MaterialTheme.typography.bodyMedium
        )
        HorizontalDivider()
        Text("Current source", style = MaterialTheme.typography.titleMedium)
        Text(
            text = source,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}
