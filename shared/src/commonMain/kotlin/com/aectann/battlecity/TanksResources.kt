package com.aectann.battlecity

import battlecity.shared.generated.resources.Res
import com.aectann.battlecity.engine.BattleCityLevelData
import com.aectann.battlecity.engine.BattleCityLevelParser
import com.aectann.battlecity.engine.BattleCityMaxDifficulty
import com.aectann.battlecity.engine.BattleCityMaxStage
import com.aectann.battlecity.engine.BattleCitySoundEvent
import com.aectann.battlecity.engine.BattleCityStageInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi

private const val AudioSfx = "files/battle_city/audio/sfx"
private const val AudioMusic = "files/battle_city/audio/music_loops"
private const val MenuLevelPath = "files/battle_city/data/levels/menu_demo.json"

/** Every sound clip the game ships, addressed by its packed resource path. */
enum class TanksClip(val path: String) {
    Shoot("$AudioSfx/shoot.wav"),
    ExplosionBig("$AudioSfx/explosion_big.wav"),
    ExplosionSmall("$AudioSfx/explosion_small.wav"),
    HitBrick("$AudioSfx/hit_brick.wav"),
    HitSteel("$AudioSfx/hit_steel.wav"),
    PowerUp("$AudioSfx/powerup.wav"),
    ExtraLife("$AudioSfx/extra_life.wav"),
    GameOver("$AudioSfx/game_over.wav"),
    MenuSelect("$AudioSfx/menu_select.wav"),
    StageStart("$AudioMusic/stage_start_jingle.wav"),
    EngineLoop("$AudioMusic/engine_rumble_loop.wav");

    companion object {
        fun of(event: BattleCitySoundEvent): TanksClip = when (event) {
            BattleCitySoundEvent.Shoot -> Shoot
            BattleCitySoundEvent.ExplosionBig -> ExplosionBig
            BattleCitySoundEvent.ExplosionSmall -> ExplosionSmall
            BattleCitySoundEvent.HitBrick -> HitBrick
            BattleCitySoundEvent.HitSteel -> HitSteel
            BattleCitySoundEvent.PowerUp -> PowerUp
            BattleCitySoundEvent.ExtraLife -> ExtraLife
            BattleCitySoundEvent.GameOver -> GameOver
            BattleCitySoundEvent.StageStart -> StageStart
        }
    }
}

/**
 * Background music. The files are CC0 chiptunes by Juhani Junkala ("Retro Game Music Pack"),
 * levelled and encoded to MP3 because it is the one format every target, Safari included, plays.
 *
 * A file holds [repeats] back-to-back copies of a [loopSeconds]-long seamless loop. MP3 pads
 * the start and end of a file with a few milliseconds that decoders trim differently, so a
 * player that can loop a window of the file (Web Audio) loops one that starts half a loop in
 * and runs a whole number of loops, well clear of both ends: the music is the same on both
 * sides of that seam whatever the decoder did with the padding. A player that can only loop
 * the whole file hears the padding once per file, not once per loop.
 */
enum class TanksMusic(val path: String, val loopSeconds: Double, val repeats: Int) {
    Menu("$AudioMusic/menu_theme.mp3", loopSeconds = 11.294127, repeats = 4),
    Battle("$AudioMusic/battle_theme.mp3", loopSeconds = 74.254150, repeats = 2);

    /** The window a seek-capable player should loop, in seconds from the start of the file. */
    val loopStartSeconds: Double get() = loopSeconds / 2
    val loopEndSeconds: Double get() = loopStartSeconds + loopSeconds * (repeats - 1)
}

/**
 * Reads the packed game data. Resource access is the only platform-shaped part of loading,
 * and Compose Resources makes it common, so this whole file is shared.
 */
object TanksResources {

    private val levelCache = mutableMapOf<Int, BattleCityLevelData>()
    private val levelMutex = Mutex()

    private var stageInfoCache: Map<Int, BattleCityStageInfo>? = null
    private val stageInfoMutex = Mutex()

    private var soundCache: Map<TanksClip, ByteArray>? = null
    private val soundMutex = Mutex()

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadLevel(stage: Int): BattleCityLevelData = levelMutex.withLock {
        levelCache[stage]?.let { return@withLock it }
        val json = Res.readBytes(BattleCityLevelParser.stagePath(stage)).decodeToString()
        BattleCityLevelParser.parse(json).also { levelCache[stage] = it }
    }

    /** One music file, read when it is first wanted rather than with the clips: it is large. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadMusic(track: TanksMusic): ByteArray = Res.readBytes(track.path)

    /** The menu's attract-mode battlefield; not a campaign stage. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadMenuLevel(): BattleCityLevelData =
        BattleCityLevelParser.parse(Res.readBytes(MenuLevelPath).decodeToString())

    /** Star rating plus map of every stage, for the picker. Read once, then cached. */
    suspend fun loadStageInfos(): Map<Int, BattleCityStageInfo> = stageInfoMutex.withLock {
        stageInfoCache?.let { return@withLock it }
        val infos = (1..BattleCityMaxStage).associateWith { stage ->
            runCatching { loadLevel(stage) }
                .map { level ->
                    BattleCityStageInfo(
                        difficulty = (level.difficulty ?: 1).coerceIn(1, BattleCityMaxDifficulty),
                        grid = level.grid
                    )
                }
                .getOrElse { BattleCityStageInfo(1, emptyList()) }
        }
        infos.also { stageInfoCache = it }
    }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadSoundBank(): Map<TanksClip, ByteArray> = soundMutex.withLock {
        soundCache?.let { return@withLock it }
        // Requested together for the same reason as the sprites: on the web each is a round trip.
        val clips = coroutineScope {
            TanksClip.entries.map { clip ->
                async { runCatching { Res.readBytes(clip.path) }.getOrNull()?.let { clip to it } }
            }.awaitAll()
        }
        clips.filterNotNull().toMap().also { soundCache = it }
    }
}
