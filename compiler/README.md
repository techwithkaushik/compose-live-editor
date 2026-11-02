# Embedded compiler milestone

This directory documents the future in-app Kotlin + Jetpack Compose compilation engine. No compiler dependency is bundled in the first editor milestone.

## Required proof before implementation is considered complete

- Compile a user-edited Kotlin source buffer on-device without Termux or a remote service.
- Apply the matching Compose compiler plugin.
- Load generated code safely in the Android process or a dedicated isolated process.
- Render the compiled Composable in the preview screen.
- Show compiler diagnostics rather than a fake preview on failure.
- Demonstrate compatibility on armeabi-v7a hardware or a 32-bit ARM emulator.
- Measure compiler APK size, peak memory, compilation latency, and supported Kotlin/Compose syntax.

A normal Android Compose runtime does not compile arbitrary Kotlin source. DexClassLoader alone is not a source compiler. Investigate compiler artifact availability and runtime compatibility before adding dependencies.
