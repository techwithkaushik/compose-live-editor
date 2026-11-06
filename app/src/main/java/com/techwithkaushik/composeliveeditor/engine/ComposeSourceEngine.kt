package com.techwithkaushik.composeliveeditor.engine

internal val STATE_REFERENCE_PATTERN = Regex("""\$([A-Za-z_][A-Za-z0-9_]*)|\$\{([A-Za-z_][A-Za-z0-9_]*)\}""")

sealed interface PreviewNode {
    data class Column(val children: List<PreviewNode>) : PreviewNode
    data class Row(val children: List<PreviewNode>) : PreviewNode
    data class Box(val children: List<PreviewNode>) : PreviewNode
    data class Text(val value: String) : PreviewNode
    data class Button(val children: List<PreviewNode>, val actions: List<PreviewAction>) : PreviewNode
}
sealed interface PreviewAction {
    val stateName: String
    data class Increment(override val stateName: String) : PreviewAction
    data class Decrement(override val stateName: String) : PreviewAction
    data class Add(override val stateName: String, val amount: Int) : PreviewAction
    data class Subtract(override val stateName: String, val amount: Int) : PreviewAction
    data class Assign(override val stateName: String, val value: Int) : PreviewAction
}
data class PreviewState(val name: String, val initialValue: Int)
data class SourceDiagnostic(val message: String, val line: Int, val column: Int)
sealed interface PreviewParseResult {
    data class Success(val entryPoint: String, val content: PreviewNode, val states: List<PreviewState>) : PreviewParseResult
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

    fun parse(source: String): PreviewParseResult {
        if (source.length > MAX_SOURCE_LENGTH) {
            return PreviewParseResult.Failure(SourceDiagnostic("Source exceeds the 64 KiB preview limit", 1, 1))
        }
        return try {
            Parser(Lexer(source).tokenize()).parse()
        } catch (failure: ParseFailure) {
            PreviewParseResult.Failure(failure.diagnostic)
        } catch (failure: Exception) {
            PreviewParseResult.Failure(SourceDiagnostic("Unable to parse source: ${failure.message ?: failure.javaClass.simpleName}", 1, 1))
        }
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
                        if (checkIdentifier("var") || checkIdentifier("val")) {
                            if (root != null) fail("State declarations must appear before the root layout", current())
                            stateDeclaration()
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
                fail("Only integer mutable state is supported", factory)
            }
            expect("(")
            val negative = match("-")
            val valueToken = current()
            if (valueToken.kind != Kind.NUMBER) fail("Expected an integer initial state value", valueToken)
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
                    if (identifierIs("text")) expect("=")
                    val value = current()
                    if (value.kind != Kind.STRING) fail("Text currently requires a string literal", value)
                    advance()
                    expect(")")
                    references.findAll(value.value).forEach { reference ->
                        val stateName = reference.groupValues[1].ifEmpty { reference.groupValues[2] }
                        if (!states.containsKey(stateName)) fail("Text references unknown state variable '$stateName'", value)
                    }
                    PreviewNode.Text(value.value)
                }
                "Column", "Row", "Box" -> {
                    if (match("(")) expect(")")
                    val children = children(depth)
                    when (name.text) {
                        "Column" -> PreviewNode.Column(children)
                        "Row" -> PreviewNode.Row(children)
                        else -> PreviewNode.Box(children)
                    }
                }
                "Button" -> {
                    expect("(")
                    val callback = identifier("Button only supports an onClick callback")
                    if (callback.text != "onClick") fail("Button only supports onClick state actions", callback)
                    expect("="); expect("{")
                    val actions = ArrayList<PreviewAction>()
                    while (!check("}") && !end()) {
                        if (match(";")) continue
                        actions += action()
                        if (actions.size > MAX_ACTIONS) fail("Button supports at most $MAX_ACTIONS actions", previous())
                    }
                    expect("}"); expect(")")
                    PreviewNode.Button(children(depth), actions)
                }
                else -> fail("Unsupported Compose call '${name.text}'. Supported: Column, Row, Box, Text, Button", name)
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
                match("=") -> PreviewAction.Assign(state.text, signedInteger("Expected integer assignment"))
                else -> fail("Use ++, --, += Int, -= Int, or = Int in Button callbacks", current())
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
