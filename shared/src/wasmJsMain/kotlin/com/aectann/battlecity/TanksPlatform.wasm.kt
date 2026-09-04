@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.aectann.battlecity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.aectann.battlecity.engine.TanksProgressStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
actual fun rememberTanksPlatform(): TanksPlatform {
    val platform = remember { BrowserTanksPlatform() }
    SideEffect { browserHideLoading() }
    return platform
}

private class BrowserTanksPlatform : TanksPlatform {
    override val keyValueStore: TanksKeyValueStore = BrowserKeyValueStore()
    override val progressStore: TanksProgressStore = StoredTanksProgress(keyValueStore)
    // Stamped into index.html by :webApp, so About matches the uploaded build.
    override val appVersion: String = "${browserAppVersion()} Web"
    override val portal: TanksPortal = BrowserTanksPortal

    override fun createSoundPlayer(
        clips: Map<TanksClip, ByteArray>,
        enabled: Boolean
    ): TanksSoundPlayer = BrowserTanksSoundPlayer(clips, enabled)

    override fun nowEpochMillis(): Long = browserEpochMillis().toLong()

    override fun setKeepAwake(enabled: Boolean) = Unit

    // CrazyGames owns the iframe/fullscreen chrome. The game only adapts to its viewport.
    override fun setImmersive(enabled: Boolean) = Unit
}

private class BrowserKeyValueStore : TanksKeyValueStore {
    private val memoryFallback = mutableMapOf<String, String>()

    override fun getInt(key: String, fallback: Int): Int =
        read(key)?.toIntOrNull() ?: fallback

    override fun putInt(key: String, value: Int) = write(key, value.toString())

    override fun getBoolean(key: String, fallback: Boolean): Boolean = when (read(key)) {
        "true" -> true
        "false" -> false
        else -> fallback
    }

    override fun putBoolean(key: String, value: Boolean) = write(key, value.toString())

    override fun getString(key: String): String? = read(key)

    override fun putString(key: String, value: String) = write(key, value)

    private fun read(key: String): String? = browserStorageGet(key) ?: memoryFallback[key]

    private fun write(key: String, value: String) {
        memoryFallback[key] = value
        browserStorageSet(key, value)
    }
}

private object BrowserTanksPortal : TanksPortal {
    override fun setGameplayActive(active: Boolean) = browserSetGameplayActive(active)
    override fun setStage(stage: Int) = browserSetStage(stage)
    override fun reportProgress(completedStage: Int, totalStages: Int) =
        browserReportProgress(completedStage, totalStages)

    override fun clearContext() = browserClearGameContext()
    override fun submitScore(score: Int) = browserSubmitScore(score)

    // Read once: the key is stamped into the page at build time and cannot change at runtime.
    override val hasLeaderboard: Boolean by lazy { browserHasLeaderboard() }
}

@OptIn(ExperimentalEncodingApi::class)
private class BrowserTanksSoundPlayer(
    clips: Map<TanksClip, ByteArray>,
    enabled: Boolean
) : TanksSoundPlayer {
    private var released = false

    init {
        clips.forEach { (clip, bytes) ->
            browserAudioLoad(clip.name, Base64.Default.encode(bytes))
        }
        browserAudioSetEnabled(enabled)
    }

    override fun setEnabled(enabled: Boolean) {
        if (!released) browserAudioSetEnabled(enabled)
    }

    override fun play(clip: TanksClip) {
        if (!released) browserAudioPlay(clip.name)
    }

    override fun setEngineRunning(running: Boolean) {
        if (!released) browserAudioSetEngineRunning(running)
    }

    override fun release() {
        if (released) return
        released = true
        browserAudioRelease()
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

private fun browserAppVersion(): String =
    js("globalThis.steelEaglePortal?.appVersion ?? '1.0.0'")

private fun browserStorageGet(key: String): String? =
    js("globalThis.steelEaglePortal?.storageGet(key) ?? null")

private fun browserStorageSet(key: String, value: String) {
    js("globalThis.steelEaglePortal?.storageSet(key, value)")
}

private fun browserSetGameplayActive(active: Boolean) {
    js("active ? globalThis.steelEaglePortal?.gameplayStart() : globalThis.steelEaglePortal?.gameplayStop()")
}

private fun browserSetStage(stage: Int) {
    js("globalThis.steelEaglePortal?.setGameContext(stage)")
}

private fun browserReportProgress(completedStage: Int, totalStages: Int) {
    js("globalThis.steelEaglePortal?.reportProgress(completedStage, totalStages)")
}

private fun browserClearGameContext() {
    js("globalThis.steelEaglePortal?.clearGameContext()")
}

private fun browserSubmitScore(score: Int) {
    js("globalThis.steelEaglePortal?.submitScore(score)")
}

private fun browserHasLeaderboard(): Boolean =
    js("globalThis.steelEaglePortal?.isLeaderboardAvailable === true")

private fun browserAudioLoad(name: String, base64: String) {
    js("globalThis.steelEaglePortal?.audioLoad(name, base64)")
}

private fun browserAudioSetEnabled(enabled: Boolean) {
    js("globalThis.steelEaglePortal?.audioSetEnabled(enabled)")
}

private fun browserAudioPlay(name: String) {
    js("globalThis.steelEaglePortal?.audioPlay(name)")
}

private fun browserAudioSetEngineRunning(running: Boolean) {
    js("globalThis.steelEaglePortal?.audioSetEngineRunning(running)")
}

private fun browserAudioRelease() {
    js("globalThis.steelEaglePortal?.audioRelease()")
}

// Kotlin/Wasm js() interop returns a JS number, so the epoch arrives as a Double.
private fun browserEpochMillis(): Double = js("Date.now()")

private fun browserHideLoading() {
    js("globalThis.steelEaglePortal?.hideLoading()")
}
