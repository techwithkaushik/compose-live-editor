# ART compiler spike: next implementation gate

This spike must run on a physical Android device, not just a desktop JVM or GitHub's x86_64 runner.

## Decision so far

The app's existing preview screen is deliberately a placeholder. Do not claim that source has been compiled or rendered until the preview host receives executable output and displays it.

The first experiment is the smallest useful pipeline:

1. Run the Kotlin compiler inside ART against one edited `.kt` file.
2. Compile with the Kotlin Compose compiler plugin whose version matches the Kotlin compiler.
3. Use a controlled classpath containing the Android API stubs, Kotlin standard library, and the exact Compose artifacts shipped by the host app.
4. Convert generated class files to DEX with D8.
5. Load the DEX in an isolated class loader and invoke a known `@Composable` entry point from a Compose host.
6. Verify that changing a `Text("...")` literal changes the rendered UI on the next run.
7. Verify a syntax error returns a line and column and never reports a successful preview.

## ART compatibility test matrix

Record the following for each device / ABI:

| Measurement | Required record |
| --- | --- |
| ABI and Android API | e.g. `armeabi-v7a`, API level |
| Compiler start result | pass or full exception / stack trace |
| Compile result | exit code and compiler diagnostics |
| Compose transformation | plugin version and whether transformation succeeded |
| D8 result | exit code and diagnostics |
| Runtime load | class-loader result |
| Preview render | actual rendered text and UI tree |
| Cold / warm compile latency | milliseconds |
| Peak app-process memory | bytes or MB |
| Installed APK size | bytes |
| Failure behavior | no crash; visible actionable diagnostic |

ARM32 is a required gate, not an assumed consequence of setting an ABI filter. A Kotlin-only APK can be architecture-neutral, but that does not prove that the compiler, D8, or their transitive dependencies work within a 32-bit ART process.

## Compatibility and memory rules

- Do not add the complete compiler dependency graph to the production app before the ART spike is measurable.
- Keep the compiler and D8 behind interfaces so a later isolated-process implementation can replace the in-process experiment.
- Bound source size and compile concurrency; cancel stale preview requests when newer source is submitted.
- Never execute the last successful output after the current source fails compilation without clearly marking it as stale.
- Treat compiler diagnostics as data (severity, message, file, line, column), not as a Boolean success flag.
- The Compose compiler plugin is required for ordinary `@Composable` source. Matching the Kotlin / Compose compiler versions is mandatory.
- Do not send source to a server, require Termux, or depend on a desktop.

## Research reference

The open-source CodeAssist IDE has a dedicated `KotlinCompilerArtSpikeTest` that empirically exercises `K2JVMCompiler` on ART and an isolated Compose preview session that interprets a lowered Compose representation. Its implementation demonstrates that on-device compilation and Compose preview are separate engineering problems; it does not establish that its code can be copied into this project or that ARM32 is already supported.

Reference: https://github.com/tyron12233/CodeAssist

## Exit criteria

The spike is complete only when the pipeline renders edited source on an ARM32 device, reports source-located errors, and includes measured size, memory, and latency. Until then the app must continue to show the explicit unavailable state rather than a fake preview.
