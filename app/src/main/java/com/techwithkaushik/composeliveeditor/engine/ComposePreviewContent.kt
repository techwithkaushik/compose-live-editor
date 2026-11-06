package com.techwithkaushik.composeliveeditor.engine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
        is PreviewNode.Button -> Button(
            enabled = true,
            onClick = {
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
            },
        ) { node.children.forEach { ComposePreviewContent(it, states) } }
    }
}

private fun renderText(template: String, states: Map<String, MutableIntState>): String =
    TEMPLATE_REFERENCE.replace(template) { match ->
        val name = match.groupValues[1].ifEmpty { match.groupValues[2] }
        states[name]?.intValue?.toString() ?: match.value
    }

private val TEMPLATE_REFERENCE =
    Regex("""\$([A-Za-z_][A-Za-z0-9_]*)|\$\{([A-Za-z_][A-Za-z0-9_]*)}""")
