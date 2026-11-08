package com.techwithkaushik.composeliveeditor.editor

import com.techwithkaushik.composeliveeditor.engine.ComposeSourceEngine
import com.techwithkaushik.composeliveeditor.engine.PreviewParseResult

enum class DiagnosticSeverity { ERROR, WARNING, INFO }
data class EditorDiagnostic(val message: String, val line: Int, val column: Int, val severity: DiagnosticSeverity, val ruleId: String)
data class CompletionItem(val label: String, val insertText: String, val detail: String)

object ComposeEditorLanguageTools {
    private val importPattern = Regex("(?m)^\\s*import\\s+([\\w.]+)(?:\\s+as\\s+\\w+)?\\s*$")
    private val composablePattern = Regex("@Composable\\s+fun\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val trailingWhitespace = Regex("[ \\t]+$", RegexOption.MULTILINE)
    private val todoPattern = Regex("\\b(TODO|FIXME)\\b")
    private val componentUsagePattern = Regex("\\b([A-Z][A-Za-z0-9_]*)\\s*(?=\\(|\\{)")

    private val composePackages = listOf(
        "androidx.compose.foundation.layout" to """Column Row Box Spacer Arrangement BoxScope ColumnScope RowScope WindowInsets WindowInsetsSides PaddingValues fillMaxSize fillMaxWidth fillMaxHeight width height size requiredWidth requiredHeight requiredSize widthIn heightIn sizeIn defaultMinSize padding offset absoluteOffset wrapContentSize wrapContentWidth wrapContentHeight weight horizontalScroll verticalScroll FlowRow FlowColumn""",
        "androidx.compose.foundation" to """Background Border BorderStroke clickable combinedClickable InteractionSource MutableInteractionSource rememberScrollState ScrollState SelectionContainer BasicText BasicTextField Image Indication Canvas""",
        "androidx.compose.foundation.lazy" to """LazyColumn LazyRow LazyListState LazyListScope LazyItemScope rememberLazyListState items item stickyHeader""",
        "androidx.compose.foundation.lazy.grid" to """LazyVerticalGrid LazyHorizontalGrid GridCells LazyGridState rememberLazyGridState GridItemSpan""",
        "androidx.compose.foundation.text" to """KeyboardOptions KeyboardActions KeyboardCapitalization KeyboardType ImeAction BasicTextFieldDefaults""",
        "androidx.compose.material3" to """Text Button OutlinedButton FilledTonalButton ElevatedButton TextButton IconButton FilledIconButton OutlinedIconButton FloatingActionButton SmallFloatingActionButton LargeFloatingActionButton ExtendedFloatingActionButton Card ElevatedCard OutlinedCard Surface Scaffold TopAppBar CenterAlignedTopAppBar MediumTopAppBar LargeTopAppBar BottomAppBar NavigationBar NavigationBarItem NavigationRail NavigationRailItem NavigationDrawerItem ModalNavigationDrawer ModalDrawerSheet PermanentNavigationDrawer DismissibleNavigationDrawer ModalBottomSheet BottomSheetScaffold AlertDialog BasicAlertDialog DatePicker DatePickerDialog DatePickerState rememberDatePickerState TimePicker TimePickerState rememberTimePickerState DropdownMenu DropdownMenuItem ExposedDropdownMenuBox ExposedDropdownMenu ExposedDropdownMenuDefaults Checkbox TriStateCheckbox Switch RadioButton Slider RangeSlider SegmentedButton SegmentedButtonDefaults SingleChoiceSegmentedButtonRow MultiChoiceSegmentedButtonRow CircularProgressIndicator LinearProgressIndicator HorizontalDivider VerticalDivider ListItem ListItemDefaults Snackbar SnackbarHost SnackbarHostState rememberSnackbarHostState TooltipBox PlainTooltip RichTooltip TooltipState rememberTooltipState Badge BadgedBox AssistChip SuggestionChip FilterChip InputChip ElevatedAssistChip ElevatedFilterChip ElevatedInputChip SearchBar DockedSearchBar Tab TabRow PrimaryTabRow SecondaryTabRow ScrollableTabRow TabRowDefaults NavigationSuiteScaffold TextField OutlinedTextField TextFieldDefaults OutlinedTextFieldDefaults MaterialTheme ColorScheme Typography Shapes ProvideTextStyle LocalContentColor LocalTextStyle Icon IconToggleButton FilledIconToggleButton rememberTopAppBarState TopAppBarState TopAppBarDefaults""",
        "androidx.compose.ui" to """Modifier Composable Alignment FocusRequester focusable onFocusChanged onFocusEvent graphicsLayer zIndex drawBehind drawWithContent drawWithCache semantics clearAndSetSemantics testTag""",
        "androidx.compose.ui.graphics" to """Color Brush Shader Path PathEffect ImageBitmap ImageVector SolidColor Stroke StrokeCap StrokeJoin TileMode""",
        "androidx.compose.ui.text" to """AnnotatedString SpanStyle ParagraphStyle TextStyle FontFamily FontWeight FontStyle TextDecoration TextRange TextLayoutResult TextMeasurer buildAnnotatedString withStyle""",
        "androidx.compose.ui.text.font" to """Font FontFamily FontWeight FontStyle FontSynthesis""",
        "androidx.compose.ui.text.style" to """TextAlign TextDecoration TextDirection LineHeightStyle TextOverflow""",
        "androidx.compose.ui.unit" to """Dp IntOffset IntSize Constraints Density TextUnit dp sp""",
        "androidx.compose.ui.layout" to """Layout SubcomposeLayout LayoutCoordinates onGloballyPositioned onSizeChanged ContentScale""",
        "androidx.compose.ui.draw" to """clip shadow alpha rotate scale drawBehind drawWithContent drawWithCache""",
        "androidx.compose.ui.input.pointer" to """pointerInput PointerInputScope detectTapGestures detectDragGestures detectTransformGestures""",
        "androidx.compose.ui.window" to """Dialog DialogProperties Popup PopupProperties""",
        "androidx.compose.ui.platform" to """LocalContext LocalDensity LocalConfiguration LocalView LocalSoftwareKeyboardController LocalClipboardManager LocalUriHandler SoftwareKeyboardController""",
    )

    private val composeSymbols = composePackages.flatMap { (packageName, names) ->
        names.split(Regex("\\s+")).filter(String::isNotBlank).distinct().map { it to "$packageName.$it" }
    }.distinctBy { it.first }.toMap()
    private val symbolsByName = composeSymbols.entries.groupBy({ it.key }, { it.value })
    private val completionCatalog = composeSymbols.entries.sortedBy { it.key }.map { (name, path) -> CompletionItem(name, name, path) }

    fun analyze(source: String): List<EditorDiagnostic> {
        if (source.isEmpty()) return emptyList()
        val diagnostics = ArrayList<EditorDiagnostic>()
        val parsed = ComposeSourceEngine.parse(source)
        if (parsed is PreviewParseResult.Failure) {
            diagnostics += EditorDiagnostic(parsed.diagnostic.message, parsed.diagnostic.line, parsed.diagnostic.column, DiagnosticSeverity.ERROR, "compose-preview")
        }
        val imports = importPattern.findAll(source).map { it.groupValues[1] }.toSet()
        val importedNames = imports.map { it.substringAfterLast('.') }.toSet()
        componentUsagePattern.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            val candidates = symbolsByName[name] ?: return@forEach
            if (name in importedNames || candidates.any { it in imports }) return@forEach
            val before = source.substring(0, match.range.first)
            val line = before.count { it == '\n' } + 1
            val column = match.range.first - source.lastIndexOf('\n', match.range.first - 1)
            val message = if (candidates.size == 1) "Unresolved reference: " + name + ". Import " + candidates.first()
                else "Unresolved reference: " + name + ". Multiple Compose imports are available"
            val rule = if (candidates.size == 1) "missing-import:" + candidates.first()
                else "ambiguous-import:" + candidates.joinToString("|")
            diagnostics += EditorDiagnostic(message, line, column, DiagnosticSeverity.ERROR, rule)
        }
        trailingWhitespace.findAll(source).forEach { match ->
            val line = source.substring(0, match.range.first).count { it == '\n' } + 1
            val column = match.range.first - source.lastIndexOf('\n', match.range.first - 1)
            diagnostics += EditorDiagnostic("Trailing whitespace", line, column, DiagnosticSeverity.WARNING, "style-trailing-whitespace")
        }
        todoPattern.findAll(source).forEach { match ->
            val line = source.substring(0, match.range.first).count { it == '\n' } + 1
            val column = match.range.first - source.lastIndexOf('\n', match.range.first - 1)
            diagnostics += EditorDiagnostic(match.value + " marker should be resolved before release", line, column, DiagnosticSeverity.INFO, "todo-marker")
        }
        importPattern.findAll(source).forEach { match ->
            val name = match.groupValues[1].substringAfterLast('.')
            if (name !in source.substring(match.range.last + 1)) {
                val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                diagnostics += EditorDiagnostic("Unused import: " + name, line, 1, DiagnosticSeverity.WARNING, "unused-import")
            }
        }
        composablePattern.findAll(source).forEach { match ->
            if (source.substring(match.range.last + 1).contains("@Composable")) {
                val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                diagnostics += EditorDiagnostic("Only one preview entry point is supported; found additional composable declarations", line, 1, DiagnosticSeverity.WARNING, "multiple-entry-points")
            }
        }
        return diagnostics.distinct().sortedWith(compareBy<EditorDiagnostic>({ it.line }, { it.column }, { it.severity.ordinal }))
    }

    fun completions(prefix: String): List<CompletionItem> {
        val query = prefix.trim()
        if (query.isEmpty()) return emptyList()
        return completionCatalog.filter { it.label.startsWith(query, ignoreCase = true) }
    }

    fun importCandidates(symbol: String): List<String> = symbolsByName[symbol].orEmpty()
}
