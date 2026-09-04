package com.aectann.classicgames.battlecity

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private const val AssetRoot = "battle_city/assets"

/** Raised when the sprite set cannot be read, so the screen can show an error instead of crashing. */
class BattleCityAssetException(message: String, cause: Throwable? = null) : Exception(message, cause)

class BattleCityAssets private constructor(
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
            return images["player_green_level${level}_$direction"]
                ?: images["player_green_level1_$direction"]
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

    /** Sprite used for the pending-enemy column next to the board. */
    fun enemyQueueIcon(): ImageBitmap? = images["enemy_basic_up"]

    companion object {
        private const val KeyBrick = "tile_brick"

        @Volatile
        private var cached: BattleCityAssets? = null

        /**
         * Decodes the sprite set once per process. Safe to call from any background thread;
         * a second caller simply reuses the finished set.
         */
        fun load(context: Context): BattleCityAssets {
            cached?.let { return it }
            synchronized(this) {
                cached?.let { return it }
                val loaded = decodeAll(context.applicationContext)
                cached = loaded
                return loaded
            }
        }

        private fun decodeAll(context: Context): BattleCityAssets {
            val assetManager = context.assets
            val paths = buildMap {
                put(KeyBrick, "$AssetRoot/graphics/tiles/brick_damage_0.png")
                put("tile_steel", "$AssetRoot/graphics/tiles/steel.png")
                put("tile_water_0", "$AssetRoot/graphics/tiles/water_0.png")
                put("tile_water_1", "$AssetRoot/graphics/tiles/water_1.png")
                put("tile_ice", "$AssetRoot/graphics/tiles/ice.png")
                put("tile_forest_0", "$AssetRoot/graphics/tiles/forest_0.png")
                put("tile_forest_1", "$AssetRoot/graphics/tiles/forest_1.png")
                put("base_alive", "$AssetRoot/graphics/ui/base_eagle_alive.png")
                put("base_destroyed", "$AssetRoot/graphics/ui/base_eagle_destroyed.png")
                put("ui_life", "$AssetRoot/graphics/ui/life_tank_icon.png")
                put("ui_flag", "$AssetRoot/graphics/ui/flag_stage.png")

                repeat(4) { frame ->
                    put("explosion_$frame", "$AssetRoot/graphics/effects/explosion_$frame.png")
                    put("spawn_$frame", "$AssetRoot/graphics/effects/spawn_$frame.png")
                }
                repeat(2) { frame ->
                    put("shield_$frame", "$AssetRoot/graphics/effects/shield_$frame.png")
                }

                put("powerup_star", "$AssetRoot/graphics/powerups/star.png")
                put("powerup_gun", "$AssetRoot/graphics/powerups/gun_1990_variant.png")
                put("powerup_helmet", "$AssetRoot/graphics/powerups/helmet.png")
                put("powerup_timer", "$AssetRoot/graphics/powerups/timer.png")
                put("powerup_shovel", "$AssetRoot/graphics/powerups/shovel.png")
                put("powerup_grenade", "$AssetRoot/graphics/powerups/grenade.png")
                put("powerup_tank_life", "$AssetRoot/graphics/powerups/tank_life.png")
                put("powerup_boat", "$AssetRoot/graphics/powerups/boat_1990_variant.png")

                BattleCityDirection.entries.forEach { direction ->
                    val suffix = direction.assetSuffix
                    repeat(4) { index ->
                        val level = index + 1
                        put(
                            "player_green_level${level}_$suffix",
                            "$AssetRoot/graphics/tanks/player/green_level${level}_$suffix.png"
                        )
                    }
                    put("enemy_basic_$suffix", "$AssetRoot/graphics/tanks/enemy/basic_$suffix.png")
                    put("enemy_fast_$suffix", "$AssetRoot/graphics/tanks/enemy/fast_$suffix.png")
                    put("enemy_power_$suffix", "$AssetRoot/graphics/tanks/enemy/power_$suffix.png")
                    put("enemy_armor_green_$suffix", "$AssetRoot/graphics/tanks/enemy/armor_green_$suffix.png")
                    put("enemy_armor_gray_$suffix", "$AssetRoot/graphics/tanks/enemy/armor_gray_$suffix.png")
                    put("enemy_bonus_$suffix", "$AssetRoot/graphics/tanks/enemy/bonus_flash_$suffix.png")
                    put("bullet_$suffix", "$AssetRoot/graphics/effects/bullet_$suffix.png")
                }
            }

            val decoded = HashMap<String, ImageBitmap>(paths.size)
            val missing = mutableListOf<String>()
            paths.forEach { (key, path) ->
                val image = runCatching {
                    assetManager.open(path).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
                if (image == null) missing.add(path) else decoded[key] = image
            }

            if (decoded[KeyBrick] == null || decoded["tile_steel"] == null) {
                throw BattleCityAssetException("Missing core tank sprites: ${missing.take(5)}")
            }
            return BattleCityAssets(decoded)
        }
    }
}
