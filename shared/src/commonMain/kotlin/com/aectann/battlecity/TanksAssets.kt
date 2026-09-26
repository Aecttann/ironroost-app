package com.aectann.battlecity

import androidx.compose.ui.graphics.ImageBitmap
import battlecity.shared.generated.resources.Res
import com.aectann.battlecity.engine.BattleCityDirection
import com.aectann.battlecity.engine.BattleCityPowerUpType
import com.aectann.battlecity.engine.BattleCityTankRenderState
import com.aectann.battlecity.engine.BattleCityTileSnapshot
import com.aectann.battlecity.engine.battleCityPlayerId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

private const val GraphicsRoot = "files/battle_city/graphics"
private const val KeyBrick = "tile_brick"
private val PlayerTwoId = battleCityPlayerId(1)

/** Raised when the sprite set cannot be read, so the screen can show an error instead of crashing. */
class TanksAssetException(message: String) : Exception(message)

/**
 * The decoded sprite set. Loaded once per process and shared; the maps are read-only after
 * construction so any thread or coroutine can use the same instance.
 */
class TanksAssets private constructor(
    private val images: Map<String, ImageBitmap>
) {

    fun tile(tile: Char, frame: Int, snapshot: BattleCityTileSnapshot): ImageBitmap? = when (tile) {
        'B' -> images[KeyBrick]
        'S' -> images["tile_steel"]
        'W' -> images[if (frame % 2 == 0) "tile_water_0" else "tile_water_1"]
        'I' -> images["tile_ice"]
        'F' -> images[if (frame % 2 == 0) "tile_forest_0" else "tile_forest_1"]
        'H' -> images[if (snapshot.baseDestroyed) "base_destroyed" else "base_alive"]
        else -> null
    }

    /** Source sprite for brick quarters; the renderer crops it per surviving quarter. */
    fun brick(): ImageBitmap? = images[KeyBrick]

    fun tank(tank: BattleCityTankRenderState): ImageBitmap? {
        val direction = tank.direction.assetSuffix
        if (tank.isPlayer) {
            val level = tank.level.coerceIn(1, 4)
            // Player two drives the yellow tank, which is how the original tells the pair apart.
            val colour = if (tank.id == PlayerTwoId) "yellow" else "green"
            return images["player_${colour}_level${level}_$direction"]
                ?: images["player_${colour}_level1_$direction"]
        }
        if (tank.isBonus && tank.bonusFlashOn) {
            return images["enemy_bonus_$direction"]
        }
        if (tank.type == "armor") {
            val key = if (tank.isDamaged) "enemy_armor_gray_$direction" else "enemy_armor_green_$direction"
            return images[key] ?: images["enemy_basic_$direction"]
        }
        return images["enemy_${tank.type}_$direction"] ?: images["enemy_basic_$direction"]
    }

    fun bullet(direction: BattleCityDirection): ImageBitmap? =
        images["bullet_${direction.assetSuffix}"]

    fun explosion(frame: Int): ImageBitmap? = images["explosion_${frame.coerceIn(0, 3)}"]

    fun spawn(frame: Int): ImageBitmap? = images["spawn_${frame.coerceIn(0, 3)}"]

    fun shield(frame: Int): ImageBitmap? = images["shield_${frame.coerceIn(0, 1)}"]

    fun powerUp(type: BattleCityPowerUpType): ImageBitmap? = images["powerup_${type.assetKey}"]

    fun lifeIcon(): ImageBitmap? = images["ui_life"]

    fun flagIcon(): ImageBitmap? = images["ui_flag"]

    /** Any sprite by its internal key; the collection cards address art this way. */
    fun sprite(key: String): ImageBitmap? = images[key]

    /** Player tank facing up, used as the menu emblem. */
    fun menuTankSprite(): ImageBitmap? = images["player_green_level1_up"]

    /** Sprite used for the pending-enemy column next to the board. */
    fun enemyQueueIcon(): ImageBitmap? = images["enemy_basic_up"]

    companion object {
        private val mutex = Mutex()
        private var cached: TanksAssets? = null

        /** Decodes the sprite set once per process; later callers reuse the finished set. */
        suspend fun load(): TanksAssets = mutex.withLock {
            cached ?: decodeAll().also { cached = it }
        }

        @OptIn(ExperimentalResourceApi::class)
        private suspend fun decodeAll(): TanksAssets {
            // Every sprite is its own file, and on the web its own request. Read one after
            // another, ninety of them kept a portal player on the loading panel for ten seconds
            // at an ordinary CDN round trip — with the run already going underneath.
            val loaded = coroutineScope {
                spritePaths().map { (key, path) ->
                    async {
                        val image = runCatching { Res.readBytes(path).decodeToImageBitmap() }.getOrNull()
                        Triple(key, path, image)
                    }
                }.awaitAll()
            }

            val decoded = mutableMapOf<String, ImageBitmap>()
            val missing = mutableListOf<String>()
            loaded.forEach { (key, path, image) ->
                if (image == null) missing.add(path) else decoded[key] = image
            }

            if (decoded[KeyBrick] == null || decoded["tile_steel"] == null) {
                throw TanksAssetException("Missing core tank sprites: ${missing.take(5)}")
            }
            return TanksAssets(decoded.toMap())
        }

        /** Exposed so a test can assert every declared sprite is actually packed. */
        fun spritePaths(): Map<String, String> = buildMap {
            put(KeyBrick, "$GraphicsRoot/tiles/brick_damage_0.png")
            put("tile_steel", "$GraphicsRoot/tiles/steel.png")
            put("tile_water_0", "$GraphicsRoot/tiles/water_0.png")
            put("tile_water_1", "$GraphicsRoot/tiles/water_1.png")
            put("tile_ice", "$GraphicsRoot/tiles/ice.png")
            put("tile_forest_0", "$GraphicsRoot/tiles/forest_0.png")
            put("tile_forest_1", "$GraphicsRoot/tiles/forest_1.png")
            put("base_alive", "$GraphicsRoot/ui/base_eagle_alive.png")
            put("base_destroyed", "$GraphicsRoot/ui/base_eagle_destroyed.png")
            put("ui_life", "$GraphicsRoot/ui/life_tank_icon.png")
            put("ui_flag", "$GraphicsRoot/ui/flag_stage.png")

            repeat(4) { frame ->
                put("explosion_$frame", "$GraphicsRoot/effects/explosion_$frame.png")
                put("spawn_$frame", "$GraphicsRoot/effects/spawn_$frame.png")
            }
            repeat(2) { frame ->
                put("shield_$frame", "$GraphicsRoot/effects/shield_$frame.png")
            }

            put("powerup_star", "$GraphicsRoot/powerups/star.png")
            put("powerup_gun", "$GraphicsRoot/powerups/gun_1990_variant.png")
            put("powerup_helmet", "$GraphicsRoot/powerups/helmet.png")
            put("powerup_timer", "$GraphicsRoot/powerups/timer.png")
            put("powerup_shovel", "$GraphicsRoot/powerups/shovel.png")
            put("powerup_grenade", "$GraphicsRoot/powerups/grenade.png")
            put("powerup_tank_life", "$GraphicsRoot/powerups/tank_life.png")
            put("powerup_boat", "$GraphicsRoot/powerups/boat_1990_variant.png")

            BattleCityDirection.entries.forEach { direction ->
                val suffix = direction.assetSuffix
                repeat(4) { index ->
                    val level = index + 1
                    listOf("green", "yellow").forEach { colour ->
                        put(
                            "player_${colour}_level${level}_$suffix",
                            "$GraphicsRoot/tanks/player/${colour}_level${level}_$suffix.png"
                        )
                    }
                }
                put("enemy_basic_$suffix", "$GraphicsRoot/tanks/enemy/basic_$suffix.png")
                put("enemy_fast_$suffix", "$GraphicsRoot/tanks/enemy/fast_$suffix.png")
                put("enemy_power_$suffix", "$GraphicsRoot/tanks/enemy/power_$suffix.png")
                put("enemy_armor_green_$suffix", "$GraphicsRoot/tanks/enemy/armor_green_$suffix.png")
                put("enemy_armor_gray_$suffix", "$GraphicsRoot/tanks/enemy/armor_gray_$suffix.png")
                put("enemy_bonus_$suffix", "$GraphicsRoot/tanks/enemy/bonus_flash_$suffix.png")
                put("bullet_$suffix", "$GraphicsRoot/effects/bullet_$suffix.png")
            }
        }
    }
}
