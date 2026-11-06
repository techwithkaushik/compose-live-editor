package com.techwithkaushik.composeliveeditor.engine

/**
 * A deliberately constrained, safe interpreter for the first live-preview milestone.
 *
 * It parses source text; it never compiles, loads, or executes user bytecode. Only a small
 * declarative Compose subset is accepted. Every unsupported construct fails with a source
 * location instead of being silently ignored.
 */
sealed interface PreviewNode {
    data class Column(val children: List<PreviewNode>) : PreviewNode
    data class Row(val children: List<PreviewNode>) : PreviewNode
    data class Box(val children: List<PreviewNode>) : PreviewNode
    data class Text(val value: String) : PreviewNode
    data class Button(val children: List<PreviewNode>) : PreviewNode
}

data class SourceDiagnostic(
    val message: String,
    val line: Int,
    val column: Int,
)

sealed interface PreviewParseResult {
    data class Success(val entryPoint: String, val content: PreviewNode) : PreviewParseResult
    data class Failure(val diagnostic: SourceDiagnostic) : PreviewParseResult
}

object ComposeSourceEngine {
    private const val MAX_SOURCE_LENGTH = 64 * 1024
    private const val MAX_NESTING = 32
    private const val MAX_NODES = 512

    fun parse(source: String): PreviewParseResult {
        if (source.length > MAX_SOURCE_LENGTH) {
            return PreviewParseResult.Failure(SourceDiagnostic("Source exceeds the 64 KiB preview limit", 1, 1))
        }
        return try {
            Parser(Lexer(source).tokenize()).parse()
        } catch (failure: ParseFailure) {
            PreviewParseResult.Failure(failure.diagnostic)
        } catch (failure: Throwable) {
            PreviewParseResult.Failure(SourceDiagnostic("Unable to parse source: ${failure.message ?: failure.javaClass.simpleName}", 1, 1))
        }
    }

    private data class Token(
        val kind: Kind,
        val text: String,
        val value: String,
        val offset: Int,
        val line: Int,
        val column: Int,
    )

    private enum class Kind { IDENTIFIER, STRING, SYMBOL, EOF }

    private class Lexer(private val source: String) {
        private var offset = 0
        private var line = 1
        private var column = 1

        fun tokenize(): List<Token> {
            val result = ArrayList<Token>()
            while (offset < source.length) {
                val c = source[offset]
                when {
                    c.isWhitespace() -> advanceWhitespace()
                    c == '/' && peek(1) == '/' -> skipLineComment()
                    c == '/' && peek(1) == '*' -> skipBlockComment()
                    c == '"' -> result += readString()
                    c.isLetter() || c == '_' -> result += readIdentifier()
                    else -> {
                        val start = tokenStart(Kind.SYMBOL, c.toString())
                        advance()
                        result += start
                    }
                }
            }
            result += Token(Kind.EOF, "", "", offset, line, column)
            return result
        }

        private fun readIdentifier(): Token {
            val start = tokenStart(Kind.IDENTIFIER, "")
            val begin = offset
            while (offset < source.length && (source[offset].isLetterOrDigit() || source[offset] == '_')) advance()
            return start.copy(text = source.substring(begin, offset), value = source.substring(begin, offset))
        }

        private fun readString(): Token {
            val start = tokenStart(Kind.STRING, "")
            advance()
            val value = StringBuilder()
            while (offset < source.length) {
                val c = source[offset]
                if (c == '"') {
                    advance()
                    return start.copy(text = source.substring(start.offset, offset), value = value.toString())
                }
                if (c == '\\') {
                    advance()
                    if (offset >= source.length) fail("Unterminated escape sequence", start)
                    val escaped = source[offset]
                    value.append(
                        when (escaped) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            '"' -> '"'
                            '\\' -> '\\'
                            '$' -> '$'
                            else -> fail("Unsupported string escape \\$escaped", start)
                        }
                    )
                    advance()
                } else {
                    if (c == '\n' || c == '\r') fail("Newlines in ordinary string literals are not supported", start)
                    value.append(c)
                    advance()
                }
            }
            fail("Unterminated string literal", start)
        }

        private fun skipLineComment() {
            while (offset < source.length && source[offset] != '\n') advance()
        }

        private fun skipBlockComment() {
            val start = tokenStart(Kind.SYMBOL, "/*")
            advance(); advance()
            var depth = 1
            while (offset < source.length && depth > 0) {
                if (source[offset] == '/' && peek(1) == '*') {
                    advance(); advance(); depth++
                } else if (source[offset] == '*' && peek(1) == '/') {
                    advance(); advance(); depth--
                } else advance()
            }
            if (depth != 0) fail("Unterminated block comment", start)
        }

        private fun advanceWhitespace() { while (offset < source.length && source[offset].isWhitespace()) advance() }
        private fun peek(distance: Int): Char? = source.getOrNull(offset + distance)
        private fun advance() {
            if (source[offset] == '\n') { line++; column = 1 } else column++
            offset++
        }
        private fun tokenStart(kind: Kind, text: String) = Token(kind, text, text, offset, line, column)
        private fun fail(message: String, token: Token): Nothing =
            throw ParseFailure(SourceDiagnostic(message, token.line, token.column))
    }

    private class Parser(private val tokens: List<Token>) {
        private var index = 0
        private var nodeCount = 0

        fun parse(): PreviewParseResult {
            var functionName: String? = null
            while (!atEnd()) {
                if (match("@")) {
                    val annotation = expectIdentifier("Expected annotation name")
                    if (annotation.text != "Composable") {
                        fail("Only @Composable entry points are supported", annotation)
                    }
                    continue
                }
                if (matchIdentifier("fun")) {
                    if (functionName != null) fail("Only one top-level function is supported", previous())
                    functionName = expectIdentifier("Expected composable function name").text
                    expect("(")
                    skipBalanced("(", ")")
                    if (match(":")) fail("Composable entry points must return Unit", previous())
                    if (!check("{")) fail("Expected a function body", current())
                    val body = parseBlock(0)
                    if (body.size != 1) fail("The entry point must contain exactly one root layout", previous())
                    if (!atEnd()) fail("Only one top-level @Composable function is supported", current())
                    val root = body.single()
                    if (root !is PreviewNode.Column && root !is PreviewNode.Row && root !is PreviewNode.Box) {
                        fail("The root must be Column, Row, or Box", previous())
                    }
                    return PreviewParseResult.Success(functionName, root)
                }
                // Package/import declarations and whitespace are accepted before the entry point.
                if (matchIdentifier("package") || matchIdentifier("import")) {
                    while (!atEnd() && current().line == previous().line) advance()
                } else {
                    fail("Expected package, import, @Composable, or fun declaration", current())
                }
            }
            fail("No @Composable entry point was found", current())
        }

        private fun parseBlock(depth: Int): List<PreviewNode> {
            if (depth > MAX_NESTING) fail("Preview nesting exceeds $MAX_NESTING levels", current())
            expect("{")
            val children = ArrayList<PreviewNode>()
            while (!check("}") && !atEnd()) {
                if (match(";")) continue
                children += parseNode(depth + 1)
                if (children.size + nodeCount > MAX_NODES) fail("Preview contains more than $MAX_NODES nodes", previous())
            }
            expect("}")
            return children
        }

        private fun parseNode(depth: Int): PreviewNode {
            val name = expectIdentifier("Expected a supported Compose call")
            nodeCount++
            return when (name.text) {
                "Text" -> {
                    expect("(")
                    if (matchIdentifier("text")) expect("=")
                    val value = current()
                    if (value.kind != Kind.STRING) fail("Text currently requires a string literal, e.g. Text(\"Hello\")", value)
                    advance()
                    skipUntilCallEnd()
                    PreviewNode.Text(value.value)
                }
                "Column", "Row", "Box" -> {
                    if (match("(")) skipBalanced("(", ")")
                    val children = parseBlock(depth)
                    when (name.text) {
                        "Column" -> PreviewNode.Column(children)
                        "Row" -> PreviewNode.Row(children)
                        else -> PreviewNode.Box(children)
                    }
                }
                "Button" -> {
                    if (match("(")) skipBalanced("(", ")")
                    PreviewNode.Button(parseBlock(depth))
                }
                else -> fail("Unsupported Compose call '${name.text}'. Supported: Column, Row, Box, Text, Button", name)
            }
        }

        private fun skipUntilCallEnd() {
            var depth = 0
            while (!atEnd()) {
                if (check(")") && depth == 0) { advance(); return }
                when (current().text) {
                    "(", "{", "[" -> depth++
                    ")", "}", "]" -> depth--
                }
                advance()
            }
            fail("Unterminated call argument list", current())
        }

        private fun skipBalanced(open: String, close: String) {
            var depth = 1
            while (!atEnd() && depth > 0) {
                if (check(open)) depth++
                if (check(close)) depth--
                advance()
            }
            if (depth != 0) fail("Expected '$close'", current())
        }

        private fun expectIdentifier(message: String): Token {
            val token = current()
            if (token.kind != Kind.IDENTIFIER) fail(message, token)
            advance()
            return token
        }
        private fun expect(value: String) {
            if (!match(value)) fail("Expected '$value'", current())
        }
        private fun match(value: String): Boolean {
            if (!check(value)) return false
            advance()
            return true
        }
        private fun matchIdentifier(value: String): Boolean {
            if (current().kind != Kind.IDENTIFIER || current().text != value) return false
            advance()
            return true
        }
        private fun check(value: String) = current().text == value
        private fun atEnd() = current().kind == Kind.EOF
        private fun current() = tokens[index.coerceAtMost(tokens.lastIndex)]
        private fun previous() = tokens[(index - 1).coerceAtLeast(0)]
        private fun advance(): Token = current().also { if (!atEnd()) index++ }
        private fun fail(message: String, token: Token): Nothing =
            throw ParseFailure(SourceDiagnostic(message, token.line, token.column))
    }

    private class ParseFailure(val diagnostic: SourceDiagnostic) : RuntimeException(diagnostic.message)
}
