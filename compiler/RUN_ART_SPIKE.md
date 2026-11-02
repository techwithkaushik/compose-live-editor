# Running the on-device compiler spike

The GitHub Actions workflow builds two separate artifacts:

- `compose-live-editor-arm32-debug`: the normal editor APK.
- `compose-live-editor-compiler-art-spike-test`: the instrumentation APK containing the embeddable Kotlin compiler.

The instrumentation APK is intentionally separate because the compiler dependency is large and is not part of the production APK.

## Install and run on a physical device

Download both artifacts from the same successful workflow run and extract the APKs. Install them on the target device:

```sh
adb install app-debug.apk
adb install app-debug-androidTest.apk
adb shell am instrument -w \
  com.techwithkaushik.composeliveeditor.test/androidx.test.runner.AndroidJUnitRunner
```

For the first acceptance run, use a 32-bit ARM device running Android 8.0/API 26 or newer. Capture the full instrumentation output. The two tests check that the compiler can emit a class file and that malformed source fails with a source-located diagnostic.

A green CI build only proves that both APKs package successfully. It does **not** prove that the compiler executes on ART; the instrumentation tests must actually run on a device. These tests also do not yet prove Compose compiler transformation, D8 of generated source, class loading, or rendered UI.
