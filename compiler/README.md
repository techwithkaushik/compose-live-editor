# Embedded Kotlin compiler and active preview engine

The first usable preview milestone is implemented as a bounded on-device source interpreter in `app/.../engine`. It parses a small declarative Compose subset and renders immutable preview nodes with the real Compose runtime.

See [ACTIVE_ENGINE.md](ACTIVE_ENGINE.md) for the exact supported syntax and limitations.

## Why the interpreter is separate from the compiler experiment

The stock Kotlin/JVM compiler spike cannot execute on stock Android ART because `java.lang.management.ManagementFactory` is absent. Do not fake Java SE classes in `java.*`, or report skipped tests as successful compilation.

The current interpreter does not execute arbitrary Kotlin, compile bytecode, or load generated code. It reports unsupported syntax with line/column diagnostics. This bounded implementation is the first real source-to-render path, not full Kotlin compatibility.

## Required next gates

- Safe literal/state expression evaluation and source-defined click actions.
- More Compose components and modifier support.
- Incremental parsing and preview debounce.
- ARM32 physical-device validation with memory/latency measurements.
- A separately proven Android-compatible compiler backend if general Kotlin execution is required.
