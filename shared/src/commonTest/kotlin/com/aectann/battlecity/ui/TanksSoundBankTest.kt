package com.aectann.battlecity.ui

import com.aectann.battlecity.TanksClip
import com.aectann.battlecity.TanksMusic
import com.aectann.battlecity.TanksSoundPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The bank exists to cover the gap before the clips finish decoding, so that is what is pinned. */
class TanksSoundBankTest {

    private class RecordingPlayer : TanksSoundPlayer {
        val played = mutableListOf<TanksClip>()
        var enabled: Boolean? = null
        val engineRuns = mutableListOf<Boolean>()
        var released = false
        val loadedMusic = mutableListOf<TanksMusic>()
        val musicAsked = mutableListOf<TanksMusic?>()

        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun play(clip: TanksClip) { played += clip }
        override fun setEngineRunning(running: Boolean) { engineRuns += running }
        override fun release() { released = true }
        override fun loadMusic(track: TanksMusic, bytes: ByteArray) { loadedMusic += track }
        override fun setMusic(track: TanksMusic?) { musicAsked += track }
    }

    @Test
    fun `music asked for before the player lands starts on arrival, file and all`() {
        val bank = TanksSoundBank()
        bank.setMusic(TanksMusic.Menu)
        bank.loadMusic(TanksMusic.Menu, byteArrayOf(1, 2, 3))

        val player = RecordingPlayer()
        bank.install(player)

        assertEquals(listOf(TanksMusic.Menu), player.loadedMusic)
        assertEquals(listOf<TanksMusic?>(TanksMusic.Menu), player.musicAsked)
    }

    @Test
    fun `a file that arrives after the player reaches it too`() {
        val bank = TanksSoundBank()
        val player = RecordingPlayer()
        bank.install(player)
        bank.setMusic(TanksMusic.Menu)

        bank.loadMusic(TanksMusic.Menu, byteArrayOf(1))

        assertEquals(listOf(TanksMusic.Menu), player.loadedMusic)
    }

    @Test
    fun `asking for the track already playing does not restart it`() {
        val bank = TanksSoundBank()
        val player = RecordingPlayer()
        bank.install(player)

        bank.setMusic(TanksMusic.Menu)
        bank.setMusic(TanksMusic.Menu)
        bank.setMusic(null)

        assertEquals(listOf<TanksMusic?>(null, TanksMusic.Menu, null), player.musicAsked)
    }

    @Test
    fun `a clip asked for before the bank lands is played on arrival`() {
        val bank = TanksSoundBank()
        val player = RecordingPlayer()

        bank.play(TanksClip.StageStart)
        assertTrue(player.played.isEmpty())

        bank.install(player)
        assertEquals(listOf(TanksClip.StageStart), player.played)
    }

    @Test
    fun `only the newest waiting clip survives the gap`() {
        val bank = TanksSoundBank()
        val player = RecordingPlayer()

        bank.play(TanksClip.Shoot)
        bank.play(TanksClip.StageStart)
        bank.install(player)

        assertEquals(listOf(TanksClip.StageStart), player.played)
    }

    @Test
    fun `the waiting clip is not replayed on the next install`() {
        val bank = TanksSoundBank()
        val first = RecordingPlayer()
        val second = RecordingPlayer()

        bank.play(TanksClip.StageStart)
        bank.install(first)
        bank.install(second)

        assertEquals(listOf(TanksClip.StageStart), first.played)
        assertTrue(second.played.isEmpty())
    }

    @Test
    fun `a sound setting chosen before the bank lands is carried over`() {
        val bank = TanksSoundBank()
        val player = RecordingPlayer()

        bank.setEnabled(false)
        bank.install(player)

        assertEquals(false, player.enabled)
    }

    @Test
    fun `installing a second player retires the first`() {
        val bank = TanksSoundBank()
        val first = RecordingPlayer()

        bank.install(first)
        bank.install(RecordingPlayer())

        assertTrue(first.released)
    }

    @Test
    fun `playing and steering reach the installed player`() {
        val bank = TanksSoundBank()
        val player = RecordingPlayer()
        bank.install(player)

        bank.play(TanksClip.Shoot)
        bank.setEngineRunning(true)

        assertEquals(listOf(TanksClip.Shoot), player.played)
        assertEquals(listOf(true), player.engineRuns)
    }

    @Test
    fun `release lets go of the player and forgets what was waiting`() {
        val bank = TanksSoundBank()
        val first = RecordingPlayer()
        bank.install(first)

        bank.play(TanksClip.Shoot)
        bank.release()
        assertTrue(first.released)

        val second = RecordingPlayer()
        bank.install(second)
        assertTrue(second.played.isEmpty())
        assertFalse(second.released)
    }
}
