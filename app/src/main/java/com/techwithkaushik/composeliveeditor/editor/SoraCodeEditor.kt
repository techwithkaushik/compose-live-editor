package com.techwithkaushik.composeliveeditor.editor

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.diagnostic.Quickfix
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.subscribeAlways
import org.eclipse.tm4e.core.registry.IThemeSource

/** Hosts Sora's native Kotlin highlighting, completion window and diagnostic quick-fix UI. */
@Composable
fun SoraCodeEditor(
    source: String,
    onSourceChange: (String) -> Unit,
    diagnostics: List<EditorDiagnostic>,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentOnSourceChange = rememberUpdatedState(onSourceChange)
    val currentDiagnostics = rememberUpdatedState(diagnostics)
    val currentDarkTheme = rememberUpdatedState(darkTheme)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            KotlinTextMateSupport.initialize(context)
            CodeEditor(context).apply {
                typefaceText = Typeface.MONOSPACE
                textSizePx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    13f,
                    context.resources.displayMetrics,
                )
                setText(source)
                setEditorLanguage(TextMateLanguage.create("source.kotlin", true))
                colorScheme = KotlinTextMateSupport.colorScheme(currentDarkTheme.value)
                setLineNumberEnabled(true)
                setHighlightCurrentLine(true)
                setHighlightCurrentBlock(true)
                setTabWidth(4)
                setDiagnostics(buildDiagnostics(this, currentDiagnostics.value))
                subscribeAlways<ContentChangeEvent> {
                    currentOnSourceChange.value(text.toString())
                }
            }
        },
        update = { editor ->
            if (editor.text.toString() != source) editor.setText(source)
            val desiredScheme = KotlinTextMateSupport.colorScheme(currentDarkTheme.value)
            if (editor.colorScheme !is TextMateColorScheme ||
                KotlinTextMateSupport.isDarkTheme != currentDarkTheme.value
            ) {
                editor.colorScheme = desiredScheme
            }
            editor.setDiagnostics(buildDiagnostics(editor, currentDiagnostics.value))
        },
        onRelease = { editor -> editor.release() },
    )
}

private object KotlinTextMateSupport {
    @Volatile
    private var initialized = false

    @Volatile
    var isDarkTheme: Boolean = false
        private set

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return

        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(context.applicationContext.assets),
        )
        val registry = ThemeRegistry.getInstance()
        listOf("darcula", "quietlight").forEach { name ->
            val path = "textmate/$name.json"
            registry.loadTheme(
                ThemeModel(
                    IThemeSource.fromInputStream(
                        FileProviderRegistry.getInstance().tryGetInputStream(path),
                        path,
                        null,
                    ),
                    name,
                ).apply {
                    isDark = name == "darcula"
                },
            )
        }
        registry.setTheme("quietlight")
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        initialized = true
    }

    @Synchronized
    fun colorScheme(dark: Boolean): TextMateColorScheme {
        val name = if (dark) "darcula" else "quietlight"
        if (isDarkTheme != dark) {
            ThemeRegistry.getInstance().setTheme(name)
            isDarkTheme = dark
        }
        return TextMateColorScheme.create(ThemeRegistry.getInstance())
    }
}

private fun buildDiagnostics(
    editor: CodeEditor,
    diagnostics: List<EditorDiagnostic>,
): DiagnosticsContainer {
    val content = editor.text
    return DiagnosticsContainer().apply {
        val regions = diagnostics.mapNotNull { diagnostic ->
            if (content.lineCount == 0) return@mapNotNull null
            val lineIndex = (diagnostic.line - 1).coerceIn(0, content.lineCount - 1)
            val lineStart = content.getCharIndex(lineIndex, 0)
            val lineLength = content.getColumnCount(lineIndex)
            val start = (lineStart + diagnostic.column - 1).coerceIn(lineStart, lineStart + lineLength)
            val end = (start + 1).coerceAtMost(lineStart + lineLength)
            if (start == end) return@mapNotNull null

            val importCandidates = when {
                diagnostic.ruleId.startsWith("missing-import:") ->
                    listOf(diagnostic.ruleId.removePrefix("missing-import:"))
                diagnostic.ruleId.startsWith("ambiguous-import:") ->
                    diagnostic.ruleId.removePrefix("ambiguous-import:").split("|").filter(String::isNotBlank)
                else -> emptyList()
            }
            val quickfixes = importCandidates.map { qualifiedName ->
                Quickfix(
                    "Import " + qualifiedName.substringAfterLast('.'),
                    fixAction = Runnable { insertImport(editor, qualifiedName) },
                )
            }.ifEmpty { null }

            DiagnosticRegion(
                start,
                end,
                when (diagnostic.severity) {
                    DiagnosticSeverity.ERROR -> DiagnosticRegion.SEVERITY_ERROR
                    DiagnosticSeverity.WARNING -> DiagnosticRegion.SEVERITY_WARNING
                    DiagnosticSeverity.INFO -> DiagnosticRegion.SEVERITY_TYPO
                },
                diagnostic.ruleId.hashCode().toLong(),
                DiagnosticDetail(diagnostic.message, quickfixes = quickfixes),
            )
        }
        addDiagnostics(regions)
    }
}

/** Inserts into the editor's current buffer, never a stale Content captured by a diagnostic. */
private fun insertImport(editor: CodeEditor, qualifiedName: String) {
    val content = editor.text
    val importLine = "import $qualifiedName"
    val currentSource = content.toString()
    if (Regex("(?m)^\\s*${Regex.escape(importLine)}\\s*$").containsMatchIn(currentSource)) return

    val lines = currentSource.lines()
    val packageLine = lines.indexOfFirst { it.trimStart().startsWith("package ") }
    val lastImportLine = lines.indexOfLast { it.trimStart().startsWith("import ") }
    val anchorLine = if (lastImportLine >= 0) lastImportLine else packageLine

    if (anchorLine < 0) {
        content.insert(0, 0, "$importLine\\n")
        return
    }

    val anchorColumn = content.getColumnCount(anchorLine)
    content.insert(anchorLine, anchorColumn, "\\n$importLine")
}
