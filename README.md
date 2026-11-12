# Compose Live Editor

Android-first Kotlin/Jetpack Compose source editor targeting Android API 26+ and ARM32-compatible builds.

## Live preview status

The current preview renderer is an explicitly limited source interpreter, **not** an Android Studio-equivalent Compose preview engine. It must never be described as universal Kotlin/Compose execution.

Preview discovery follows the Compose convention: only a function annotated with both `@Preview` and `@Composable` is an entry point. Plain `@Composable` functions are reusable helpers and are not previewed automatically. Multiple annotated entry points are discoverable and selectable.

The current interpreter still understands only a bounded subset of Kotlin/Compose syntax. It does not resolve arbitrary imports, compile project source, execute custom composables, or provide complete Compose runtime semantics. Unsupported source must show a diagnostic rather than a fabricated rendering.

### Required path to Android Studio-equivalent behaviour

- Kotlin frontend / symbol resolution for the complete project source set.
- Dependency and import resolution from the project's Gradle model.
- Matching Kotlin and Compose compiler plugin pipeline.
- Android-compatible compilation and DEX conversion.
- Isolated loading and invocation of compiled preview entry points inside a real Compose host.
- Multiple preview configurations, parameter providers, themes, state/recomposition and source-mapped diagnostics.
- Measured physical-device validation on ARM32 before shipping the compiler backend.

The stock Kotlin/JVM compiler experiment is not a validated Android runtime backend. Do not add its full dependency graph to the production APK until ART compatibility, D8, class loading, memory, latency and APK size are demonstrated on target hardware.

## Workspace file manager

The Files drawer manages the app-private `files/workspace` directory. Tap folders to navigate, tap files to open them in the editor, and long-press an item to rename or delete it. The drawer also supports creating files and folders. Open-file edits are autosaved back to the selected workspace file.

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
