package com.techwithkaushik.composeliveeditor.compiler

import java.io.File

/** One Kotlin source file from the selected workspace snapshot. */
data class KotlinSourceUnit(
    val relativePath: String,
    val content: String,
)

/** Immutable inputs shared by the compiler, D8 and runtime stages. */
data class PreviewCompilationRequest(
    val workspaceRoot: File,
    val sources: List<KotlinSourceUnit>,
    val previewEntryPoint: String,
    val compileClasspath: List<File>,
    val androidJar: File,
    val minSdk: Int,
    val outputDirectory: File,
)

enum class CompilerDiagnosticSeverity { INFO, WARNING, ERROR }

data class CompilerDiagnostic(
    val message: String,
    val severity: CompilerDiagnosticSeverity = CompilerDiagnosticSeverity.ERROR,
    val sourcePath: String? = null,
    val line: Int? = null,
    val column: Int? = null,
)

data class KotlinCompilerArtifact(
    val programJar: File,
    val previewClassName: String,
)

sealed interface KotlinCompilationResult {
    data class Success(
        val artifact: KotlinCompilerArtifact,
        val diagnostics: List<CompilerDiagnostic> = emptyList(),
    ) : KotlinCompilationResult

    data class Failure(
        val diagnostics: List<CompilerDiagnostic>,
    ) : KotlinCompilationResult
}

/**
 * Implementations must compile the supplied project sources with the Kotlin
 * compiler and the matching Compose compiler plugin. A plain JVM compilation
 * without Compose transformation is not a valid implementation.
 */
interface KotlinComposeCompilerBackend {
    val backendId: String

    fun compile(request: PreviewCompilationRequest): KotlinCompilationResult
}

data class DexCompilationRequest(
    val androidJar: File,
    val minSdk: Int,
    val outputDirectory: File,
)

data class DexArtifact(
    val dexFiles: List<File>,
    val previewClassName: String,
)

sealed interface DexCompilationResult {
    data class Success(val artifact: DexArtifact) : DexCompilationResult
    data class Failure(val diagnostics: List<CompilerDiagnostic>) : DexCompilationResult
}

/** Converts the Compose-transformed program into Android-loadable DEX files. */
interface AndroidDexBackend {
    val backendId: String

    fun convert(
        input: KotlinCompilerArtifact,
        request: DexCompilationRequest,
    ): DexCompilationResult
}

data class LoadedPreview(
    val entryPointClass: Class<*>,
    val classLoader: ClassLoader,
)

sealed interface PreviewRuntimeLoadResult {
    data class Success(val preview: LoadedPreview) : PreviewRuntimeLoadResult
    data class Failure(val diagnostics: List<CompilerDiagnostic>) : PreviewRuntimeLoadResult
}

/**
 * Loads generated DEX in the preview runtime and resolves the transformed
 * composable entry point. Invocation and UI attachment belong to the host.
 */
interface AndroidPreviewRuntimeBackend {
    val backendId: String

    fun load(
        artifact: DexArtifact,
        previewEntryPoint: String,
        optimizedDirectory: File,
    ): PreviewRuntimeLoadResult
}

enum class PreviewPipelineStage { INPUT, KOTLIN_COMPOSE_COMPILE, DEX, RUNTIME_LOAD }

sealed interface PreviewPipelineResult {
    data class Success(
        val preview: LoadedPreview,
        val compilerDiagnostics: List<CompilerDiagnostic>,
    ) : PreviewPipelineResult

    data class Failure(
        val stage: PreviewPipelineStage,
        val diagnostics: List<CompilerDiagnostic>,
    ) : PreviewPipelineResult

    data class Unavailable(
        val stage: PreviewPipelineStage,
        val reason: String,
    ) : PreviewPipelineResult
}

/**
 * Strict orchestration for the compiler-backed path.
 *
 * Missing backends and failed stages are surfaced explicitly. This pipeline
 * never falls back to the source interpreter, since doing so would make a
 * failed Kotlin compilation look like a successful preview.
 */
class PreviewCompilationPipeline(
    private val compiler: KotlinComposeCompilerBackend?,
    private val dexBackend: AndroidDexBackend?,
    private val runtime: AndroidPreviewRuntimeBackend?,
) {
    fun build(request: PreviewCompilationRequest): PreviewPipelineResult {
        validate(request)?.let {
            return PreviewPipelineResult.Failure(PreviewPipelineStage.INPUT, listOf(it))
        }

        val compilerBackend = compiler
            ?: return PreviewPipelineResult.Unavailable(
                PreviewPipelineStage.KOTLIN_COMPOSE_COMPILE,
                "No Android-compatible Kotlin + Compose compiler backend is installed.",
            )
        val dexConverter = dexBackend
            ?: return PreviewPipelineResult.Unavailable(
                PreviewPipelineStage.DEX,
                "No D8 backend is installed.",
            )
        val runtimeBackend = runtime
            ?: return PreviewPipelineResult.Unavailable(
                PreviewPipelineStage.RUNTIME_LOAD,
                "No Android preview runtime backend is installed.",
            )

        val compiled = try {
            compilerBackend.compile(request)
        } catch (failure: Exception) {
            return PreviewPipelineResult.Failure(
                PreviewPipelineStage.KOTLIN_COMPOSE_COMPILE,
                listOf(CompilerDiagnostic("Compiler backend failed: ${failure.message ?: failure.javaClass.simpleName}")),
            )
        }
        val compilerArtifact = when (compiled) {
            is KotlinCompilationResult.Failure -> return PreviewPipelineResult.Failure(
                PreviewPipelineStage.KOTLIN_COMPOSE_COMPILE,
                compiled.diagnostics,
            )
            is KotlinCompilationResult.Success -> {
                if (compiled.diagnostics.any { it.severity == CompilerDiagnosticSeverity.ERROR }) {
                    return PreviewPipelineResult.Failure(
                        PreviewPipelineStage.KOTLIN_COMPOSE_COMPILE,
                        compiled.diagnostics,
                    )
                }
                compiled
            }
        }

        val dex = try {
            dexConverter.convert(
                compilerArtifact.artifact,
                DexCompilationRequest(
                    androidJar = request.androidJar,
                    minSdk = request.minSdk,
                    outputDirectory = request.outputDirectory,
                ),
            )
        } catch (failure: Exception) {
            return PreviewPipelineResult.Failure(
                PreviewPipelineStage.DEX,
                listOf(CompilerDiagnostic("D8 backend failed: ${failure.message ?: failure.javaClass.simpleName}")),
            )
        }
        val dexArtifact = when (dex) {
            is DexCompilationResult.Failure -> return PreviewPipelineResult.Failure(
                PreviewPipelineStage.DEX,
                dex.diagnostics,
            )
            is DexCompilationResult.Success -> dex.artifact
        }

        val loaded = try {
            runtimeBackend.load(
                dexArtifact,
                request.previewEntryPoint,
                File(request.outputDirectory, "optimized"),
            )
        } catch (failure: Exception) {
            return PreviewPipelineResult.Failure(
                PreviewPipelineStage.RUNTIME_LOAD,
                listOf(CompilerDiagnostic("Preview runtime failed: ${failure.message ?: failure.javaClass.simpleName}")),
            )
        }
        return when (loaded) {
            is PreviewRuntimeLoadResult.Failure -> PreviewPipelineResult.Failure(
                PreviewPipelineStage.RUNTIME_LOAD,
                loaded.diagnostics,
            )
            is PreviewRuntimeLoadResult.Success -> PreviewPipelineResult.Success(
                preview = loaded.preview,
                compilerDiagnostics = compilerArtifact.diagnostics,
            )
        }
    }

    private fun validate(request: PreviewCompilationRequest): CompilerDiagnostic? {
        if (request.sources.isEmpty()) return CompilerDiagnostic("No Kotlin source files were supplied.")
        if (request.previewEntryPoint.isBlank()) return CompilerDiagnostic("A preview entry point is required.")
        if (request.minSdk < 26) return CompilerDiagnostic("Compiler-backed preview requires minSdk 26 or newer.")
        if (request.sources.any { it.relativePath.isBlank() }) {
            return CompilerDiagnostic("Every source file must have a workspace-relative path.")
        }
        if (request.sources.map { it.relativePath }.distinct().size != request.sources.size) {
            return CompilerDiagnostic("Duplicate source paths were supplied.")
        }
        val root = request.workspaceRoot.canonicalFile.toPath()
        for (source in request.sources) {
            val sourcePath = File(request.workspaceRoot, source.relativePath).canonicalFile.toPath()
            if (sourcePath == root || !sourcePath.startsWith(root)) {
                return CompilerDiagnostic("Source path escapes the selected workspace: ${source.relativePath}")
            }
        }
        return null
    }
}
