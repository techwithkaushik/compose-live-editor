package com.techwithkaushik.composeliveeditor.engine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Renders only the immutable tree produced by [ComposeSourceEngine]. */
@Composable
fun ComposePreviewContent(node: PreviewNode) {
    when (node) {
        is PreviewNode.Column -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { node.children.forEach { ComposePreviewContent(it) } }

        is PreviewNode.Row -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { node.children.forEach { ComposePreviewContent(it) } }

        is PreviewNode.Box -> Box(modifier = Modifier.fillMaxWidth()) {
            node.children.forEach { ComposePreviewContent(it) }
        }

        is PreviewNode.Text -> Text(node.value, style = MaterialTheme.typography.bodyLarge)

        is PreviewNode.Button -> Button(
            enabled = false,
            onClick = { /* Source-level callback execution is intentionally not part of this parser milestone. */ },
        ) {
            node.children.forEach { ComposePreviewContent(it) }
        }
    }
}
