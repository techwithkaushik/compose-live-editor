# On-device Compose execution: implementation plan

## Current state

The app has an active, bounded source interpreter. The Run Preview action parses the current editor buffer and renders the resulting node tree with the real Compose runtime. Supported calls and limits are documented in [ACTIVE_ENGINE.md](ACTIVE_ENGINE.md).

This is a genuine source-to-UI path, but it is not arbitrary Kotlin execution. Unsupported syntax produces a diagnostic and does not display a canned preview.

## Why the stock Kotlin compiler is not the active engine

The ART experiment cannot execute the stock Kotlin/JVM compiler because Android ART lacks `java.lang.management.ManagementFactory`. The spike's compile assertions skip on that runtime. Do not fake Java SE classes in `java.*` or treat skipped tests as success.

## Next implementation gates

1. Add safe literal/state expression evaluation and a restricted callback action model.
2. Add modifier support and commonly used Material3 components.
3. Add incremental parsing, cancellation, and edit-to-preview debounce.
4. Add source maps and richer diagnostics as grammar grows.
5. Evaluate a separately validated Android-compatible compiler backend for arbitrary Kotlin only if the interpreter's supported language ceiling becomes insufficient.
6. Validate the release APK on a physical `armeabi-v7a` device; measure memory, latency, and package size.

## Acceptance rule

A preview is successful only when it is derived from the current edited source and rendered by the real Compose runtime. Unsupported syntax must be reported, never silently ignored or replaced by a canned sample.
