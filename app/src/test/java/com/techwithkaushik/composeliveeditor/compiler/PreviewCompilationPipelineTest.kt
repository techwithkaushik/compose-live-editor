package com.techwithkaushik.composeliveeditor.compiler

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewCompilationPipelineTest {
    private fun request(minSdk: Int = 26) = PreviewCompilationRequest(
        workspaceRoot = File("workspace"),
        sources = listOf(KotlinSourceUnit("src/Main.kt", "@Preview @Composable fun Screen() {}")),
        previewEntryPoint = "Screen",
        compileClasspath = emptyList(),
        androidJar = File("android.jar"),
        minSdk = minSdk,
        outputDirectory = File("build/preview"),
    )

    @Test
    fun missingCompilerIsExplicitAndDoesNotPretendInterpreterSucceeded() {
        val pipeline = PreviewCompilationPipeline(
            compiler = null,
            dexBackend = null,
            runtime = null,
        )

        val result = pipeline.build(request())

        assertTrue(result is PreviewPipelineResult.Unavailable)
        assertEquals(
            PreviewPipelineStage.KOTLIN_COMPOSE_COMPILE,
            (result as PreviewPipelineResult.Unavailable).stage,
        )
    }

    @Test
    fun invalidMinSdkIsRejectedBeforeAnyBackendRuns() {
        var compilerCalls = 0
        val compiler = object : KotlinComposeCompilerBackend {
            override val backendId = "test"
            override fun compile(request: PreviewCompilationRequest): KotlinCompilationResult {
                compilerCalls++
                return KotlinCompilationResult.Failure(emptyList())
            }
        }

        val result = PreviewCompilationPipeline(compiler, null, null).build(request(minSdk = 25))

        assertTrue(result is PreviewPipelineResult.Failure)
        assertEquals(PreviewPipelineStage.INPUT, (result as PreviewPipelineResult.Failure).stage)
        assertEquals(0, compilerCalls)
    }

    @Test
    fun sourcePathsCannotEscapeTheWorkspace() {
        val result = PreviewCompilationPipeline(null, null, null).build(
            request().copy(sources = listOf(KotlinSourceUnit("../outside.kt", "fun main() = Unit"))),
        )

        assertTrue(result is PreviewPipelineResult.Failure)
        assertEquals(PreviewPipelineStage.INPUT, (result as PreviewPipelineResult.Failure).stage)
        assertTrue(result.diagnostics.single().message.contains("escapes"))
    }

    @Test
    fun compilerDiagnosticsStopThePipelineBeforeDex() {
        var dexCalls = 0
        val compiler = object : KotlinComposeCompilerBackend {
            override val backendId = "test-compiler"
            override fun compile(request: PreviewCompilationRequest) = KotlinCompilationResult.Failure(
                listOf(CompilerDiagnostic("Unresolved reference: MaterialTheme", sourcePath = "src/Main.kt", line = 4, column = 5)),
            )
        }
        val dex = object : AndroidDexBackend {
            override val backendId = "test-d8"
            override fun convert(input: KotlinCompilerArtifact, request: DexCompilationRequest): DexCompilationResult {
                dexCalls++
                return DexCompilationResult.Failure(emptyList())
            }
        }

        val result = PreviewCompilationPipeline(compiler, dex, null).build(request())

        assertTrue(result is PreviewPipelineResult.Failure)
        assertEquals(PreviewPipelineStage.KOTLIN_COMPOSE_COMPILE, (result as PreviewPipelineResult.Failure).stage)
        assertEquals(0, dexCalls)
    }

    @Test
    fun missingDexBackendIsReportedAfterSuccessfulCompilation() {
        var compilerCalls = 0
        val compiler = object : KotlinComposeCompilerBackend {
            override val backendId = "test-compiler"
            override fun compile(request: PreviewCompilationRequest): KotlinCompilationResult {
                compilerCalls++
                return KotlinCompilationResult.Success(
                    KotlinCompilerArtifact(File("program.jar"), "sample.ScreenKt"),
                )
            }
        }

        val result = PreviewCompilationPipeline(compiler, null, null).build(request())

        assertEquals(1, compilerCalls)
        assertTrue(result is PreviewPipelineResult.Unavailable)
        assertEquals(PreviewPipelineStage.DEX, (result as PreviewPipelineResult.Unavailable).stage)
    }

    @Test
    fun missingRuntimeBackendIsReportedAfterSuccessfulDexConversion() {
        var dexCalls = 0
        val compiler = object : KotlinComposeCompilerBackend {
            override val backendId = "test-compiler"
            override fun compile(request: PreviewCompilationRequest) =
                KotlinCompilationResult.Success(
                    KotlinCompilerArtifact(File("program.jar"), "sample.ScreenKt"),
                )
        }
        val dex = object : AndroidDexBackend {
            override val backendId = "test-d8"
            override fun convert(input: KotlinCompilerArtifact, request: DexCompilationRequest): DexCompilationResult {
                dexCalls++
                return DexCompilationResult.Success(
                    DexArtifact(listOf(File("classes.dex")), input.previewClassName),
                )
            }
        }

        val result = PreviewCompilationPipeline(compiler, dex, null).build(request())

        assertEquals(1, dexCalls)
        assertTrue(result is PreviewPipelineResult.Unavailable)
        assertEquals(PreviewPipelineStage.RUNTIME_LOAD, (result as PreviewPipelineResult.Unavailable).stage)
    }

    @Test
    fun successfulBuildRunsCompilerDexAndRuntimeInOrder() {
        val calls = mutableListOf<String>()
        val compiler = object : KotlinComposeCompilerBackend {
            override val backendId = "test-compiler"
            override fun compile(request: PreviewCompilationRequest): KotlinCompilationResult {
                calls += "compile"
                return KotlinCompilationResult.Success(
                    KotlinCompilerArtifact(File("program.jar"), "sample.ScreenKt"),
                    listOf(CompilerDiagnostic("Using test compiler", CompilerDiagnosticSeverity.INFO)),
                )
            }
        }
        val dex = object : AndroidDexBackend {
            override val backendId = "test-d8"
            override fun convert(input: KotlinCompilerArtifact, request: DexCompilationRequest): DexCompilationResult {
                calls += "dex"
                assertEquals("sample.ScreenKt", input.previewClassName)
                assertEquals(26, request.minSdk)
                return DexCompilationResult.Success(DexArtifact(listOf(File("classes.dex")), input.previewClassName))
            }
        }
        val runtime = object : AndroidPreviewRuntimeBackend {
            override val backendId = "test-runtime"
            override fun load(
                artifact: DexArtifact,
                previewEntryPoint: String,
                optimizedDirectory: File,
            ): PreviewRuntimeLoadResult {
                calls += "runtime"
                assertEquals("Screen", previewEntryPoint)
                return PreviewRuntimeLoadResult.Success(LoadedPreview(String::class.java, javaClass.classLoader!!))
            }
        }

        val result = PreviewCompilationPipeline(compiler, dex, runtime).build(request())

        assertEquals(listOf("compile", "dex", "runtime"), calls)
        assertTrue(result is PreviewPipelineResult.Success)
        assertEquals(
            "Using test compiler",
            (result as PreviewPipelineResult.Success).compilerDiagnostics.single().message,
        )
    }
}
