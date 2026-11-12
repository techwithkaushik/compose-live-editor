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
    private val componentUsagePattern = Regex("\\b([A-Za-z_][A-Za-z0-9_]*)\\b")

    private val composePackages = listOf(
        "androidx.compose.foundation.layout" to """Column Row Box Spacer Arrangement BoxScope ColumnScope RowScope WindowInsets WindowInsetsSides PaddingValues fillMaxSize fillMaxWidth fillMaxHeight width height size requiredWidth requiredHeight requiredSize widthIn heightIn sizeIn defaultMinSize padding offset absoluteOffset wrapContentSize wrapContentWidth wrapContentHeight weight horizontalScroll verticalScroll FlowRow FlowColumn""",
        "androidx.compose.foundation" to """BorderStroke clickable combinedClickable Image Indication Canvas background border rememberScrollState ScrollState""",
        "androidx.compose.foundation.interaction" to """InteractionSource MutableInteractionSource""",
        "androidx.compose.foundation.text.selection" to """SelectionContainer""",
        "androidx.compose.foundation.lazy" to """LazyColumn LazyRow LazyListState LazyListScope LazyItemScope rememberLazyListState items item stickyHeader""",
        "androidx.compose.foundation.lazy.grid" to """LazyVerticalGrid LazyHorizontalGrid GridCells LazyGridState rememberLazyGridState GridItemSpan""",
        "androidx.compose.foundation.text" to """KeyboardOptions KeyboardActions KeyboardCapitalization KeyboardType ImeAction BasicTextFieldDefaults BasicText BasicTextField""",
        "androidx.compose.material3" to """Text Button OutlinedButton FilledTonalButton ElevatedButton TextButton IconButton FilledIconButton OutlinedIconButton FloatingActionButton SmallFloatingActionButton LargeFloatingActionButton ExtendedFloatingActionButton Card ElevatedCard OutlinedCard Surface Scaffold TopAppBar CenterAlignedTopAppBar MediumTopAppBar LargeTopAppBar BottomAppBar NavigationBar NavigationBarItem NavigationRail NavigationRailItem NavigationDrawerItem ModalNavigationDrawer ModalDrawerSheet PermanentNavigationDrawer DismissibleNavigationDrawer ModalBottomSheet BottomSheetScaffold AlertDialog BasicAlertDialog DatePicker DatePickerDialog DatePickerState rememberDatePickerState TimePicker TimePickerState rememberTimePickerState DropdownMenu DropdownMenuItem ExposedDropdownMenuBox ExposedDropdownMenu ExposedDropdownMenuDefaults Checkbox TriStateCheckbox Switch RadioButton Slider RangeSlider SegmentedButton SegmentedButtonDefaults SingleChoiceSegmentedButtonRow MultiChoiceSegmentedButtonRow CircularProgressIndicator LinearProgressIndicator HorizontalDivider VerticalDivider ListItem ListItemDefaults Snackbar SnackbarHost SnackbarHostState TooltipBox PlainTooltip RichTooltip TooltipState rememberTooltipState Badge BadgedBox AssistChip SuggestionChip FilterChip InputChip ElevatedAssistChip ElevatedFilterChip ElevatedInputChip SearchBar DockedSearchBar Tab TabRow PrimaryTabRow SecondaryTabRow ScrollableTabRow TabRowDefaults TextField OutlinedTextField TextFieldDefaults OutlinedTextFieldDefaults MaterialTheme ColorScheme Typography Shapes ProvideTextStyle LocalContentColor LocalTextStyle Icon IconToggleButton FilledIconToggleButton rememberTopAppBarState TopAppBarState TopAppBarDefaults""",
        "androidx.compose.runtime" to """Composable remember key rememberCoroutineScope LaunchedEffect DisposableEffect SideEffect derivedStateOf mutableStateOf mutableIntStateOf mutableLongStateOf mutableFloatStateOf mutableDoubleStateOf mutableStateListOf mutableStateMapOf State MutableState SnapshotStateList SnapshotStateMap getValue setValue produceState rememberUpdatedState rememberSaveable""",
        "androidx.compose.animation" to """AnimatedVisibility AnimatedContent Crossfade animateContentSize animateColorAsState animateDpAsState animateFloatAsState animateIntAsState animateOffsetAsState EnterTransition ExitTransition ContentTransform""",
        "androidx.compose.animation.core" to """spring tween keyframes repeatable infiniteRepeatable""",
        "androidx.compose.material.icons" to """Icons""",
        "androidx.compose.material.icons.filled" to """Add Menu Home Search Close Delete Edit ArrowBack ArrowForward Check Clear MoreVert MoreHoriz Settings Favorite FavoriteBorder Person AccountCircle Notifications Info Warning Error Visibility VisibilityOff ExpandMore ExpandLess ChevronLeft ChevronRight ArrowDropDown ArrowDropUp Done""",
        "androidx.compose.material.icons.outlined" to """Add Menu Home Search Close Delete Edit ArrowBack ArrowForward Check Settings Favorite Person AccountCircle Notifications Info Warning Visibility ExpandMore""",
        "androidx.compose.ui" to """Modifier Alignment""",
        "androidx.compose.ui.focus" to """FocusRequester focusRequester onFocusChanged onFocusEvent""",
        "androidx.compose.foundation" to """focusable""",
        "androidx.compose.ui.graphics" to """Color Brush Shader Path PathEffect ImageBitmap ImageVector SolidColor Stroke StrokeCap StrokeJoin TileMode graphicsLayer""",
        "androidx.compose.ui.text" to """AnnotatedString SpanStyle ParagraphStyle TextStyle FontFamily FontWeight FontStyle TextDecoration TextRange TextLayoutResult TextMeasurer buildAnnotatedString withStyle""",
        "androidx.compose.ui.text.font" to """Font FontFamily FontWeight FontStyle FontSynthesis""",
        "androidx.compose.ui.text.style" to """TextAlign TextDecoration TextDirection LineHeightStyle TextOverflow""",
        "androidx.compose.ui.unit" to """Dp IntOffset IntSize Constraints Density TextUnit dp sp""",
        "androidx.compose.ui.layout" to """Layout SubcomposeLayout LayoutCoordinates onGloballyPositioned onSizeChanged""",
        "androidx.compose.ui.draw" to """clip shadow alpha rotate scale drawBehind drawWithContent drawWithCache""",
        "androidx.compose.ui.zIndex" to """zIndex""",
        "androidx.compose.ui.semantics" to """semantics clearAndSetSemantics testTag""",
        "androidx.compose.ui.input.pointer" to """pointerInput PointerInputScope detectTapGestures detectDragGestures detectTransformGestures""",
        "androidx.compose.ui.window" to """Dialog DialogProperties Popup PopupProperties""",
        "androidx.compose.ui.platform" to """LocalContext LocalDensity LocalConfiguration LocalView LocalSoftwareKeyboardController LocalClipboardManager LocalUriHandler SoftwareKeyboardController""",
    )

    private val composeSymbols = composePackages.flatMap { (packageName, names) ->
        names.split(Regex("\\s+")).filter(String::isNotBlank).distinct().map { it to "$packageName.$it" }
    }
    private val symbolsByName = composeSymbols.groupBy({ it.first }, { it.second })
    private val completionCatalog = composeSymbols.distinctBy { it.first }.sortedBy { it.first }
        .map { (name, path) -> CompletionItem(name, completionSnippet(name), path) } +
        CompletionItem("@Composable", "@Composable", "androidx.compose.runtime.Composable")

    private fun completionSnippet(name: String): String = when (name) {
        "Column", "Row", "Box" -> "$name {\n    \n}"
        "Button", "OutlinedButton", "TextButton" -> "$name(onClick = { }) {\n    Text(\"Action\")\n}"
        "Card", "Surface" -> "$name {\n    Text(\"Content\")\n}"
        "Text" -> "Text(\"Hello\")"
        "Spacer" -> "Spacer(modifier = Modifier.height(8.dp))"
        "HorizontalDivider" -> "HorizontalDivider()"
        else -> name
    }

    private fun maskCommentsAndStrings(source: String): String {
        val nonCode = Regex("(?s)/\\*.*?\\*/|//[^\\n]*|\\\"\\\"\\\".*?\\\"\\\"\\\"|\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|'(?:\\\\.|[^'\\\\])*'")
        return nonCode.replace(source) { match ->
            match.value.map { character -> if (character == '\n') '\n' else ' ' }.joinToString("")
        }
    }

    fun analyze(source: String): List<EditorDiagnostic> {
        if (source.isEmpty()) return emptyList()
        val diagnostics = ArrayList<EditorDiagnostic>()
        val parsed = ComposeSourceEngine.parse(source)
        if (parsed is PreviewParseResult.Failure) {
            diagnostics += EditorDiagnostic(parsed.diagnostic.message, parsed.diagnostic.line, parsed.diagnostic.column, DiagnosticSeverity.ERROR, "compose-preview")
        }
        val imports = importPattern.findAll(source).map { it.groupValues[1] }.toSet()
        val importedNames = imports.map { it.substringAfterLast('.') }.toSet()
        componentUsagePattern.findAll(maskCommentsAndStrings(source)).forEach { match ->
            val name = match.groupValues[1]
            val candidates = symbolsByName[name] ?: return@forEach
            if (name in importedNames || candidates.any { it in imports }) return@forEach
            val lineStart = source.lastIndexOf('\n', match.range.first - 1) + 1
            val linePrefix = source.substring(lineStart, match.range.first)
            if (Regex("\\b(fun|class|interface|object|typealias)\\s+$name\\s*$").containsMatchIn(linePrefix)) return@forEach
            if (linePrefix.trimStart().startsWith("import ")) return@forEach
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
        return diagnostics.distinct().sortedWith(compareBy<EditorDiagnostic>({ it.line }, { it.column }, { it.severity.ordinal }))
    }

    fun completions(prefix: String): List<CompletionItem> {
        val query = prefix.trim()
        if (query.isEmpty()) return completionCatalog
        return completionCatalog.filter { it.label.startsWith(query, ignoreCase = true) }
    }

    fun importCandidates(symbol: String): List<String> = symbolsByName[symbol].orEmpty()
}
