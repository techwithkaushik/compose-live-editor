package com.techwithkaushik.composeliveeditor.compiler

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Discovery tests: determine whether the embeddable Kotlin compiler can execute on ART and produce
 * useful diagnostics. These tests do not yet claim Compose compilation or preview rendering.
 *
 * Run on a physical Android device (ARM32 is the required first target):
 *   adb install app-debug.apk
 *   adb install app-debug-androidTest.apk
 *   adb shell am instrument -w com.techwithkaushik.composeliveeditor.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class KotlinCompilerArtSpikeTest {
    @Test
    fun compilerRunsOnArtAndEmitsClassFile() {
        assumeCompilerRuntimeSupported()
        val workDir = newWorkDir("valid")
        val source = File(workDir, "Smoke.kt").apply {
            writeText("package spike\nfun answer(): Int = 42\n")
        }
        val output = File(workDir, "out").apply { mkdirs() }
        val messages = RecordingMessageCollector()
        val exit = compile(source, output, messages)

        val generated = File(output, "spike/SmokeKt.class")
        assertTrue("Compiler exit=$exit; messages:\n${messages.dump()}", exit == ExitCode.OK)
        assertTrue("Expected generated class at ${generated.absolutePath}", generated.isFile)
    }

    @Test
    fun invalidSourceFailsAndReportsCompilerDiagnostic() {
        assumeCompilerRuntimeSupported()
        val workDir = newWorkDir("invalid")
        val source = File(workDir, "Broken.kt").apply {
            writeText("package spike\nfun answer(: Int = 42\n")
        }
        val output = File(workDir, "out").apply { mkdirs() }
        val messages = RecordingMessageCollector()
        val exit = compile(source, output, messages)

        assertFalse("Invalid source must not report success", exit == ExitCode.OK)
        assertTrue(
            "Expected a compiler error with source location; got:\n${messages.dump()}",
            messages.entries.any { it.contains("ERROR") && it.contains("Broken.kt") }
        )
        assertFalse("Invalid source must not emit the entry class", File(output, "spike/BrokenKt.class").exists())
    }

    /**
     * The stock Kotlin/JVM compiler references java.lang.management.ManagementFactory,
     * which is absent from Android's boot class path. Skip compilation assertions on ART
     * until an Android-compatible compiler adapter is implemented; never count this as a
     * successful compile.
     */
    private fun assumeCompilerRuntimeSupported() {
        val available = try {
            Class.forName("java.lang.management.ManagementFactory")
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: NoClassDefFoundError) {
            false
        }
        assumeTrue(
            "SKIPPED: stock Kotlin/JVM compiler requires java.lang.management.ManagementFactory; " +
                "Android ART does not provide it. Add an Android-compatible compiler adapter before claiming compilation works.",
            available
        )
    }

    private fun newWorkDir(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.cacheDir, "kotlinc-art-spike/$name").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun compile(source: File, output: File, messages: RecordingMessageCollector): ExitCode {
        val args = K2JVMCompilerArguments().apply {
            freeArgs = listOf(source.absolutePath)
            destination = output.absolutePath
            noJdk = true
            noStdlib = true
            noReflect = true
            jvmTarget = "1.8"
        }
        return try {
            K2JVMCompiler().exec(messages, Services.EMPTY, args)
        } catch (throwable: Throwable) {
            Log.e(TAG, "Kotlin compiler threw on ART; this is the spike result", throwable)
            fail("Compiler threw on ART: ${throwable.javaClass.name}: ${throwable.message}\n${throwable.stackTraceToString()}")
            throw AssertionError("unreachable")
        }
    }

    private class RecordingMessageCollector : MessageCollector {
        val entries = mutableListOf<String>()
        private var sawError = false

        override fun clear() {
            entries.clear()
            sawError = false
        }

        override fun hasErrors(): Boolean = sawError

        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?
        ) {
            val source = location?.let { "${it.path}:${it.line}:${it.column}: " }.orEmpty()
            entries += "$severity: $source$message"
            if (severity == CompilerMessageSeverity.ERROR) sawError = true
        }

        fun dump(): String = entries.joinToString("\n")
    }

    private companion object {
        const val TAG = "ComposeEditorKotLincSpike"
    }
}
