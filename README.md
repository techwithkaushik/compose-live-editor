# Compose Live Editor

A lightweight Android-first Kotlin/Jetpack Compose editor, designed to target 32-bit ARM (`armeabi-v7a`) devices from the start.

## Current scope

- Launch directly into a single editable Kotlin source buffer.
- Provide a **Run Preview** action in the top app bar.
- Keep editor text in memory when navigating to and from the preview screen.
- No file picker, file explorer, project browser, or external compiler.
- Build and validate the Android app in GitHub Actions.

## Important implementation status

The first milestone establishes the editor UI, preview navigation, and ARM32 CI. Executing arbitrary user-written Kotlin/Jetpack Compose source requires an embedded Kotlin compiler plus the Compose compiler plugin and a safe class-loading strategy. That engine is not represented as complete by this starter milestone; the preview screen will report that compilation is not yet available rather than pretending the user's source has been executed.

## Build

Open this repository in Android Studio and run the `assembleDebug` Gradle task. The project uses Kotlin DSL and a Gradle Version Catalog.

## Target

- Android min SDK: 23
- ABI: `armeabi-v7a`
- CI JDK: 17
