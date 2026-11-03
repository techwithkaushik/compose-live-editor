# On-device Compose execution: implementation plan

## Goal

The Run Preview action must execute the user's edited source locally on the Android device and display the resulting Compose UI. It must not send code to a server, use Termux, or substitute a hard-coded preview.

## What the first CI milestone proves

- The editor app builds as an Android APK.
- The source buffer and Editor → Preview → Editor navigation work.
- The initial source sample includes the imports required by the Compose APIs it uses.

It does not prove runtime compilation or user-code execution.

## Latest ART experiment (2026-11-02)

The compiler class can be loaded and constructed in the instrumentation APK, but invoking it fails before compilation starts:

`NoClassDefFoundError: java.lang.management.ManagementFactory`.

This class belongs to the Java SE management APIs and is not available on Android's boot class path. This is an environment/API compatibility blocker, not a Kotlin syntax diagnostic. The smoke tests explicitly skip compile assertions when this API is missing; skipped tests are not evidence that compilation works. CI should report the build, class-loading probe, and instrumentation status, while the actual compile gate remains blocked until an Android-compatible compiler adapter or alternate execution strategy is implemented. Do not add a fake `java.lang.management` class under `java.*` or mark the compiler as working based on class construction alone.

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

## Immediate next steps

1. Inspect the exact Kotlin compiler call path that touches `ManagementFactory` and determine whether the dependency can be isolated behind an Android-safe adapter without patching platform packages.
2. If it cannot, prototype an Android-compatible compilation/interpretation path in a separate module and keep it out of the production APK until the proof-of-concept gate passes.
3. Keep unsupported runtime, compiler failure, and successful compilation as three separate outcomes in instrumentation reports.

## References

- Kotlin compiler plugins: https://kotlinlang.org/docs/compiler-plugins-overview.html
- Compose compiler migration and matching Kotlin/Compose plugin versions: https://kotlinlang.org/docs/compose-compiler-migration-guide.html
- Android on-device IDE research reference (CodeAssist): https://github.com/tyron12233/CodeAssist
