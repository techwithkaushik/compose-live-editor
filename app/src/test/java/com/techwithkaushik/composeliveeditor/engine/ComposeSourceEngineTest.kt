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
    fun keepsApplicationCallbacksInertInsteadOfExecutingThem() {
        val result = ComposeSourceEngine.parse(
            """@Composable fun Screen() { Column { Button(onClick = { submitLogin("a", "b") }, modifier = Modifier.fillMaxWidth()) { Text("Run") } } }""",
        )
        assertTrue("Expected safe no-op callback, got $result", result is PreviewParseResult.Success)
        val button = (((result as PreviewParseResult.Success).content as PreviewNode.Column).children.single() as PreviewNode.Button)
        assertTrue(button.actions.isEmpty())
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
    fun acceptsTextStyleArgumentsWhileRenderingLiteralText() {
        val result = ComposeSourceEngine.parse(
            """@Composable fun Screen() { Column { Text("Hello", color = Color.Red) } }""",
        )
        assertTrue("Expected literal text preview, got $result", result is PreviewParseResult.Success)
        val text = (((result as PreviewParseResult.Success).content as PreviewNode.Column).children.single() as PreviewNode.Text)
        assertEquals("Hello", text.value)
    }

    @Test
    fun rejectsMalformedAndOversizedSource() {
        assertTrue(ComposeSourceEngine.parse("@Composable fun Screen() { Column { Text( } }") is PreviewParseResult.Failure)
        assertTrue(ComposeSourceEngine.parse("x".repeat(65 * 1024)) is PreviewParseResult.Failure)
    }

    @Test
    fun parsesMaterialContainersAndButtonVariants() {
        val result = ComposeSourceEngine.parse(
            """@Composable fun Screen() { Column { Card {
                Surface { Text("Content") }
                OutlinedButton(onClick = { }) { Text("Outlined") }
                TextButton(onClick = { }) { Text("Text action") }
            } } }""",
        )
        assertTrue("Expected success, got $result", result is PreviewParseResult.Success)
        val root = (result as PreviewParseResult.Success).content as PreviewNode.Column
        val card = root.children.single() as PreviewNode.Card
        assertTrue(card.children[0] is PreviewNode.Surface)
        assertEquals(ButtonStyle.OUTLINED, (card.children[1] as PreviewNode.Button).style)
        assertEquals(ButtonStyle.TEXT, (card.children[2] as PreviewNode.Button).style)
    }


    @Test
    fun parsesSpacerAndDivider() {
        val result = ComposeSourceEngine.parse(
            """@Composable fun Screen() { Column { Spacer(modifier = Modifier.height(24.dp)); HorizontalDivider() } }""",
        )
        assertTrue("Expected success, got $result", result is PreviewParseResult.Success)
        val children = ((result as PreviewParseResult.Success).content as PreviewNode.Column).children
        assertEquals(24, (children[0] as PreviewNode.Spacer).heightDp)
        assertTrue(children[1] is PreviewNode.Divider)
    }

    @Test
    fun rejectsOutOfRangeSpacerHeight() {
        val result = ComposeSourceEngine.parse(
            """@Composable fun Screen() { Column { Spacer(modifier = Modifier.height(999.dp)) } }""",
        )
        assertTrue(result is PreviewParseResult.Failure)
    }

    @Test
    fun selectsPreviewFunctionAndExpandsMaterialThemeAndHelperComposable() {
        val source = """
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.material3.MaterialTheme
            @Composable
            fun LoginScreen(onLoginClick: (String, String) -> Unit) {
                var email by remember { mutableStateOf("") }
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sign in")
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
                    Button(onClick = { onLoginClick(email, email) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Login")
                    }
                }
            }
            @Preview(showBackground = true)
            @Composable
            fun SimpleLoginScreenPreview() {
                MaterialTheme {
                    LoginScreen(onLoginClick = { _, _ -> })
                }
            }
            @Composable
            fun AnotherHelper() { Column { Text("Not the preview") } }
        """.trimIndent()
        val result = ComposeSourceEngine.parse(source)
        assertTrue("Expected preview success, got $result", result is PreviewParseResult.Success)
        val success = result as PreviewParseResult.Success
        assertEquals("SimpleLoginScreenPreview", success.entryPoint)
        assertEquals(listOf("SimpleLoginScreenPreview"), success.previewEntries)
        val root = success.content as PreviewNode.Column
        assertEquals("Sign in", (root.children[0] as PreviewNode.Text).value)
        assertEquals("Email", (root.children[1] as PreviewNode.TextField).label)
        assertTrue(root.children[2] is PreviewNode.Button)
    }

    @Test
    fun exposesMultiplePreviewFunctionsAndAllowsSelectingOne() {
        val source = """
            @Preview @Composable fun FirstPreview() { Column { Text("First") } }
            @Composable @Preview(showBackground = true) fun SecondPreview() { Row { Text("Second") } }
            @Composable fun Helper() { Text("Helper") }
        """.trimIndent()
        val first = ComposeSourceEngine.parse(source)
        assertTrue(first is PreviewParseResult.Success)
        assertEquals(listOf("FirstPreview", "SecondPreview"), (first as PreviewParseResult.Success).previewEntries)
        val second = ComposeSourceEngine.parse(source, "SecondPreview")
        assertTrue(second is PreviewParseResult.Success)
        val secondSuccess = second as PreviewParseResult.Success
        assertEquals("SecondPreview", secondSuccess.entryPoint)
        assertTrue(secondSuccess.content is PreviewNode.Row)
    }

    @Test
    fun requiresPreviewWhenMultipleComposableFunctionsExist() {
        val source = """
            @Composable fun First() { Column { Text("A") } }
            @Composable fun Second() { Column { Text("B") } }
        """.trimIndent()
        assertTrue(ComposeSourceEngine.parse(source) is PreviewParseResult.Failure)
    }

    @Test
    fun acceptsWildcardComposeRuntimeImport() {
        val source = """
            import androidx.compose.runtime.*
            import androidx.compose.ui.tooling.preview.Preview

            @Preview(showBackground = true)
            @Composable
            fun RuntimeWildcardPreview() {
                var count by remember { mutableIntStateOf(2) }
                Column {
                    Text("Count: ${'$'}count")
                    Button(onClick = { count++ }) { Text("Add") }
                }
            }
        """.trimIndent()

        val result = ComposeSourceEngine.parse(source)
        assertTrue("Expected wildcard-import preview success, got $result", result is PreviewParseResult.Success)
        val success = result as PreviewParseResult.Success
        assertEquals("RuntimeWildcardPreview", success.entryPoint)
        assertEquals(2, success.states.single().initialValue)
        val root = success.content as PreviewNode.Column
        assertEquals("Count: " + 36.toChar() + "count", (root.children.first() as PreviewNode.Text).value)
        assertEquals(
            listOf(PreviewAction.Increment("count")),
            (root.children.last() as PreviewNode.Button).actions,
        )
    }

}
