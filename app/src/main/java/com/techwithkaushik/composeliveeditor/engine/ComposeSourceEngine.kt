package com.techwithkaushik.composeliveeditor.engine

internal val STATE_REFERENCE_PATTERN = Regex("""\$([A-Za-z_][A-Za-z0-9_]*)|\$\{([A-Za-z_][A-Za-z0-9_]*)\}""")

sealed interface PreviewNode {
    data class Column(val children: List<PreviewNode>) : PreviewNode
    data class Row(val children: List<PreviewNode>) : PreviewNode
    data class Box(val children: List<PreviewNode>) : PreviewNode
    data class Text(val value: String) : PreviewNode
    data class TextField(val label: String, val stateName: String?, val password: Boolean = false, val passwordVisibleStateName: String? = null) : PreviewNode
    data class Button(val children: List<PreviewNode>, val actions: List<PreviewAction>, val style: ButtonStyle = ButtonStyle.FILLED) : PreviewNode
    data class Card(val children: List<PreviewNode>) : PreviewNode
    data class Surface(val children: List<PreviewNode>) : PreviewNode
    data class Spacer(val heightDp: Int) : PreviewNode
    data object Divider : PreviewNode
}

enum class ButtonStyle { FILLED, OUTLINED, TEXT }
sealed interface PreviewAction {
    val stateName: String
    data class Increment(override val stateName: String) : PreviewAction
    data class Decrement(override val stateName: String) : PreviewAction
    data class Add(override val stateName: String, val amount: Int) : PreviewAction
    data class Subtract(override val stateName: String, val amount: Int) : PreviewAction
    data class Assign(override val stateName: String, val value: Int) : PreviewAction
    data class AssignText(override val stateName: String, val value: String) : PreviewAction
    data class AssignBoolean(override val stateName: String, val value: Boolean) : PreviewAction
    data class ToggleBoolean(override val stateName: String) : PreviewAction
}
data class PreviewState(val name: String, val initialValue: Int = 0, val initialText: String? = null, val initialBoolean: Boolean? = null)
data class SourceDiagnostic(val message: String, val line: Int, val column: Int)
sealed interface PreviewParseResult {
    data class Success(val entryPoint: String, val content: PreviewNode, val states: List<PreviewState>, val previewEntries: List<String> = listOf(entryPoint)) : PreviewParseResult
    data class Failure(val diagnostic: SourceDiagnostic) : PreviewParseResult
}

/** Bounded source interpreter. It never compiles or executes arbitrary Kotlin. */
object ComposeSourceEngine {
    private const val MAX_SOURCE_LENGTH = 64 * 1024
    private const val MAX_NESTING = 32
    private const val MAX_NODES = 512
    private const val MAX_STATES = 32
    private const val MAX_ACTIONS = 32
    private val references = STATE_REFERENCE_PATTERN

    fun parse(source: String, previewName: String? = null): PreviewParseResult {
        if (source.length > MAX_SOURCE_LENGTH) {
            return PreviewParseResult.Failure(SourceDiagnostic("Source exceeds the 64 KiB preview limit", 1, 1))
        }
        return try {
            val selection = selectPreviewSource(source, previewName)
            val parsed = Parser(Lexer(selection.source).tokenize()).parse()
            if (parsed is PreviewParseResult.Success) parsed.copy(entryPoint = selection.selectedName, previewEntries = selection.previewNames) else parsed
        } catch (failure: ParseFailure) {
            PreviewParseResult.Failure(failure.diagnostic)
        } catch (failure: Exception) {
            PreviewParseResult.Failure(SourceDiagnostic("Unable to parse source: ${failure.message ?: failure.javaClass.simpleName}", 1, 1))
        }
    }


    private data class PreviewSelection(val source: String, val selectedName: String, val previewNames: List<String>)
    private data class FunctionDeclaration(val name: String, val body: String, val composable: Boolean, val preview: Boolean, val lineOffset: Int)

    /**
     * Selects the @Preview composable and safely inlines calls to other declared
     * @Composable functions. Function arguments are intentionally not evaluated:
     * preview callbacks such as onLoginClick = { _, _ -> } are inert in this interpreter.
     */
    private fun selectPreviewSource(source: String, requestedName: String?): PreviewSelection {
        val tokens = Lexer(source).tokenize()
        val declarations = ArrayList<FunctionDeclaration>()
        var index = 0
        var pendingComposable = false
        var pendingPreview = false
        while (index < tokens.size - 1) {
            val token = tokens[index]
            // Imports are declarations, not preview-body syntax. Skip the complete
            // line so explicit and wildcard imports (for example runtime.*) are
            // accepted without their package segments affecting annotation state.
            if (token.kind == Kind.IDENTIFIER && token.text in setOf("package", "import")) {
                val declarationLine = token.line
                index++
                while (index < tokens.size - 1 && tokens[index].line == declarationLine) index++
                continue
            }
            if (token.text == "@") {
                index++
                var annotation = tokens.getOrNull(index)?.text.orEmpty()
                index++
                while (tokens.getOrNull(index)?.text == "." &&
                    tokens.getOrNull(index + 1)?.kind == Kind.IDENTIFIER
                ) {
                    annotation = tokens[index + 1].text
                    index += 2
                }
                if (annotation == "Composable") pendingComposable = true
                if (annotation == "Preview") pendingPreview = true
                if (tokens.getOrNull(index)?.text == "(") {
                    index = matchingToken(tokens, index, "(", ")") + 1
                }
                continue
            }
            if (token.text == "fun") {
                val name = tokens.getOrNull(index + 1)?.text
                if (name == null) break
                var cursor = index + 2
                if (tokens.getOrNull(cursor)?.text == "<") {
                    while (cursor < tokens.size && tokens[cursor].text != ">") cursor++
                    cursor++
                }
                if (tokens.getOrNull(cursor)?.text == "(") {
                    cursor = matchingToken(tokens, cursor, "(", ")") + 1
                }
                while (cursor < tokens.size && tokens[cursor].text != "{" && tokens[cursor].kind != Kind.EOF) cursor++
                if (cursor >= tokens.size || tokens[cursor].text != "{") {
                    if (pendingPreview) {
                        throw ParseFailure(
                            SourceDiagnostic(
                                "The @Preview function is missing its body. Use fun ScreenPreview() { Screen() }.",
                                token.line,
                                token.column,
                            ),
                        )
                    }
                    pendingComposable = false
                    pendingPreview = false
                    index++
                    continue
                }
                val close = matchingToken(tokens, cursor, "{", "}")
                val body = source.substring(tokens[cursor].offset + 1, tokens[close].offset)
                declarations += FunctionDeclaration(name, body, pendingComposable, pendingPreview, source.substring(0, token.offset).count { it == '\n' })
                pendingComposable = false
                pendingPreview = false
                index = close + 1
                continue
            }
            // Ignore package/import statements and unrelated top-level tokens.
            if (token.text !in setOf("package", "import") && token.kind == Kind.IDENTIFIER) {
                pendingComposable = false
                pendingPreview = false
            }
            index++
        }

        val composables = declarations.filter { it.composable }
        val previews = composables.filter { it.preview }
        val names = previews.map { it.name }
        if (previews.isEmpty()) {
            throw ParseFailure(
                SourceDiagnostic(
                    "No @Preview entry point found. Add @Preview to a @Composable function to render it.",
                    1,
                    1,
                ),
            )
        }
        val selected = previews.firstOrNull { it.name == requestedName } ?: previews.first()

        val helpers = composables.associateBy { it.name }
        val expandedBody = expandComposableCalls(selected.body, helpers, linkedSetOf(selected.name), 0)
        return PreviewSelection(
            source = "\n".repeat(selected.lineOffset) + "@Composable fun ${selected.name}() { $expandedBody }",
            selectedName = selected.name,
            previewNames = names,
        )
    }

    private fun expandComposableCalls(
        body: String,
        helpers: Map<String, FunctionDeclaration>,
        stack: MutableSet<String>,
        depth: Int,
    ): String {
        if (depth > MAX_NESTING) throw ParseFailure(SourceDiagnostic("Composable helper expansion exceeds $MAX_NESTING levels", 1, 1))
        val tokens = Lexer(body).tokenize()
        val output = StringBuilder()
        var sourceCursor = 0
        var index = 0
        while (index < tokens.size - 1) {
            val token = tokens[index]
            if (token.kind == Kind.IDENTIFIER && token.text == "MaterialTheme") {
                var open = index + 1
                if (tokens.getOrNull(open)?.text == "(") open = matchingToken(tokens, open, "(", ")") + 1
                if (tokens.getOrNull(open)?.text == "{") {
                    val close = matchingToken(tokens, open, "{", "}")
                    output.append(body.substring(sourceCursor, token.offset))
                    val inner = body.substring(tokens[open].offset + 1, tokens[close].offset)
                    output.append(expandComposableCalls(inner, helpers, stack, depth + 1))
                    sourceCursor = tokens.getOrNull(close + 1)?.offset ?: body.length
                    index = close + 1
                    continue
                }
            }
            val helper = helpers[token.text]
            if (token.kind == Kind.IDENTIFIER && helper != null && helper.name !in stack &&
                tokens.getOrNull(index + 1)?.text == "("
            ) {
                val close = matchingToken(tokens, index + 1, "(", ")")
                output.append(body.substring(sourceCursor, token.offset))
                stack.add(helper.name)
                output.append(expandComposableCalls(helper.body, helpers, stack, depth + 1))
                stack.remove(helper.name)
                sourceCursor = tokens.getOrNull(close + 1)?.offset ?: body.length
                index = close + 1
                continue
            }
            index++
        }
        output.append(body.substring(sourceCursor))
        return output.toString()
    }

    private fun matchingToken(tokens: List<Token>, start: Int, open: String, close: String): Int {
        var depth = 0
        for (i in start until tokens.size) {
            if (tokens[i].text == open) depth++
            if (tokens[i].text == close) {
                depth--
                if (depth == 0) return i
            }
        }
        throw ParseFailure(SourceDiagnostic("Expected '$close'", tokens.getOrElse(start) { tokens.last() }.line, tokens.getOrElse(start) { tokens.last() }.column))
    }

    private data class Token(val kind: Kind, val text: String, val value: String, val offset: Int, val line: Int, val column: Int)
    private enum class Kind { IDENTIFIER, NUMBER, STRING, SYMBOL, EOF }

    private class Lexer(private val source: String) {
        private var offset = 0
        private var line = 1
        private var column = 1

        fun tokenize(): List<Token> {
            val result = ArrayList<Token>()
            while (offset < source.length) {
                when {
                    source[offset].isWhitespace() -> whitespace()
                    source[offset] == '/' && peek(1) == '/' -> lineComment()
                    source[offset] == '/' && peek(1) == '*' -> blockComment()
                    source[offset] == '"' -> result += string()
                    source[offset].isDigit() -> result += number()
                    source[offset].isLetter() || source[offset] == '_' -> result += identifier()
                    else -> {
                        result += start(Kind.SYMBOL, source[offset].toString())
                        advance()
                    }
                }
            }
            result += Token(Kind.EOF, "", "", offset, line, column)
            return result
        }

        private fun identifier(): Token {
            val token = start(Kind.IDENTIFIER, "")
            val begin = offset
            while (offset < source.length && (source[offset].isLetterOrDigit() || source[offset] == '_')) advance()
            val value = source.substring(begin, offset)
            return token.copy(text = value, value = value)
        }

        private fun number(): Token {
            val token = start(Kind.NUMBER, "")
            val begin = offset
            while (offset < source.length && source[offset].isDigit()) advance()
            val value = source.substring(begin, offset)
            return token.copy(text = value, value = value)
        }

        private fun string(): Token {
            val token = start(Kind.STRING, "")
            advance()
            val value = StringBuilder()
            while (offset < source.length) {
                val c = source[offset]
                if (c == '"') {
                    advance()
                    return token.copy(text = source.substring(token.offset, offset), value = value.toString())
                }
                if (c == '\\') {
                    advance()
                    if (offset >= source.length) fail("Unterminated escape sequence", token)
                    val escaped = source[offset]
                    value.append(when (escaped) {
                        'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; '"' -> '"'; '\\' -> '\\'; '$' -> '$'
                        else -> fail("Unsupported string escape \\$escaped", token)
                    })
                    advance()
                } else {
                    if (c == '\n' || c == '\r') fail("Newlines in ordinary string literals are not supported", token)
                    value.append(c)
                    advance()
                }
            }
            fail("Unterminated string literal", token)
        }

        private fun lineComment() { while (offset < source.length && source[offset] != '\n') advance() }
        private fun blockComment() {
            val token = start(Kind.SYMBOL, "/*")
            advance(); advance()
            var depth = 1
            while (offset < source.length && depth > 0) {
                if (source[offset] == '/' && peek(1) == '*') { advance(); advance(); depth++ }
                else if (source[offset] == '*' && peek(1) == '/') { advance(); advance(); depth-- }
                else advance()
            }
            if (depth != 0) fail("Unterminated block comment", token)
        }
        private fun whitespace() { while (offset < source.length && source[offset].isWhitespace()) advance() }
        private fun peek(distance: Int) = source.getOrNull(offset + distance)
        private fun advance() {
            if (source[offset] == '\n') { line++; column = 1 } else column++
            offset++
        }
        private fun start(kind: Kind, text: String) = Token(kind, text, text, offset, line, column)
        private fun fail(message: String, token: Token): Nothing =
            throw ParseFailure(SourceDiagnostic(message, token.line, token.column))
    }

    private class Parser(private val tokens: List<Token>) {
        private var index = 0
        private var nodeCount = 0
        private val states = LinkedHashMap<String, PreviewState>()

        fun parse(): PreviewParseResult {
            var entry: String? = null
            var annotated = false
            while (!end()) {
                if (match("@")) {
                    val annotation = identifier("Expected annotation name")
                    if (annotation.text != "Composable") fail("Only @Composable entry points are supported", annotation)
                    annotated = true
                } else if (identifierIs("fun")) {
                    if (!annotated) fail("Entry point must be annotated with @Composable", previous())
                    if (entry != null) fail("Only one top-level function is supported", previous())
                    entry = identifier("Expected composable function name").text
                    expect("(")
                    balanced("(", ")")
                    if (match(":")) fail("Composable entry points must return Unit", previous())
                    expect("{")
                    var root: PreviewNode? = null
                    while (!check("}") && !end()) {
                        if (match(";")) continue
                        if (checkIdentifier("var")) {
                            if (root != null) fail("State declarations must appear before the root layout", current())
                            stateDeclaration()
                        } else if (checkIdentifier("val") || checkIdentifier("fun")) {
                            if (root != null) fail("Only one root layout is supported", current())
                            skipLocalDeclaration()
                        } else {
                            if (root != null) fail("The entry point must contain exactly one root layout", current())
                            root = node(0)
                        }
                    }
                    expect("}")
                    if (!end()) fail("Only one top-level @Composable function is supported", current())
                    val content = root ?: fail("The entry point must contain exactly one root layout", previous())
                    if (content !is PreviewNode.Column && content !is PreviewNode.Row && content !is PreviewNode.Box) {
                        fail("The root must be Column, Row, or Box", previous())
                    }
                    return PreviewParseResult.Success(entry, content, states.values.toList())
                } else if (identifierIs("package") || identifierIs("import")) {
                    while (!end() && current().line == previous().line) advance()
                } else fail("Expected package, import, @Composable, or fun declaration", current())
            }
            fail("No @Composable entry point was found", current())
        }

        /**
         * Local calculations and event-handler functions are app logic, not preview UI.
         * Skip them without evaluating their expressions or invoking callbacks. Stop at
         * the first top-level Compose layout, which is the declarative preview root.
         */
        private fun skipLocalDeclaration() {
            advance() // val or fun
            var parentheses = 0
            var braces = 0
            var brackets = 0
            while (!end()) {
                val token = current()
                if (parentheses == 0 && braces == 0 && brackets == 0 &&
                    token.kind == Kind.IDENTIFIER && token.text in setOf("Column", "Row", "Box")
                ) {
                    return
                }
                when (token.text) {
                    "(" -> parentheses++
                    ")" -> parentheses = (parentheses - 1).coerceAtLeast(0)
                    "{" -> braces++
                    "}" -> {
                        if (braces == 0 && parentheses == 0 && brackets == 0) return
                        braces = (braces - 1).coerceAtLeast(0)
                    }
                    "[" -> brackets++
                    "]" -> brackets = (brackets - 1).coerceAtLeast(0)
                }
                advance()
            }
        }

        private fun stateDeclaration() {
            val declaration = advance()
            if (declaration.text != "var") fail("State variables must use 'var'", declaration)
            val name = identifier("Expected state variable name")
            if (states.containsKey(name.text)) fail("Duplicate state variable '${name.text}'", name)
            if (states.size >= MAX_STATES) fail("Preview supports at most $MAX_STATES state variables", name)
            expectIdentifier("by")
            expectIdentifier("remember")
            expect("{")
            val factory = identifier("Expected mutableIntStateOf or mutableStateOf")
            if (factory.text !in setOf("mutableIntStateOf", "mutableStateOf")) {
                fail("Only mutableIntStateOf and mutableStateOf are supported", factory)
            }
            expect("(")
            val negative = match("-")
            val valueToken = current()
            if (factory.text == "mutableStateOf" && valueToken.kind == Kind.STRING) {
                advance()
                expect(")")
                expect("}")
                states[name.text] = PreviewState(name.text, initialText = valueToken.value)
                return
            }
            if (factory.text == "mutableStateOf" && valueToken.text in setOf("true", "false")) {
                advance()
                expect(")")
                expect("}")
                states[name.text] = PreviewState(name.text, initialBoolean = valueToken.text == "true")
                return
            }
            if (valueToken.kind != Kind.NUMBER) fail("Expected an integer, string, or Boolean initial state value", valueToken)
            advance()
            val number = valueToken.value.toIntOrNull() ?: fail("Integer is out of range", valueToken)
            expect(")")
            expect("}")
            states[name.text] = PreviewState(name.text, if (negative) -number else number)
        }

        private fun node(depth: Int): PreviewNode {
            if (depth > MAX_NESTING) fail("Preview nesting exceeds $MAX_NESTING levels", current())
            val name = identifier("Expected a supported Compose call")
            nodeCount++
            if (nodeCount > MAX_NODES) fail("Preview contains more than $MAX_NODES nodes", name)
            return when (name.text) {
                "Text" -> {
                    expect("(")
                    val argsOpen = index - 1
                    if (identifierIs("text")) expect("=")
                    val value = current()
                    if (value.kind != Kind.STRING) fail("Text currently requires a string literal", value)
                    advance()
                    val argsClose = matchingToken(tokens, argsOpen, "(", ")")
                    index = argsClose + 1
                    references.findAll(value.value).forEach { reference ->
                        val stateName = reference.groupValues[1].ifEmpty { reference.groupValues[2] }
                        if (!states.containsKey(stateName)) fail("Text references unknown state variable '$stateName'", value)
                    }
                    PreviewNode.Text(value.value)
                }
                "OutlinedTextField", "TextField" -> {
                    expect("(")
                    val start = index
                    val close = matchingToken(tokens, start - 1, "(", ")")
                    val args = tokens.subList(start, close).joinToString(" ") { it.text }
                    index = close + 1
                    val label = Regex("""label\s*=\s*\{\s*Text\s*\(\s*"([^"]*)""")
                        .find(args)?.groupValues?.get(1)
                        ?: Regex("""placeholder\s*=\s*\{\s*Text\s*\(\s*"([^"]*)""")
                            .find(args)?.groupValues?.get(1)
                        ?: "Input"
                    val stateName = Regex("""value\s*=\s*([A-Za-z_][A-Za-z0-9_]*)""")
                        .find(args)?.groupValues?.get(1)
                    val visibilityState = Regex("""if\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)""")
                        .find(args)?.groupValues?.get(1)
                    if (stateName != null && !states.containsKey(stateName)) {
                        fail("TextField references unknown state variable '$stateName'", name)
                    }
                    if (visibilityState != null && !states.containsKey(visibilityState)) {
                        fail("Password visibility references unknown state variable '$visibilityState'", name)
                    }
                    PreviewNode.TextField(label, stateName, args.contains("PasswordVisualTransformation"), visibilityState)
                }
                "Column", "Row", "Box" -> {
                    if (match("(")) balanced("(", ")")
                    val children = children(depth)
                    when (name.text) {
                        "Column" -> PreviewNode.Column(children)
                        "Row" -> PreviewNode.Row(children)
                        else -> PreviewNode.Box(children)
                    }
                }
                "Card", "Surface" -> {
                    if (match("(")) {
                        if (!check(")")) fail("${name.text} preview does not accept arguments yet", current())
                        expect(")")
                    }
                    val content = children(depth)
                    if (name.text == "Card") PreviewNode.Card(content) else PreviewNode.Surface(content)
                }
                "Spacer" -> {
                    expect("(")
                    val argsStart = current()
                    balanced("(", ")")
                    val argumentText = tokens.subList(tokens.indexOf(argsStart), index).joinToString("") { it.text }
                    val height = Regex("""height\s*\(\s*(\d+)\s*\.dp\s*\)""").find(argumentText)?.groupValues?.get(1)?.toIntOrNull() ?: 8
                    if (height !in 0..512) fail("Spacer height must be between 0 and 512 dp", argsStart)
                    PreviewNode.Spacer(height)
                }
                "Divider", "HorizontalDivider" -> {
                    if (match("(")) {
                        if (!check(")")) fail("Divider preview does not accept arguments yet", current())
                        expect(")")
                    }
                    PreviewNode.Divider
                }
                "Button", "OutlinedButton", "TextButton" -> {
                    expect("(")
                    val argsOpen = index - 1
                    val argsClose = matchingToken(tokens, argsOpen, "(", ")")
                    var callbackIndex = -1
                    for (candidate in index until argsClose) {
                        if (tokens[candidate].text == "onClick" && tokens.getOrNull(candidate + 1)?.text == "=" &&
                            tokens.getOrNull(candidate + 2)?.text == "{"
                        ) {
                            callbackIndex = candidate + 2
                            break
                        }
                    }
                    if (callbackIndex < 0) fail("Button requires an onClick lambda", current())
                    val callbackClose = matchingToken(tokens, callbackIndex, "{", "}")
                    val actions = ArrayList<PreviewAction>()
                    index = callbackIndex + 1
                    try {
                        while (index < callbackClose) {
                            if (match(";")) continue
                            actions += action()
                            if (actions.size > MAX_ACTIONS) fail("Button supports at most $MAX_ACTIONS actions", previous())
                        }
                    } catch (_: ParseFailure) {
                        // Application callbacks are not executed; unsupported callback bodies become safe no-ops.
                        actions.clear()
                    }
                    index = argsClose + 1
                    val style = when (name.text) { "OutlinedButton" -> ButtonStyle.OUTLINED; "TextButton" -> ButtonStyle.TEXT; else -> ButtonStyle.FILLED }
                    PreviewNode.Button(children(depth), actions, style)
                }
                else -> fail("Unsupported Compose call '${name.text}'. Supported: Column, Row, Box, Text, Button, OutlinedButton, TextButton, Card, Surface, Spacer, Divider", name)
            }
        }

        private fun action(): PreviewAction {
            val state = identifier("Expected a state action such as count++")
            if (!states.containsKey(state.text)) fail("Button action references unknown state variable '${state.text}'", state)
            return when {
                match("+") -> if (match("+")) PreviewAction.Increment(state.text) else {
                    expect("=")
                    PreviewAction.Add(state.text, signedInteger("Expected integer after +="))
                }
                match("-") -> if (match("-")) PreviewAction.Decrement(state.text) else {
                    expect("=")
                    PreviewAction.Subtract(state.text, signedInteger("Expected integer after -="))
                }
                match("=") -> when {
                    current().kind == Kind.STRING -> {
                        val value = advance().value
                        PreviewAction.AssignText(state.text, value)
                    }
                    checkIdentifier("true") || checkIdentifier("false") -> {
                        val value = advance().text == "true"
                        PreviewAction.AssignBoolean(state.text, value)
                    }
                    match("!") -> {
                        val source = identifier("Expected Boolean state after !")
                        if (source.text != state.text) {
                            fail("Boolean toggle must reference the same state variable", source)
                        }
                        PreviewAction.ToggleBoolean(state.text)
                    }
                    else -> PreviewAction.Assign(state.text, signedInteger("Expected integer, string, or Boolean assignment"))
                }
                else -> fail("Use ++, --, += Int, -= Int, or assign an integer, string, or Boolean literal in Button callbacks", current())
            }
        }

        private fun signedInteger(message: String): Int {
            val negative = match("-")
            val token = current()
            if (token.kind != Kind.NUMBER) fail(message, token)
            advance()
            val value = token.value.toIntOrNull() ?: fail("Integer is out of range", token)
            return if (negative) -value else value
        }

        private fun children(depth: Int): List<PreviewNode> {
            if (depth + 1 > MAX_NESTING) fail("Preview nesting exceeds $MAX_NESTING levels", current())
            expect("{")
            val result = ArrayList<PreviewNode>()
            while (!check("}") && !end()) {
                if (match(";")) continue
                if (checkIdentifier("var") || checkIdentifier("val")) {
                    stateDeclaration()
                    continue
                }
                result += node(depth + 1)
            }
            expect("}")
            return result
        }

        private fun balanced(open: String, close: String) {
            var depth = 1
            while (!end() && depth > 0) {
                if (check(open)) depth++
                if (check(close)) depth--
                advance()
            }
            if (depth != 0) fail("Expected '$close'", current())
        }
        private fun identifier(message: String): Token {
            val token = current()
            if (token.kind != Kind.IDENTIFIER) fail(message, token)
            advance()
            return token
        }
        private fun expectIdentifier(value: String) {
            val token = identifier("Expected '$value'")
            if (token.text != value) fail("Expected '$value'", token)
        }
        private fun expect(value: String) { if (!match(value)) fail("Expected '$value'", current()) }
        private fun match(value: String): Boolean { if (!check(value)) return false; advance(); return true }
        private fun checkIdentifier(value: String): Boolean = current().kind == Kind.IDENTIFIER && current().text == value
        private fun identifierIs(value: String): Boolean {
            if (current().kind != Kind.IDENTIFIER || current().text != value) return false
            advance()
            return true
        }
        private fun check(value: String) = current().text == value
        private fun end() = current().kind == Kind.EOF
        private fun current() = tokens[index.coerceAtMost(tokens.lastIndex)]
        private fun previous() = tokens[(index - 1).coerceAtLeast(0)]
        private fun advance(): Token = current().also { if (!end()) index++ }
        private fun fail(message: String, token: Token): Nothing =
            throw ParseFailure(SourceDiagnostic(message, token.line, token.column))
    }
    private class ParseFailure(val diagnostic: SourceDiagnostic) : RuntimeException(diagnostic.message)
}
