# Active preview engine status

## Preview entry-point contract

Only functions carrying both `@Preview` and `@Composable` are preview entry points. A plain `@Composable` function is never selected as an implicit preview. Multiple preview functions are discovered and can be selected independently. Other composables are reusable source declarations, not preview entry points.

## Current implementation limitation

The active engine remains a bounded Kotlin source interpreter in `app/.../engine`. It parses a small subset and renders immutable preview nodes using the app's Compose runtime. It does **not** compile the user's Kotlin source, resolve imports against Gradle dependencies, execute arbitrary Kotlin, or provide Android Studio-equivalent preview behaviour.

Supported syntax is intentionally limited and documented by the parser/tests. Unsupported syntax must produce a source-located diagnostic. Never replace unsupported user source with a canned preview or claim that imports have been compiled.

## Android Studio-equivalent target architecture

1. Parse the complete project source set and discover annotated preview declarations.
2. Resolve imports, symbols and dependencies from the project's Gradle configuration.
3. Compile with a Kotlin compiler and matching Compose compiler plugin.
4. Convert compatible output to DEX using D8.
5. Load and invoke preview entry points in an isolated Android runtime host that uses the actual Compose libraries shipped by the app.
6. Render each preview with real Compose state, recomposition, modifiers, themes and parameter providers.
7. Map compiler and runtime diagnostics back to source locations.

## Mandatory compiler backend gates

The existing stock Kotlin/JVM compiler experiment cannot run as-is on stock Android ART because ART lacks `java.lang.management.ManagementFactory`. It is not a production compiler backend. Do not add its complete dependency graph to the APK before the following are demonstrated on physical ARM32 hardware:

- Compiler starts and compiles an edited Kotlin file in ART.
- Matching Compose compiler transformation succeeds.
- D8 converts output successfully.
- Isolated class loading and composable invocation succeed.
- Editing a string in the source changes the rendered preview.
- Syntax/type errors produce accurate diagnostics and never display stale output as current.
- Cold/warm latency, peak memory and APK size are measured.

Keep compilation and execution behind interfaces so the backend can move to an isolated process. Do not use Termux, remote source compilation, fake Java SE classes, or weakened SELinux as shortcuts.


## Safe interactive state subset

The interpreter supports remember with mutableIntStateOf or mutableStateOf for integer, string, and Boolean values, text interpolation from declared state, and controlled TextField/OutlinedTextField bindings to declared string state. Button callbacks are never executed as Kotlin. Only these parsed state operations are translated into explicit preview actions:

- Integer increment/decrement and += / -=.
- Integer, string, and Boolean literal assignment.
- Boolean self-toggle (visible = !visible).

Unknown text-field state bindings are rejected with a source diagnostic. Unsupported callback code remains an inert no-op; this is not arbitrary callback execution or general Kotlin expression evaluation.


## Compiler pipeline contract

`app/.../compiler/PreviewCompilationPipeline.kt` now defines the strict compiler-backed flow:

1. `KotlinComposeCompilerBackend` compiles workspace source with the matching Compose compiler transformation.
2. `AndroidDexBackend` converts the compiler artifact to DEX for the requested Android API floor.
3. `AndroidPreviewRuntimeBackend` loads the DEX and resolves the selected preview entry point.

The pipeline validates workspace-relative source paths, carries source diagnostics, stops on the first failed stage, and returns an explicit unavailable result when a backend is not installed. It intentionally has no interpreter fallback. These are interfaces and orchestration, not concrete compiler/D8/runtime implementations; the existing editor Preview action still uses the bounded interpreter.
