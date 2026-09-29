package com.aectann.battlecity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aectann.battlecity.engine.TanksProgressStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.GameController.GCKeyboard
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.UIKit.UIApplication
import kotlin.time.Clock

@Composable
actual fun rememberTanksPlatform(): TanksPlatform = remember { IosTanksPlatform() }

private class IosTanksPlatform : TanksPlatform {

    override val keyValueStore: TanksKeyValueStore =
        UserDefaultsStore(NSUserDefaults.standardUserDefaults)
    override val progressStore: TanksProgressStore = StoredTanksProgress(keyValueStore)

    override val appVersion: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""

    override fun nowEpochMillis(): Long =
        Clock.System.now().toEpochMilliseconds()

    override fun createSoundPlayer(
        clips: Map<TanksClip, ByteArray>,
        enabled: Boolean
    ): TanksSoundPlayer = IosSoundPlayer(clips, enabled)

    override fun setKeepAwake(enabled: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
    }

    /**
     * iOS has no system bars to hide: the Compose view controller is already presented
     * edge to edge by ContentView, so there is nothing to toggle here.
     */
    override fun setImmersive(enabled: Boolean) = Unit

    /**
     * GameController reports a coalesced keyboard whenever a physical one is attached, which on
     * iPad means a keyboard case or a paired Bluetooth board. Read per call, because it can be
     * connected and disconnected while the game is open.
     */
    override val hasPhysicalKeyboard: Boolean
        get() = GCKeyboard.coalescedKeyboard != null

    /** Every device this build runs on has a touchscreen. */
    override val usesTouchControls: Boolean = true
}

private class UserDefaultsStore(private val defaults: NSUserDefaults) : TanksKeyValueStore {

    override fun getInt(key: String, fallback: Int): Int =
        if (defaults.objectForKey(key) == null) fallback else defaults.integerForKey(key).toInt()

    override fun putInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), key)
    }

    override fun getBoolean(key: String, fallback: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) fallback else defaults.boolForKey(key)

    override fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, key)
    }

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) {
        defaults.setObject(value, key)
    }
}

/**
 * AVAudioPlayer plays one sound at a time, so each clip keeps a small ring of players and
 * the next free one is used. The session category is Ambient: game sound should never stop
 * whatever the player is already listening to.
 *
 * If the AVAudioPlayer constructor signature differs in your Kotlin/Native version, this is
 * the only place to adjust — and returning [SilentSoundPlayer] from the platform keeps the
 * game fully playable while you do.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosSoundPlayer(
    clips: Map<TanksClip, ByteArray>,
    private var enabled: Boolean
) : TanksSoundPlayer {

    private val pools: Map<TanksClip, List<AVAudioPlayer>>
    private val engineLoop: AVAudioPlayer?
    private var engineRunning = false
    private var released = false

    init {
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
            AVAudioSession.sharedInstance().setActive(true, null)
        }

        val built = mutableMapOf<TanksClip, List<AVAudioPlayer>>()
        clips.forEach { (clip, bytes) ->
            val data = bytes.toNSData() ?: return@forEach
            val voices = if (clip == TanksClip.EngineLoop) 1 else VoicesPerClip
            val players = (0 until voices).mapNotNull {
                makePlayer(data)?.also { player ->
                    player.setVolume(if (clip == TanksClip.EngineLoop) EngineVolume else SfxVolume)
                    player.prepareToPlay()
                }
            }
            if (players.isNotEmpty()) built[clip] = players
        }
        pools = built.toMap()
        engineLoop = pools[TanksClip.EngineLoop]?.firstOrNull()?.also { it.numberOfLoops = -1 }
    }

    // Music: one looping AVAudioPlayer per track, made when its file arrives. Only the wanted one
    // plays, and only while sound is on.
    private val musicPlayers = mutableMapOf<TanksMusic, AVAudioPlayer>()
    private var musicWanted: TanksMusic? = null
    private var musicPlaying: TanksMusic? = null

    override fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (!enabled) engineLoop?.pause() else if (engineRunning) engineLoop?.play()
        reconcileMusic()
    }

    override fun loadMusic(track: TanksMusic, bytes: ByteArray) {
        if (released || track in musicPlayers) return
        val data = bytes.toNSData() ?: return
        makePlayer(data)?.let { player ->
            player.numberOfLoops = -1
            player.setVolume(MusicVolume)
            player.prepareToPlay()
            musicPlayers[track] = player
            reconcileMusic()
        }
    }

    override fun setMusic(track: TanksMusic?) {
        musicWanted = track
        reconcileMusic()
    }

    private fun reconcileMusic() {
        val wanted = musicWanted.takeIf { enabled && !released }
        if (wanted == musicPlaying) return
        musicPlaying?.let { musicPlayers[it]?.stop() }
        musicPlaying = null
        val player = wanted?.let { musicPlayers[it] } ?: return
        player.currentTime = 0.0
        player.play()
        musicPlaying = wanted
    }

    override fun play(clip: TanksClip) {
        if (!enabled || released) return
        val voices = pools[clip] ?: return
        val voice = voices.firstOrNull { !it.playing } ?: voices.first()
        voice.currentTime = 0.0
        voice.play()
    }

    override fun setEngineRunning(running: Boolean) {
        if (engineRunning == running) return
        engineRunning = running
        val loop = engineLoop ?: return
        if (running && enabled) loop.play() else loop.pause()
    }

    override fun release() {
        if (released) return
        released = true
        pools.values.flatten().forEach { it.stop() }
        musicPlayers.values.forEach { it.stop() }
        musicPlaying = null
        runCatching { AVAudioSession.sharedInstance().setActive(false, null) }
    }

    private fun makePlayer(data: NSData): AVAudioPlayer? =
        runCatching { AVAudioPlayer(data = data, error = null) }.getOrNull()

    private companion object {
        const val VoicesPerClip = 3
        const val SfxVolume = 0.7f
        const val EngineVolume = 0.3f
        const val MusicVolume = 0.5f
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData? {
    if (isEmpty()) return null
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
