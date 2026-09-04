package com.aectann.battlecity

import battlecity.shared.generated.resources.Res
import com.aectann.battlecity.engine.BattleCityLevelData
import com.aectann.battlecity.engine.BattleCityLevelParser
import com.aectann.battlecity.engine.BattleCityMaxDifficulty
import com.aectann.battlecity.engine.BattleCityMaxStage
import com.aectann.battlecity.engine.BattleCitySoundEvent
import com.aectann.battlecity.engine.BattleCityStageInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi

private const val AudioSfx = "files/battle_city/audio/sfx"
private const val AudioMusic = "files/battle_city/audio/music_loops"

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
        val clips = mutableMapOf<TanksClip, ByteArray>()
        TanksClip.entries.forEach { clip ->
            runCatching { Res.readBytes(clip.path) }.onSuccess { clips[clip] = it }
        }
        clips.toMap().also { soundCache = it }
    }
}
