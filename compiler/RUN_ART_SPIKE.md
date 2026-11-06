# Running the on-device compiler spike

The GitHub Actions workflow builds two separate artifacts:

- `compose-live-editor-debug`: the normal editor APK.
- `compose-live-editor-compiler-art-spike-test`: the instrumentation APK containing the embeddable Kotlin compiler.

The instrumentation APK is intentionally separate because the compiler dependency is large and is not part of the production APK.

## Install and run on a physical device

Download both artifacts from the same workflow run and extract the APKs. Install them on the target device:

```sh
adb install app-debug.apk
adb install app-debug-androidTest.apk
adb shell am instrument -w \
  com.techwithkaushik.composeliveeditor.test/androidx.test.runner.AndroidJUnitRunner
```

For the first acceptance run, use a 32-bit ARM device running Android 8.0/API 26 or newer. Capture the full instrumentation output.

## Interpreting the result

A green GitHub Actions run proves that the APKs build, install on the configured x86_64 emulator, and the instrumentation runner exits successfully. It does **not** prove that Kotlin compilation works.

The current compiler spike checks for `java.lang.management.ManagementFactory`. Stock Android ART does not provide that Java SE management API. When it is absent, the two compilation assertions are reported as skipped; the capability test reports `UNSUPPORTED_RUNTIME`. A skipped test is not a successful compilation result.

The current spike also does not prove Compose compiler transformation, D8 of generated source, generated-code loading, preview rendering, or live recomposition. Do not claim the preview feature is complete until edited source is actually rendered on an ARM32 Android device and syntax errors produce source-located diagnostics.
