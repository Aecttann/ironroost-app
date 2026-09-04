package com.aectann.battlecity.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BattleCityLevelParserTest {

    private fun stageJson(
        rows: String = """
            ".............",
            ".............",
            ".............",
            ".............",
            ".............",
            ".............",
            ".............",
            ".............",
            ".............",
            ".............",
            ".............",
            ".....BBB.....",
            ".....BHB....."
        """.trimIndent(),
        cols: Int = 13,
        rowCount: Int = 13,
        enemyCount: Int = 20
    ) = """
        {
          "id": "unit_test_stage",
          "note": "fixture",
          "gridSize": { "cols": $cols, "rows": $rowCount, "unitTilePx": 16 },
          "spawnPoints": {
            "enemy": [[0, 0], [6, 0], [12, 0]],
            "player1": [4, 12],
            "player2": [8, 12],
            "base": [6, 12]
          },
          "tilesLegend": { ".": "empty", "B": "brick", "H": "base" },
          "grid": [
        $rows
          ],
          "enemyGroups": [
            { "enemy": "basic", "count": $enemyCount }
          ],
          "difficulty": 3
        }
    """.trimIndent()

    @Test
    fun parsesAWellFormedStage() {
        val level = BattleCityLevelParser.parse(stageJson())
        assertEquals("unit_test_stage", level.id)
        assertEquals(13, level.gridSize.cols)
        assertEquals(13, level.grid.size)
        assertEquals(3, level.difficulty)
        assertEquals(20, level.enemyGroups.sumOf { it.count })
        assertEquals('H', level.grid[12][6])
    }

    @Test
    fun rejectsARowCountThatContradictsTheDeclaredSize() {
        val error = assertFailsWith<IllegalArgumentException> {
            BattleCityLevelParser.parse(stageJson(rowCount = 12))
        }
        assertTrue(
            error.message.orEmpty().contains("rows"),
            "unhelpful message: ${error.message}"
        )
    }

    @Test
    fun rejectsARowWhoseWidthContradictsTheDeclaredSize() {
        val error = assertFailsWith<IllegalArgumentException> {
            BattleCityLevelParser.parse(stageJson(cols = 12))
        }
        assertTrue(
            error.message.orEmpty().contains("cells"),
            "unhelpful message: ${error.message}"
        )
    }

    @Test
    fun rejectsAStageWithNoEnemies() {
        assertFailsWith<IllegalArgumentException> {
            BattleCityLevelParser.parse(stageJson(enemyCount = 0))
        }
    }

    @Test
    fun unknownFieldsAreToleratedSoTheFormatCanGrow() {
        val withExtra = stageJson().replaceFirst(
            "\"id\": \"unit_test_stage\",",
            "\"id\": \"unit_test_stage\", \"someFutureField\": 7,"
        )
        assertEquals("unit_test_stage", BattleCityLevelParser.parse(withExtra).id)
    }

    @Test
    fun stagePathsAreClampedToTheShippedRange() {
        assertTrue(BattleCityLevelParser.stagePath(1).endsWith("prototype_stage_01.json"))
        assertTrue(BattleCityLevelParser.stagePath(35).endsWith("prototype_stage_35.json"))
        assertTrue(BattleCityLevelParser.stagePath(0).endsWith("prototype_stage_01.json"))
        assertTrue(BattleCityLevelParser.stagePath(99).endsWith("prototype_stage_35.json"))
    }
}
