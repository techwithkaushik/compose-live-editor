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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Discovery test: proves whether the embeddable Kotlin compiler can execute on ART.
 *
 * This intentionally does not yet claim Compose compilation. It compiles a tiny Kotlin file with no JDK
 * or stdlib classpath so the first failure isolates compiler/ART compatibility before adding Compose and D8.
 * Run on a physical ARM32 device with:
 *   adb shell am instrument -w com.techwithkaushik.composeliveeditor.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class KotlinCompilerArtSpikeTest {
    @Test
    fun compilerRunsOnArtAndEmitsClassFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workDir = File(context.cacheDir, "kotlinc-art-spike").apply {
            deleteRecursively()
            mkdirs()
        }
        val source = File(workDir, "Smoke.kt").apply {
            writeText("package spike\nfun answer(): Int = 42\n")
        }
        val output = File(workDir, "out").apply { mkdirs() }
        val messages = RecordingMessageCollector()
        val args = K2JVMCompilerArguments().apply {
            freeArgs = listOf(source.absolutePath)
            destination = output.absolutePath
            noJdk = true
            noStdlib = true
            noReflect = true
            jvmTarget = "1.8"
        }

        val exit = try {
            K2JVMCompiler().exec(messages, Services.EMPTY, args)
        } catch (throwable: Throwable) {
            Log.e(TAG, "Kotlin compiler threw on ART; this is the spike result", throwable)
            fail("Compiler threw on ART: ${throwable.javaClass.name}: ${throwable.message}\n${throwable.stackTraceToString()}")
            return
        }

        val generated = File(output, "spike/SmokeKt.class")
        assertTrue("Compiler exit=$exit; messages:\n${messages.dump()}", exit == ExitCode.OK)
        assertTrue("Expected generated class at ${generated.absolutePath}", generated.isFile)
    }

    private class RecordingMessageCollector : MessageCollector {
        private val lines = mutableListOf<String>()
        private var sawError = false

        override fun clear() {
            lines.clear()
            sawError = false
        }

        override fun hasErrors(): Boolean = sawError

        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?
        ) {
            lines += "$severity: $message"
            if (severity == CompilerMessageSeverity.ERROR) sawError = true
        }

        fun dump(): String = lines.joinToString("\n")
    }

    private companion object {
        const val TAG = "ComposeEditorKotLincSpike"
    }
}
