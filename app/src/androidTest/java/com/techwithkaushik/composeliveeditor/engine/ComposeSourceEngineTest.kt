package com.techwithkaushik.composeliveeditor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeSourceEngineTest {
    @Test
    fun parsesStarterComposeScreen() {
        val source = """
            import androidx.compose.runtime.Composable
            @Composable
            fun MyScreen() {
                Column {
                    Text("Hello, Compose!")
                    Button(onClick = { }) {
                        Text("Click me")
                    }
                }
            }
        """.trimIndent()

        val result = ComposeSourceEngine.parse(source)
        assertTrue("Expected success, got $result", result is PreviewParseResult.Success)
        val root = (result as PreviewParseResult.Success).content as PreviewNode.Column
        assertEquals("Hello, Compose!", (root.children[0] as PreviewNode.Text).value)
        val button = root.children[1] as PreviewNode.Button
        assertEquals("Click me", (button.children.single() as PreviewNode.Text).value)
    }

    @Test
    fun parsesEscapedStringsAndNestedLayouts() {
        val result = ComposeSourceEngine.parse(
            """@Composable fun Screen() { Column { Row { Text("A\\nB") } } }"""
        )
        assertTrue(result is PreviewParseResult.Success)
        val root = (result as PreviewParseResult.Success).content as PreviewNode.Column
        val row = root.children.single() as PreviewNode.Row
        assertEquals("A\nB", (row.children.single() as PreviewNode.Text).value)
    }

    @Test
    fun reportsUnsupportedCallsWithSourceLocation() {
        val result = ComposeSourceEngine.parse(
            "@Composable\nfun Screen() {\n  Column {\n    Image()\n  }\n}"
        )
        assertTrue(result is PreviewParseResult.Failure)
        val diagnostic = (result as PreviewParseResult.Failure).diagnostic
        assertEquals(4, diagnostic.line)
        assertTrue(diagnostic.column > 1)
        assertTrue(diagnostic.message.contains("Unsupported Compose call"))
    }

    @Test
    fun rejectsMalformedAndOversizedSource() {
        val malformed = ComposeSourceEngine.parse("@Composable fun Screen() { Column { Text( } }")
        assertTrue(malformed is PreviewParseResult.Failure)
        val oversized = ComposeSourceEngine.parse("x".repeat(65 * 1024))
        assertTrue(oversized is PreviewParseResult.Failure)
    }
}
