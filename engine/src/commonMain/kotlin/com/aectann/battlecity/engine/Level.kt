package com.aectann.battlecity.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Number of stages shipped with the game. */
const val BattleCityMaxStage = 35

/** Highest star rating a stage can carry. */
const val BattleCityMaxDifficulty = 5

@Serializable
data class BattleCityLevelData(
    val id: String,
    val note: String? = null,
    val gridSize: BattleCityGridSize,
    val spawnPoints: BattleCitySpawnPoints,
    val tilesLegend: Map<String, String>? = null,
    val grid: List<String>,
    val enemyGroups: List<BattleCityEnemyGroup>,
    val difficulty: Int? = null
)

@Serializable
data class BattleCityGridSize(
    val cols: Int,
    val rows: Int,
    val unitTilePx: Int = 16
)

@Serializable
data class BattleCitySpawnPoints(
    val enemy: List<List<Int>>,
    val player1: List<Int>,
    val player2: List<Int>? = null,
    val base: List<Int>
)

@Serializable
data class BattleCityEnemyGroup(
    val enemy: String,
    val count: Int
)

/** Star rating plus the raw map, enough to draw a stage thumbnail without loading sprites. */
data class BattleCityStageInfo(
    val difficulty: Int,
    val grid: List<String>
)

/**
 * Parses and validates stage files. Reading the bytes is the platform's job; this stays pure
 * so the same checks run in unit tests and on both platforms.
 */
object BattleCityLevelParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(source: String): BattleCityLevelData {
        val level = json.decodeFromString<BattleCityLevelData>(source)
        validate(level)
        return level
    }

    fun stagePath(stageNumber: Int): String {
        val stage = stageNumber.coerceIn(1, BattleCityMaxStage)
        val name = "prototype_stage_" + stage.toString().padStart(2, '0')
        return "files/battle_city/data/levels/prototype_13x13/$name.json"
    }

    private fun validate(level: BattleCityLevelData) {
        require(level.gridSize.cols > 0 && level.gridSize.rows > 0) {
            "${level.id}: grid size must be positive"
        }
        require(level.grid.size == level.gridSize.rows) {
            "${level.id}: expected ${level.gridSize.rows} rows, got ${level.grid.size}"
        }
        level.grid.forEachIndexed { index, row ->
            require(row.length == level.gridSize.cols) {
                "${level.id}: row $index has ${row.length} cells, expected ${level.gridSize.cols}"
            }
        }
        require(level.spawnPoints.base.size == 2) { "${level.id}: base needs an x and a y" }
        require(level.spawnPoints.player1.size == 2) { "${level.id}: player1 needs an x and a y" }
        require(level.spawnPoints.enemy.isNotEmpty()) { "${level.id}: no enemy spawn points" }
        level.spawnPoints.enemy.forEach { spawn ->
            require(spawn.size == 2) { "${level.id}: enemy spawn needs an x and a y" }
        }
        require(level.enemyGroups.sumOf { it.count } > 0) { "${level.id}: no enemies" }
    }
}
