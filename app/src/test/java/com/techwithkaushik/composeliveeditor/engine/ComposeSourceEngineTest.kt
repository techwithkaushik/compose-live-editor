package com.techwithkaushik.composeliveeditor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeSourceEngineTest {
    @Test
    fun parsesBasicComposeTree() {
        val result = ComposeSourceEngine.parse(
            """@Composable fun Screen() { Column { Text("Hello"); Button(onClick = { }) { Text("Tap") } } }""",
        )
        assertTrue("Expected success, got $result", result is PreviewParseResult.Success)
        val root = (result as PreviewParseResult.Success).content as PreviewNode.Column
        assertEquals("Hello", (root.children[0] as PreviewNode.Text).value)
        assertTrue((root.children[1] as PreviewNode.Button).actions.isEmpty())
    }

    @Test
    fun parsesStateAndClickActions() {
        val result = ComposeSourceEngine.parse(
            """@Composable fun Counter() {
                var count by remember { mutableIntStateOf(0) }
                Column {
                    Text("Count: ${'$'}count")
                    Button(onClick = { count++; count += 2 }) { Text("Add") }
                    Button(onClick = { count--; count -= 1 }) { Text("Subtract") }
                    Button(onClick = { count = 10 }) { Text("Reset") }
                }
            }""",
        )
        assertTrue("Expected success, got $result", result is PreviewParseResult.Success)
        val success = result as PreviewParseResult.Success
        assertEquals(listOf(PreviewState("count", 0)), success.states)
        val root = success.content as PreviewNode.Column
        assertEquals("Count: " + '$' + "count", (root.children[0] as PreviewNode.Text).value)
        assertEquals(
            listOf(PreviewAction.Increment("count"), PreviewAction.Add("count", 2)),
            (root.children[1] as PreviewNode.Button).actions,
        )
        assertEquals(
            listOf(PreviewAction.Decrement("count"), PreviewAction.Subtract("count", 1)),
            (root.children[2] as PreviewNode.Button).actions,
        )
        assertEquals(listOf(PreviewAction.Assign("count", 10)), (root.children[3] as PreviewNode.Button).actions)
    }

    @Test
    fun acceptsMutableStateOfInteger() {
        val result = ComposeSourceEngine.parse(
            """@Composable fun Screen() { var value by remember { mutableStateOf(4) }; Column { Text("${'$'}value") } }""",
        )
        assertTrue(result is PreviewParseResult.Success)
        assertEquals(4, (result as PreviewParseResult.Success).states.single().initialValue)
    }

    @Test
    fun rejectsUnknownStateReferences() {
        val result = ComposeSourceEngine.parse(
            """@Composable fun Screen() { Column { Text("${'$'}missing") } }""",
        )
        assertTrue(result is PreviewParseResult.Failure)
    }

    @Test
    fun rejectsUnsupportedButtonStatements() {
        val result = ComposeSourceEngine.parse(
            """@Composable fun Screen() { var count by remember { mutableIntStateOf(0) }; Column { Button(onClick = { println(count) }) { Text("Run") } } }""",
        )
        assertTrue(result is PreviewParseResult.Failure)
    }

    @Test
    fun parsesEscapedStringsAndNestedLayouts() {
        val result = ComposeSourceEngine.parse(
            """@Composable fun Screen() { Column { Row { Text("A\nB") } } }""",
        )
        assertTrue(result is PreviewParseResult.Success)
        val root = (result as PreviewParseResult.Success).content as PreviewNode.Column
        val row = root.children.single() as PreviewNode.Row
        assertEquals("A\nB", (row.children.single() as PreviewNode.Text).value)
    }

    @Test
    fun reportsUnsupportedCallsWithSourceLocation() {
        val result = ComposeSourceEngine.parse("@Composable\nfun Screen() {\n  Column {\n    Image()\n  }\n}")
        assertTrue(result is PreviewParseResult.Failure)
        val diagnostic = (result as PreviewParseResult.Failure).diagnostic
        assertEquals(4, diagnostic.line)
        assertTrue(diagnostic.column > 1)
    }

    @Test
    fun requiresComposableEntryPointAnnotation() {
        val result = ComposeSourceEngine.parse("""fun Screen() { Column { Text("Hello") } }""")
        assertTrue(result is PreviewParseResult.Failure)
    }

    @Test
    fun rejectsUnsupportedTextArguments() {
        val result = ComposeSourceEngine.parse(
            """@Composable fun Screen() { Column { Text("Hello", color = Color.Red) } }""",
        )
        assertTrue(result is PreviewParseResult.Failure)
    }

    @Test
    fun rejectsMalformedAndOversizedSource() {
        assertTrue(ComposeSourceEngine.parse("@Composable fun Screen() { Column { Text( } }") is PreviewParseResult.Failure)
        assertTrue(ComposeSourceEngine.parse("x".repeat(65 * 1024)) is PreviewParseResult.Failure)
    }
}
