package com.aectann.classicgames.battlecity

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.util.concurrent.ConcurrentHashMap

private const val SfxRoot = "battle_city/assets/audio/sfx"
private const val MusicRoot = "battle_city/assets/audio/music_loops"
private const val SfxVolume = 0.7f
private const val EngineVolume = 0.3f

/**
 * Plays the tank sound set. Sounds are skipped until SoundPool reports them as decoded,
 * so the first shot of a session is never a silent one on slow devices.
 */
class BattleCitySoundPlayer(
    context: Context,
    private var enabled: Boolean
) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ready = ConcurrentHashMap<Int, Boolean>()
    private var released = false
    private var engineStreamId = 0
    private var engineRunning = false

    private val sounds: Map<BattleCitySoundEvent, Int> = mapOf(
        BattleCitySoundEvent.Shoot to load(context, SfxRoot, "shoot.wav"),
        BattleCitySoundEvent.ExplosionBig to load(context, SfxRoot, "explosion_big.wav"),
        BattleCitySoundEvent.ExplosionSmall to load(context, SfxRoot, "explosion_small.wav"),
        BattleCitySoundEvent.HitBrick to load(context, SfxRoot, "hit_brick.wav"),
        BattleCitySoundEvent.HitSteel to load(context, SfxRoot, "hit_steel.wav"),
        BattleCitySoundEvent.PowerUp to load(context, SfxRoot, "powerup.wav"),
        BattleCitySoundEvent.ExtraLife to load(context, SfxRoot, "extra_life.wav"),
        BattleCitySoundEvent.GameOver to load(context, SfxRoot, "game_over.wav"),
        BattleCitySoundEvent.StageStart to load(context, MusicRoot, "stage_start_jingle.wav")
    ).filterValues { it != 0 }

    private val menuSelectId = load(context, SfxRoot, "menu_select.wav")
    private val engineLoopId = load(context, MusicRoot, "engine_rumble_loop.wav")

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, statusCode ->
            if (statusCode == 0) ready[sampleId] = true
        }
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!enabled) {
            stopEngineLoop()
        } else if (engineRunning) {
            startEngineLoop()
        }
    }

    fun play(event: BattleCitySoundEvent) {
        val soundId = sounds[event] ?: return
        playId(soundId, SfxVolume)
    }

    fun playMenuSelect() = playId(menuSelectId, SfxVolume)

    /** Keeps the engine rumble looping while the tank is actually being driven. */
    fun setEngineRunning(running: Boolean) {
        if (engineRunning == running) return
        engineRunning = running
        if (running) startEngineLoop() else stopEngineLoop()
    }

    fun release() {
        if (released) return
        released = true
        stopEngineLoop()
        soundPool.release()
    }

    private fun playId(soundId: Int, volume: Float) {
        if (!enabled || released || soundId == 0 || ready[soundId] != true) return
        soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    private fun startEngineLoop() {
        if (!enabled || released || engineLoopId == 0 || ready[engineLoopId] != true) return
        if (engineStreamId != 0) return
        engineStreamId = soundPool.play(engineLoopId, EngineVolume, EngineVolume, 0, -1, 1f)
    }

    private fun stopEngineLoop() {
        if (engineStreamId != 0) {
            soundPool.stop(engineStreamId)
            engineStreamId = 0
        }
    }

    private fun load(context: Context, root: String, fileName: String): Int = runCatching {
        context.assets.openFd("$root/$fileName").use { descriptor ->
            soundPool.load(descriptor, 1)
        }
    }.getOrDefault(0)
}
