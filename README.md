# Compose Live Editor

A lightweight Android-first Kotlin/Jetpack Compose editor, designed to target 32-bit ARM (`armeabi-v7a`) devices from the start.

## Current scope

- Launch directly into a single editable Kotlin source buffer.
- Provide a **Run Preview** action in the top app bar.
- Keep editor text in memory when navigating to and from the preview screen.
- No file picker, file explorer, project browser, or external compiler.
- Build and validate the Android app in GitHub Actions.

## Important implementation status

**The app is not yet a functional Kotlin/Compose live preview.** The current preview screen is an explicit unavailable-state placeholder. It does not compile or execute the user's source.

The latest successful GitHub Actions run (#33, commit `58da370`) proves that the debug and instrumentation APKs build and that the x86_64 Android ART instrumentation runner completes. It does not prove Kotlin compilation: the runtime capability probe reports that `java.lang.management.ManagementFactory` is unavailable on Android ART, and the two compiler assertions are skipped on that runtime. A green packaging/instrumentation run must not be described as a successful compiler test.

The remaining implementation gates are an Android-compatible Kotlin execution backend, matching Compose compiler transformation (or a sound Compose interpreter), actual preview rendering with the host's Compose runtime, source-located diagnostics, and ARM32 device validation. The implementation must remain on-device: no remote compilation service, no Termux dependency, and no hard-coded preview in place of the edited source.

## Build

Open this repository in Android Studio and run the `assembleDebug` Gradle task. The project uses Kotlin DSL and a Gradle Version Catalog.

## Target

- Android min SDK: 26 (API 26 / Android 8.0); this is the current Gradle configuration.
- Primary device ABI: `armeabi-v7a` (32-bit ARM).
- CI JDK: 17.
