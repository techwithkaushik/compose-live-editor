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
            val signingStore = System.getenv("SIGN_KEY_STORE")
                ?: error("SIGN_KEY_STORE must point to the restored repository keystore")
            val signingAlias = System.getenv("SIGNING_KEY_ALIAS")
                ?: error("SIGNING_KEY_ALIAS repository secret is required")
            val signingKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                ?: error("SIGNING_KEY_PASSWORD repository secret is required")
            val signingStorePassword = System.getenv("SIGNING_STORE_PASSWORD")
                ?: error("SIGNING_STORE_PASSWORD repository secret is required")
            val keystoreFile = file(signingStore)
            require(keystoreFile.isFile) {
                "Release keystore does not exist: $signingStore"
            }
            storeFile = keystoreFile
            keyAlias = signingAlias
            keyPassword = signingKeyPassword
            storePassword = signingStorePassword
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
