# Android Studio-equivalent Compose preview implementation plan

## Current state

Preview discovery now requires explicit `@Preview` plus `@Composable`. Plain composables are not implicitly previewed. The active renderer is still a bounded source interpreter and is not universal Kotlin/Compose execution.

## Work sequence

1. **Safe interactive interpreter gate:** typed state declarations, text-field bindings, literal assignments and Boolean self-toggle are implemented with regression tests.
2. **Pipeline contract:** explicit Kotlin + Compose compile, D8 and runtime-load interfaces, typed diagnostics, stage failures and no-fallback behavior are implemented. Concrete backends are not installed.
3. **Compiler feasibility gate:** validate an Android-compatible Kotlin compiler and matching Compose compiler plugin on physical ARM32 ART. Measure startup, memory and output.
4. **Project model:** resolve all Kotlin source files, Gradle dependencies, imports, symbols and module classpaths.
5. **Compilation:** compile the selected project source set with Compose transformation and actionable diagnostics.
6. **DEX pipeline:** run D8 against Android APIs and the exact Compose artifacts used by the host app.
7. **Isolated execution:** run generated DEX in a dedicated isolated-UID Android service; transfer immutable DEX and required inputs through an explicit IPC contract; expose only a narrow preview-rendering protocol. A same-process class loader is not a sandbox.
8. **Real preview host:** render with the actual Compose runtime, including nested/custom composables, Material 2/3, modifiers, state, recomposition, themes and preview parameters.
9. **Incremental updates:** cancel stale builds, debounce edits, cache dependency/compiler outputs and refresh previews safely.
10. **Validation:** run a fixture matrix covering arbitrary imports, multiple preview functions, helper composables, state, callbacks, parameter providers, compile errors and runtime exceptions on ARM32 devices.

## Workspace file manager

The app-private workspace drawer supports folder navigation, file opening, create file/folder, and long-press rename/delete. Editor changes are autosaved to the active workspace file.

## Acceptance criteria

- Only `@Preview @Composable` declarations are previewed.
- No predefined component whitelist is required for actual preview rendering.
- Valid project imports and custom composables resolve through the project compiler classpath.
- Preview output is generated from the current source and executed by the real Compose runtime.
- Compiler errors, runtime failures and stale previews are clearly distinguished.
- ARM32 physical-device measurements are available before enabling the compiler backend by default.

A parser-only rendering pass is not considered completion of the universal preview feature.
