# Compose Live Editor

A lightweight Android-first Kotlin/Jetpack Compose editor targeting 32-bit ARM (\`armeabi-v7a\`).

## Active preview engine

Run Preview parses source locally and renders the supported tree with the app's real Jetpack Compose runtime. It is a bounded interpreter, not a Kotlin compiler or arbitrary Kotlin execution environment.

Supported syntax:
- One top-level \`@Composable fun Name() { ... }\` entry point.
- Optional package/import declarations before the entry point.
- \`Column { ... }\`, \`Row { ... }\`, and \`Box { ... }\`.
- \`Text("literal")\`, \`Text(text = "literal")\`, and integer state interpolation such as \`$count\`.
- State declarations before the root: \`var count by remember { mutableIntStateOf(0) }\` or \`mutableStateOf(0)\`.
- Button callback actions: \`count++\`, \`count--\`, \`count += 2\`, \`count -= 2\`, and \`count = 10\`.
- Button state updates use Compose snapshot state and automatically recompose the preview.

Unsupported Kotlin constructs produce source-located errors. General functions, loops, conditionals, arbitrary lambdas, non-integer state, modifiers, additional Material components, and general expressions are not supported. Limits: 64 KiB source, 32 layout nesting levels, 512 nodes, 32 state variables, and 32 actions per button.

There is no Termux or remote compilation service. Parser tests run as JVM unit tests, and CI publishes exactly one installable debug APK.

## Build
Open this repository in Android Studio and run \`assembleDebug\`. The project uses Kotlin DSL and a Gradle Version Catalog.

## Target
- Android min SDK: 26
- Primary ABI: \`armeabi-v7a\`
- CI JDK: 17
