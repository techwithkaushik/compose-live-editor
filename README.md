# Compose Live Editor

A lightweight Android-first Kotlin/Jetpack Compose editor targeting 32-bit ARM (`armeabi-v7a`).

## Active preview engine

The Run Preview action now uses an on-device, bounded Compose source interpreter. It parses the edited source and renders the resulting tree using the app's real Jetpack Compose runtime.

Supported first milestone:

- One `@Composable fun Name() { ... }` entry point.
- Optional package/import declarations.
- `Column { ... }`, `Row { ... }`, and `Box { ... }`.
- `Text("literal")` / `Text(text = "literal")`.
- `Button(onClick = { ... }) { ... }` with declarative child content.

This is **not arbitrary Kotlin execution**. Callback bodies, variables, expressions, modifiers, themes, and other Kotlin syntax are not interpreted yet. Unsupported calls produce source-located errors; the engine never substitutes a hard-coded preview. Source is parsed locally; there is no Termux or remote compilation service.

The old stock Kotlin/JVM compiler experiment is not part of the app build. Engine parser tests run as local JVM unit tests, and CI publishes exactly one installable debug APK.

## Build

Open this repository in Android Studio and run `assembleDebug`. The project uses Kotlin DSL and a Gradle Version Catalog.

## Target

- Android min SDK: 26
- Primary ABI: `armeabi-v7a`
- CI JDK: 17
