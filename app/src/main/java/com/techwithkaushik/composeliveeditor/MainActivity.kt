package com.techwithkaushik.composeliveeditor

import android.os.Bundle
import android.content.Context
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.techwithkaushik.composeliveeditor.engine.ComposePreviewContent
import com.techwithkaushik.composeliveeditor.engine.ComposeSourceEngine
import com.techwithkaushik.composeliveeditor.engine.PreviewParseResult
import com.techwithkaushik.composeliveeditor.editor.ComposeEditorLanguageTools
import com.techwithkaushik.composeliveeditor.editor.SoraCodeEditor

private const val EDITOR_PREFERENCES = "compose_live_editor_session"
private const val KEY_SOURCE = "source"
private const val KEY_SELECTED_PREVIEW = "selected_preview"
private const val KEY_SHOW_PREVIEW = "show_preview"

private const val STARTER_SOURCE = """import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
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
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) {
        context.getSharedPreferences(EDITOR_PREFERENCES, Context.MODE_PRIVATE)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var source by remember {
        mutableStateOf(preferences.getString(KEY_SOURCE, null) ?: STARTER_SOURCE)
    }
    var showPreview by remember {
        mutableStateOf(preferences.getBoolean(KEY_SHOW_PREVIEW, false))
    }
    var previewResult by remember { mutableStateOf<PreviewParseResult?>(null) }
    var selectedPreviewName by remember {
        mutableStateOf(preferences.getString(KEY_SELECTED_PREVIEW, null))
    }
    var showFileManager by rememberSaveable { mutableStateOf(false) }
    var activeFile by remember { mutableStateOf<File?>(null) }
    var isDirty by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    val editorController = remember { com.techwithkaushik.composeliveeditor.editor.SoraEditorController() }
    val coroutineScope = rememberCoroutineScope()
    val workspaceDirectory = remember(context) { File(context.filesDir, "workspace").apply { mkdirs() } }
    var darkTheme by rememberSaveable { mutableStateOf(false) }
    val currentSource = rememberUpdatedState(source)
    val currentPreviewName = rememberUpdatedState(selectedPreviewName)
    val currentShowPreview = rememberUpdatedState(showPreview)
    val persistSession: (flushToDisk: Boolean) -> Unit = { flushToDisk ->
        val editor = preferences.edit()
            .putString(KEY_SOURCE, currentSource.value)
            .putString(KEY_SELECTED_PREVIEW, currentPreviewName.value)
            .putBoolean(KEY_SHOW_PREVIEW, currentShowPreview.value)
        if (flushToDisk) editor.commit() else editor.apply()
    }

    // Debounced autosave protects the editor from losing recent edits while avoiding
    // a disk write for every keystroke.
    LaunchedEffect(source, selectedPreviewName, showPreview) {
        delay(350)
        persistSession(false)
    }

    LaunchedEffect(workspaceDirectory) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (!workspaceDirectory.exists()) workspaceDirectory.mkdirs()
                val starterFile = File(workspaceDirectory, "Main.kt")
                if (!starterFile.exists()) starterFile.writeText(STARTER_SOURCE)
                starterFile
            }
        }.onSuccess { starterFile ->
            if (activeFile == null && source == STARTER_SOURCE) activeFile = starterFile
        }
    }

    LaunchedEffect(source, activeFile) {
        val file = activeFile ?: return@LaunchedEffect
        val snapshot = source
        delay(450)
        val saved = withContext(Dispatchers.IO) {
            runCatching {
                val root = workspaceDirectory.canonicalFile.toPath()
                val target = file.canonicalFile.toPath()
                check(target.startsWith(root)) { "File is outside the workspace" }
                file.parentFile?.mkdirs()
                file.writeText(snapshot)
            }
        }
        if (saved.isSuccess && source == snapshot) isDirty = false
    }

    val saveCurrentFile: () -> Unit = {
        val snapshot = source
        val currentTarget = activeFile
        coroutineScope.launch {
            val savedFile = withContext(Dispatchers.IO) {
                runCatching {
                    val root = workspaceDirectory.canonicalFile
                    check(root.exists() || root.mkdirs()) { "Unable to create workspace" }
                    val target = currentTarget ?: run {
                        var candidate = File(root, "Untitled.kt")
                        var suffix = 1
                        while (candidate.exists()) {
                            candidate = File(root, "Untitled-$suffix.kt")
                            suffix++
                        }
                        candidate
                    }
                    val canonicalTarget = target.canonicalFile
                    check(canonicalTarget.toPath().startsWith(root.canonicalFile.toPath())) {
                        "File is outside the workspace"
                    }
                    canonicalTarget.parentFile?.mkdirs()
                    canonicalTarget.writeText(snapshot)
                    check(
                        preferences.edit()
                            .putString(KEY_SOURCE, snapshot)
                            .putString(KEY_SELECTED_PREVIEW, selectedPreviewName)
                            .putBoolean(KEY_SHOW_PREVIEW, showPreview)
                            .commit()
                    ) { "Unable to persist editor session" }
                    canonicalTarget
                }
            }
            savedFile.onSuccess { file ->
                activeFile = file
                isDirty = source != snapshot
                Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_SHORT).show()
            }.onFailure { failure ->
                Toast.makeText(context, "Save failed: ${failure.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Flush the latest editor session when the app goes to the background or
    // the composition is disposed (configuration change / activity recreation).
    DisposableEffect(lifecycleOwner, preferences) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) persistSession(true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            persistSession(true)
        }
    }

    LaunchedEffect(source, selectedPreviewName, showPreview) {
        previewResult = if (showPreview) {
            ComposeSourceEngine.parse(source, selectedPreviewName)
        } else {
            null
        }
    }

    val diagnostics = remember(source) { ComposeEditorLanguageTools.analyze(source) }
    BackHandler(enabled = showFileManager || showPreview) {
        if (showFileManager) showFileManager = false else showPreview = false
    }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(activeFile?.name ?: "Untitled", maxLines = 1)
                        Text(
                            if (isDirty) "Modified" else "Saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDirty) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {},
                actions = {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = saveCurrentFile) {
                            Icon(Icons.Filled.Save, contentDescription = "Save")
                        }
                        IconButton(onClick = {
                            editorController.undo()
                            canUndo = editorController.canUndo()
                            canRedo = editorController.canRedo()
                        }, enabled = canUndo) {
                            Icon(Icons.Filled.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = {
                            editorController.redo()
                            canUndo = editorController.canUndo()
                            canRedo = editorController.canRedo()
                        }, enabled = canRedo) {
                            Icon(Icons.Filled.Redo, contentDescription = "Redo")
                        }
                        IconButton(onClick = { showFileManager = true }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = "File explorer")
                        }
                        IconButton(onClick = { darkTheme = !darkTheme }) {
                            Icon(Icons.Filled.Brightness4, contentDescription = "Toggle theme")
                        }
                        IconButton(
                            onClick = {
                                previewResult = ComposeSourceEngine.parse(source, selectedPreviewName)
                                showPreview = !showPreview
                            },
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Preview")
                        }
                    }
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
                        isDirty = true
                        if (showPreview) previewResult = ComposeSourceEngine.parse(updated, selectedPreviewName)
                    },
                    diagnostics = diagnostics,
                    darkTheme = darkTheme,
                    controller = editorController,
                    onHistoryChanged = { undoAvailable, redoAvailable ->
                        canUndo = undoAvailable
                        canRedo = redoAvailable
                    },
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
            if (showFileManager) FileManagerDrawer(
                rootDirectory = workspaceDirectory,
                onOpenFile = { file, contents ->
                    activeFile = file
                    source = contents
                    isDirty = false
                    canUndo = false
                    canRedo = false
                    selectedPreviewName = null
                },
                onFilePathChanged = { previous, updated ->
                    val openPath = activeFile?.canonicalFile?.toPath()
                    val previousPath = previous.canonicalFile.toPath()
                    if (openPath != null && (openPath == previousPath || openPath.startsWith(previousPath))) {
                        activeFile = if (updated == null) {
                            isDirty = true
                            null
                        } else {
                            val relative = previousPath.relativize(openPath)
                            File(updated.canonicalFile.toPath().resolve(relative).toFile().path)
                        }
                    }
                },
                onClose = { showFileManager = false },
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
                                result.states.associate { state ->
                                    state.name to mutableStateOf<Any>(
                                        state.initialText ?: state.initialBoolean ?: state.initialValue,
                                    )
                                }
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
