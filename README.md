# Compose Live Editor

Android-first Kotlin/Jetpack Compose source editor targeting Android API 26+ and ARM32-compatible builds.

## Live preview

The preview engine is a bounded Kotlin/Compose source interpreter. It renders parsed nodes using the app's real Jetpack Compose Material 3 runtime; it does not compile or execute arbitrary Kotlin.

Supported preview constructs:
- One top-level `@Composable fun Name() { ... }` entry point.
- Package and import declarations.
- `Column`, `Row`, and `Box` layout containers.
- `Card` and `Surface` containers.
- `Text("literal")` and integer state interpolation.
- `Button`, `OutlinedButton`, and `TextButton` with bounded integer state actions.
- `Spacer(modifier = Modifier.height(n.dp))` for integer heights from 0 to 512 dp.
- `Divider()` and `HorizontalDivider()`.
- Integer state declared with `remember { mutableIntStateOf(n) }` or `remember { mutableStateOf(n) }`.

Unsupported Kotlin constructs are rejected with source-located preview diagnostics. Limits: 64 KiB source, 32 layout nesting levels, 512 nodes, 32 state variables, and 32 actions per button.

## Editor assistance

- Light and dark Material 3 editor themes.
- Compose component completion palette with insertable snippets.
- Live parser diagnostics, trailing-whitespace warnings, TODO/FIXME notices, and basic unused-import checks.
- Diagnostic counts in the editor status bar.

The current completion and diagnostics layer is a local Compose-aware editor assistant, not a Kotlin Language Server Protocol implementation. Full Kotlin semantic analysis, compiler-backed diagnostics, cursor-aware completion, syntax highlighting, and formatting require further editor infrastructure.

## Build and verification

- JDK 17
- Kotlin DSL and Gradle Version Catalog
- Android min SDK 26
- CI runs JVM unit tests and builds/signs the release APK using repository signing secrets.
- The preview parser and editor assistance have JVM unit tests.

Run `gradle :app:testDebugUnitTest :app:assembleDebug` for local verification. Release CI uses `gradle :app:assembleRelease`.
