package com.techwithkaushik.composeliveeditor

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First ART compatibility gate for the embedded Kotlin compiler.
 *
 * This intentionally checks class loading and construction only. It does not claim
 * that Kotlin source compilation or Compose rendering works on Android yet.
 */
@RunWith(AndroidJUnit4::class)
class KotlinCompilerArtSpikeTest {

    @Test
    fun compilerEntryPointLoadsAndCanBeConstructedOnArt() {
        val compilerClass = Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
        assertNotNull("K2JVMCompiler must be present in the instrumentation APK", compilerClass)

        val compiler = compilerClass.getDeclaredConstructor().newInstance()
        assertNotNull("K2JVMCompiler must be constructible on ART", compiler)
    }
}
