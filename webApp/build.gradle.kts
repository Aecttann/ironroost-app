@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The page carries the release version and the About screen reads it back, so the uploaded build
// names itself. index.html sits outside the resource tree because it is a template: only the
// stamped copy is packed.
val releaseVersionName = providers.gradleProperty("releaseVersionName").orElse("1.0.0").get()

// The CrazyGames leaderboard key. It is only issued to invited games, and it is a client-side
// secret in name only — it ships inside the page either way — but it stays out of the repository
// so a fork does not submit scores to someone else's board. Empty leaves the board switched off.
val leaderboardKey = providers.gradleProperty("crazyGamesLeaderboardKey").orElse("").get()

val stampAppVersion = tasks.register<Copy>("stampAppVersion") {
    group = "build"
    description = "Writes the release version and leaderboard key into the page shell."
    from(layout.projectDirectory.file("src/webShell/index.html"))
    into(layout.buildDirectory.dir("generated/webShell"))
    filter(
        mapOf(
            "tokens" to mapOf(
                "APP_VERSION" to releaseVersionName,
                "LEADERBOARD_KEY" to leaderboardKey
            )
        ),
        ReplaceTokens::class.java
    )
}

kotlin {
    wasmJs {
        outputModuleName = "steel-eagle"
        browser {
            commonWebpackConfig {
                outputFileName = "steel-eagle.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
        wasmJsMain {
            resources.srcDir(stampAppVersion)
        }
    }
}

// Source maps would ship the whole Kotlin source tree to players for no gain.
tasks.named<KotlinWebpack>("wasmJsBrowserProductionWebpack") {
    sourceMaps = false
}

val productionDistribution = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")

val verifyCrazyGamesBasic = tasks.register<Exec>("verifyCrazyGamesBasic") {
    group = "distribution"
    description = "Checks the production web bundle against CrazyGames Basic file limits."
    dependsOn("wasmJsBrowserDistribution")
    inputs.dir(productionDistribution)
    commandLine(
        "java",
        layout.projectDirectory.file("scripts/VerifyCrazyGamesBasic.java").asFile.absolutePath,
        productionDistribution.get().asFile.absolutePath
    )
}

tasks.register<Zip>("packageCrazyGamesBasic") {
    group = "distribution"
    description = "Builds the upload-ready CrazyGames Basic ZIP."
    dependsOn(verifyCrazyGamesBasic)
    from(productionDistribution)
    destinationDirectory = layout.buildDirectory.dir("crazygames")
    archiveFileName = "steel-eagle-crazygames-basic.zip"
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
