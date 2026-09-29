package com.aectann.battlecity.ui

import com.aectann.battlecity.SilentSoundPlayer
import com.aectann.battlecity.TanksClip
import com.aectann.battlecity.TanksMusic
import com.aectann.battlecity.TanksSoundPlayer

/**
 * The app's clip and music player, plus whatever was asked of it before it was ready.
 *
 * The bank is decoded asynchronously, while the frame loop, the key handler and the on-screen
 * buttons all reach for the player from callbacks that do not see snapshot writes on Kotlin/Wasm.
 * Holding the player in a plain field here keeps any of them from being left with the silent
 * placeholder, and the stage jingle asked for during that gap is played as soon as it can be.
 * Music works the same way: the track the screen wants is remembered, and handed over — with its
 * file, once read — to whichever player is installed.
 */
internal class TanksSoundBank {
    private var player: TanksSoundPlayer = SilentSoundPlayer
    private var pendingClip: TanksClip? = null
    private var enabled: Boolean = true
    private var music: TanksMusic? = null
    private val musicFiles = mutableMapOf<TanksMusic, ByteArray>()

    fun install(decoded: TanksSoundPlayer) {
        player.release()
        player = decoded
        decoded.setEnabled(enabled)
        musicFiles.forEach { (track, bytes) -> decoded.loadMusic(track, bytes) }
        decoded.setMusic(music)
        pendingClip?.let { decoded.play(it) }
        pendingClip = null
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        player.setEnabled(value)
    }

    /** Only the newest waiting clip is kept: by the time the bank lands the rest are stale. */
    fun play(clip: TanksClip) {
        if (player === SilentSoundPlayer) pendingClip = clip else player.play(clip)
    }

    fun setEngineRunning(running: Boolean) = player.setEngineRunning(running)

    fun loadMusic(track: TanksMusic, bytes: ByteArray) {
        musicFiles[track] = bytes
        player.loadMusic(track, bytes)
    }

    fun setMusic(track: TanksMusic?) {
        if (music == track) return
        music = track
        player.setMusic(track)
    }

    fun release() {
        player.release()
        player = SilentSoundPlayer
        pendingClip = null
    }
}
