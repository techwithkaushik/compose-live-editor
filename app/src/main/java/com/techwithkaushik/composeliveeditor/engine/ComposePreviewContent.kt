package com.techwithkaushik.composeliveeditor.engine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ComposePreviewContent(node: PreviewNode, states: Map<String, MutableIntState>) {
    when (node) {
        is PreviewNode.Column -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            node.children.forEach { ComposePreviewContent(it, states) }
        }
        is PreviewNode.Row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            node.children.forEach { ComposePreviewContent(it, states) }
        }
        is PreviewNode.Box -> Box(Modifier.fillMaxWidth()) {
            node.children.forEach { ComposePreviewContent(it, states) }
        }
        is PreviewNode.Text -> Text(renderText(node.value, states), style = MaterialTheme.typography.bodyLarge)
        is PreviewNode.Card -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                node.children.forEach { ComposePreviewContent(it, states) }
            }
        }
        is PreviewNode.Surface -> Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth()) {
                node.children.forEach { ComposePreviewContent(it, states) }
            }
        }
        is PreviewNode.Spacer -> Spacer(Modifier.height(node.heightDp.dp))
        PreviewNode.Divider -> HorizontalDivider()
        is PreviewNode.Button -> {
            val onClick = {
                node.actions.forEach { action ->
                    val state = states[action.stateName] ?: return@forEach
                    when (action) {
                        is PreviewAction.Increment -> state.intValue += 1
                        is PreviewAction.Decrement -> state.intValue -= 1
                        is PreviewAction.Add -> state.intValue += action.amount
                        is PreviewAction.Subtract -> state.intValue -= action.amount
                        is PreviewAction.Assign -> state.intValue = action.value
                    }
                }
            }
            when (node.style) {
                ButtonStyle.FILLED -> Button(onClick = onClick) { node.children.forEach { ComposePreviewContent(it, states) } }
                ButtonStyle.OUTLINED -> OutlinedButton(onClick = onClick) { node.children.forEach { ComposePreviewContent(it, states) } }
                ButtonStyle.TEXT -> TextButton(onClick = onClick) { node.children.forEach { ComposePreviewContent(it, states) } }
            }
        }
    }
}

private fun renderText(template: String, states: Map<String, MutableIntState>): String =
    STATE_REFERENCE_PATTERN.replace(template) { match ->
        val name = match.groupValues[1].ifEmpty { match.groupValues[2] }
        states[name]?.intValue?.toString() ?: match.value
    }
