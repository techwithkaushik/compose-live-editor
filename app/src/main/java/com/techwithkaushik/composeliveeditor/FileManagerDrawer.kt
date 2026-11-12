package com.techwithkaushik.composeliveeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

private enum class FileDialogMode { NEW_FILE, NEW_FOLDER, RENAME, DELETE }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileManagerDrawer(
    rootDirectory: File,
    onOpenFile: (File, String) -> Unit,
    onClose: () -> Unit,
) {
    var currentDirectory by remember(rootDirectory) { mutableStateOf(rootDirectory) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var dialogMode by remember { mutableStateOf<FileDialogMode?>(null) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var inputName by remember { mutableStateOf("") }
    var operationError by remember { mutableStateOf<String?>(null) }

    fun isInsideRoot(file: File): Boolean {
        val root = rootDirectory.canonicalFile.toPath()
        val candidate = file.canonicalFile.toPath()
        return candidate == root || candidate.startsWith(root)
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).combinedClickable(onClick = onClose, onLongClick = {}))
        Surface(
            modifier = Modifier.width(320.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("File Manager", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text("Close") }
                }
                Text(
                    currentDirectory.relativeTo(rootDirectory).path.let { if (it.isBlank()) "/" else "/$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        dialogMode = FileDialogMode.NEW_FILE
                        inputName = ""
                        operationError = null
                    }, modifier = Modifier.weight(1f)) { Text("+ File") }
                    OutlinedButton(onClick = {
                        dialogMode = FileDialogMode.NEW_FOLDER
                        inputName = ""
                        operationError = null
                    }, modifier = Modifier.weight(1f)) { Text("+ Folder") }
                }
                if (currentDirectory != rootDirectory) {
                    TextButton(onClick = {
                        currentDirectory = currentDirectory.parentFile ?: rootDirectory
                        refreshKey++
                    }) { Text("↑  Parent folder") }
                }
                Text("Long-press an item for rename or delete", style = MaterialTheme.typography.labelSmall)
                val children = remember(currentDirectory, refreshKey) {
                    currentDirectory.listFiles()?.sortedWith(
                        compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
                    ).orEmpty()
                }
                if (children.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("This folder is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(children, key = { it.absolutePath }) { file ->
                            Row(
                                Modifier.fillMaxWidth().combinedClickable(
                                    onClick = {
                                        if (file.isDirectory) {
                                            currentDirectory = file
                                            refreshKey++
                                        } else if (file.isFile && isInsideRoot(file)) {
                                            runCatching { file.readText() }
                                                .onSuccess { content -> onOpenFile(file, content); onClose() }
                                                .onFailure { operationError = it.message ?: "Unable to open file" }
                                        }
                                    },
                                    onLongClick = {
                                        selectedFile = file
                                        inputName = file.name
                                        operationError = null
                                        dialogMode = FileDialogMode.RENAME
                                    },
                                ).padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (file.isDirectory) "📁" else "📄")
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(file.name, maxLines = 1)
                                    Text(
                                        if (file.isDirectory) "Folder" else file.length().toString() + " bytes",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("⋮", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                operationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    val mode = dialogMode
    if (mode != null) {
        AlertDialog(
            onDismissRequest = { dialogMode = null },
            title = {
                Text(
                    when (mode) {
                        FileDialogMode.NEW_FILE -> "Create file"
                        FileDialogMode.NEW_FOLDER -> "Create folder"
                        FileDialogMode.RENAME -> "Rename"
                        FileDialogMode.DELETE -> "Delete"
                    }
                )
            },
            text = {
                if (mode == FileDialogMode.DELETE) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Delete " + selectedFile?.name.orEmpty() + "? This operation cannot be undone.")
                        operationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            label = { Text(if (mode == FileDialogMode.RENAME) "New name" else "Name") },
                            singleLine = true,
                        )
                        operationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = selectedFile
                    val validName = inputName.isNotBlank() && inputName != "." && inputName != ".." &&
                        !inputName.contains('/') && !inputName.contains('\\')
                    runCatching {
                        when (mode) {
                            FileDialogMode.NEW_FILE -> {
                                check(validName) { "Enter a valid file name" }
                                val created = File(currentDirectory, inputName)
                                check(!created.exists()) { "An item with this name already exists" }
                                check(created.createNewFile()) { "Unable to create file" }
                            }
                            FileDialogMode.NEW_FOLDER -> {
                                check(validName) { "Enter a valid folder name" }
                                val created = File(currentDirectory, inputName)
                                check(!created.exists()) { "An item with this name already exists" }
                                check(created.mkdir()) { "Unable to create folder" }
                            }
                            FileDialogMode.RENAME -> {
                                check(target != null && isInsideRoot(target)) { "Invalid file selection" }
                                check(validName) { "Enter a valid name" }
                                val destination = File(target.parentFile, inputName)
                                check(!destination.exists()) { "An item with this name already exists" }
                                check(target.renameTo(destination)) { "Unable to rename item" }
                            }
                            FileDialogMode.DELETE -> {
                                check(target != null && isInsideRoot(target) && target != rootDirectory) { "Invalid file selection" }
                                check(target.deleteRecursively()) { "Unable to delete item" }
                            }
                        }
                    }.onSuccess {
                        dialogMode = null
                        selectedFile = null
                        operationError = null
                        refreshKey++
                    }.onFailure {
                        operationError = it.message ?: "File operation failed"
                    }
                }) { Text(if (mode == FileDialogMode.DELETE) "Delete" else "Save") }
            },
            dismissButton = {
                Row {
                    if (mode == FileDialogMode.RENAME) {
                        TextButton(onClick = { dialogMode = FileDialogMode.DELETE; operationError = null }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { dialogMode = null; operationError = null }) { Text("Cancel") }
                }
            },
        )
    }
}
