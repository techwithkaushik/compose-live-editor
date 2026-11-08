plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.techwithkaushik.composeliveeditor"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.techwithkaushik.composeliveeditor"
        // The embedded compiler's MethodHandle APIs require Android O (API 26) for D8.
        // This is an explicit compatibility floor for the current compiler spike.
        minSdk = 26
        targetSdk = 36
        // Keep APK version identity tied to the source branch and exact commit.
        val gitBranch = providers.exec {
            commandLine("git", "branch", "--show-current")
            isIgnoreExitValue = false
        }.standardOutput.asText.get().trim()
            .ifBlank {
                System.getenv("GITHUB_HEAD_REF")
                    ?: System.getenv("GITHUB_REF_NAME")
                    ?: error("Unable to determine the current Git branch")
            }

        val distroName = gitBranch
            .substringAfter('/', gitBranch)
            .substringBefore("-runtime-ssh")
            .substringBefore('-')
            .ifBlank { error("Unable to derive distro name from Git branch: '$gitBranch'") }

        val shortCommitSha = providers.exec {
            commandLine("git", "rev-parse", "--short=7", "HEAD")
            isIgnoreExitValue = false
        }.standardOutput.asText.get().trim()
            .also { if (it.isBlank()) error("Unable to determine the current Git commit SHA") }

        val isShallowCheckout = providers.exec {
            commandLine("git", "rev-parse", "--is-shallow-repository")
            isIgnoreExitValue = false
        }.standardOutput.asText.get().trim().toBoolean()

        check(!isShallowCheckout) {
            "Full Git history is required to calculate versionCode. " +
                "Configure actions/checkout with fetch-depth: 0."
        }

        val relatedBranchCommitCount = providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            isIgnoreExitValue = false
        }.standardOutput.asText.get().trim().toIntOrNull()
            ?.takeIf { it > 0 }
            ?: error("Unable to determine a positive branch commit count")

        versionCode = relatedBranchCommitCount
        versionName = "$distroName-$shortCommitSha"
        logger.lifecycle(
            "App version: versionName=$versionName, versionCode=$versionCode, branch=$gitBranch"
        )
    }
    signingConfigs {
        create("release") {
            val signingStore = providers.environmentVariable("SIGN_KEY_STORE").orNull
            keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
            storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
            signingStore?.let { storeFile = file(it) }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Keep debug/test configuration independent from private release credentials.
    // Release assembly itself must validate every credential and the restored keystore.
    tasks.register("validateReleaseSigning") {
        group = "verification"
        description = "Validates the release signing credentials and keystore."

        doLast {
            val requiredEnvironmentVariables = listOf(
                "SIGN_KEY_STORE",
                "SIGNING_KEY_ALIAS",
                "SIGNING_KEY_PASSWORD",
                "SIGNING_STORE_PASSWORD"
            )
            val missingVariables = requiredEnvironmentVariables.filter {
                System.getenv(it).isNullOrBlank()
            }
            check(missingVariables.isEmpty()) {
                "Missing release signing environment variables: ${missingVariables.joinToString()}"
            }

            val keystorePath = checkNotNull(System.getenv("SIGN_KEY_STORE"))
            val keystore = file(keystorePath)
            check(keystore.isFile) {
                "Release keystore does not exist: $keystorePath"
            }
        }
    }

    tasks.configureEach {
        if (name == "assembleRelease") {
            dependsOn("validateReleaseSigning")
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin {
    jvmToolchain(17)
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(platform(libs.sora.editor.bom))
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
