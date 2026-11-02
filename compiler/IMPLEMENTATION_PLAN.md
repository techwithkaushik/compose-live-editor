# On-device Compose execution: implementation plan

## Goal

The Run Preview action must execute the user's edited source locally on the Android device and display the resulting Compose UI. It must not send code to a server, use Termux, or substitute a hard-coded preview.

## What the first CI milestone proves

- The editor app builds as an Android APK.
- The source buffer and Editor → Preview → Editor navigation work.
- The initial source sample includes the imports required by the Compose APIs it uses.

It does not prove runtime compilation or user-code execution.

## Technical options to test

### Option 1: Kotlin compile → Compose transform → DEX → load

1. Invoke a matching Kotlin/JVM compiler in-process against the edited source.
2. Apply the matching Kotlin Compose compiler plugin; it rewrites @Composable functions and is not optional for ordinary Compose source.
3. Compile against the exact Compose Runtime/UI/Material3 and Android API classpath available to the app.
4. Convert generated JVM bytecode to DEX using D8.
5. Load the generated DEX and invoke the agreed entry point from the preview host.
6. Convert compiler output into actionable line/column diagnostics.

Risks to measure early: Kotlin compiler artifact size, compiler APIs and dependencies on Android/ART, memory pressure on 32-bit processes, D8 latency, classloader boundaries, and dynamic-code-loading policy. Do not add all compiler dependencies to the main APK until a minimal on-device spike proves these assumptions.

### Option 2: Interpret a supported Compose subset

Parse and resolve a deliberately small Kotlin subset, then interpret it while invoking precompiled Compose library functions. Unsupported syntax must produce an explicit diagnostic. This can avoid repeated code generation, but a correct interpreter must respect Compose's compiler-generated calling convention, slot-table groups, state, and recomposition. It is not equivalent to rendering arbitrary Kotlin.

## First proof-of-concept gate

Use the current starter source and prove these in order:

1. Compile one file containing @Composable fun MyScreen().
2. Render Text and Column in the actual Preview screen.
3. Edit the displayed string and verify the next preview reflects the edit.
4. Show a syntax error with a source location instead of opening a false-success preview.
5. Repeat on a 32-bit ARM device/emulator and record APK size, peak memory, and compile latency.

Only after this gate should the compiler/interpreter dependencies become part of the app.

## References

- Kotlin compiler plugins: https://kotlinlang.org/docs/compiler-plugins-overview.html
- Compose compiler migration and matching Kotlin/Compose plugin versions: https://kotlinlang.org/docs/compose-compiler-migration-guide.html
- Android on-device IDE research reference (CodeAssist): https://github.com/tyron12233/CodeAssist
