# Embedded Kotlin compiler and active preview engine

The app currently has a bounded source interpreter and a separately defined compiler-backed pipeline contract.

See [ACTIVE_ENGINE.md](ACTIVE_ENGINE.md) for the active interpreter limits and [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for the remaining compiler gates.

## Current implementation

- The bounded interpreter parses a small declarative Compose subset and renders nodes with the real Compose runtime.
- Typed state updates and text-field bindings are supported only for the documented safe subset.
- `PreviewCompilationPipeline` defines explicit Kotlin + Compose compile, D8 conversion, and Android runtime loading stages.
- Each stage has a typed result and diagnostics. Missing backends are reported as unavailable.
- A failed compiler stage stops the pipeline. It never silently falls back to the interpreter and reports a false compiler success.

## Why there is no production compiler backend yet

The stock Kotlin/JVM compiler experiment cannot execute on stock Android ART because `java.lang.management.ManagementFactory` is absent. Do not fake Java SE classes in `java.*`, report skipped tests as successful compilation, or bundle the full compiler dependency graph before proving ART compatibility.

The pipeline contracts are orchestration only. No Android-compatible Kotlin compiler, Compose compiler transformation, D8 implementation, or generated-code runtime loader is registered yet. Therefore the app's regular Preview action continues to use the bounded interpreter.

## Remaining gates

- Prove an Android-compatible Kotlin compiler and matching Compose compiler plugin on physical ARM32 ART.
- Implement and validate D8 conversion against the Android platform and host Compose classpath.
- Implement isolated DEX loading and safe invocation of transformed composable entry points.
- Resolve workspace source sets, Gradle dependencies, imports, symbols and module classpaths.
- Add cancellation, stale-result rejection and incremental compilation.
- Measure cold/warm latency, peak memory and APK size on the target device.
