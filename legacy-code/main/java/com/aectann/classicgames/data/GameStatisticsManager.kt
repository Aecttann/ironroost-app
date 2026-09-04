package com.aectann.classicgames.data

import android.content.Context
import com.aectann.classicgames.ui.screens.GameResult
import kotlin.apply
import androidx.core.content.edit

class GameStatisticsManager(context: Context) {
    private val sharedPreferences =
        context.getSharedPreferences("game_statistics", Context.MODE_PRIVATE)
    private val sharedPreferences2 = context.getSharedPreferences("game_data", Context.MODE_PRIVATE)

    fun addDebugInstallTokensIfNeeded(count: Int): Boolean {
        if (sharedPreferences2.getBoolean(KEY_DEBUG_INSTALL_TOKENS_ADDED, false)) {
            return false
        }

        sharedPreferences2.edit {
            putInt(KEY_IN_GAME_TOKENS, getInGameTokens() + count)
            putBoolean(KEY_DEBUG_INSTALL_TOKENS_ADDED, true)
        }
        return true
    }

    // Отримання кількості зіграних ігор
    fun getTotalGames(): Int = sharedPreferences.getInt("total_games", 0)

    // Отримання кількості перемог
    fun getPlayerWins(): Int = sharedPreferences.getInt("player_wins", 0)

    // Отримання кількості нічиїх
    fun getDraws(): Int = sharedPreferences.getInt("draws", 0)

    // Отримання кількості знайдених зображень
    fun getSearchedImagesCount(): Int = sharedPreferences.getInt("searched_images_count", 0)
    // Збереження кількості знайдених зображень
    fun putSearchedImagesCount() {
        sharedPreferences.edit {
            apply {
                putInt("searched_images_count", getSearchedImagesCount() + 1)
            }
        }
    }

    // Отримання кількості збережених зображень в SD
    fun getSavedSDImagesCount(): Int = sharedPreferences.getInt("saved_sd_count", 0)
    // Збереження кількості збережених зображень в SD
    fun putSavedSDImagesCount() {
        sharedPreferences.edit {
            apply {
                putInt("saved_sd_count", getSavedSDImagesCount() + 1)
            }
        }
    }

    // Отримання кількості збережених зображень в HD
    fun getSavedHDImagesCount(): Int = sharedPreferences.getInt("saved_hd_count", 0)
    // Збереження кількості збережених зображень в HD
    fun putSavedHDImagesCount() {
        sharedPreferences.edit {
            apply {
                putInt("saved_hd_count", getSavedHDImagesCount() + 1)
            }
        }
    }

    // Отримання кількості ігр в змійку
    fun getSnakePlayedCount(): Int = sharedPreferences.getInt("snake_played_count", 0)
    // Збереження кількості ігр в змійку
    fun putSnakePlayedCount() {
        sharedPreferences.edit {
            apply {
                putInt("snake_played_count", getSnakePlayedCount() + 1)
            }
        }
    }

    // Отримання кількості виграних ігр в змійку
    fun getSnakeWonCount(): Int = sharedPreferences.getInt("snake_won_count", 0)
    // Збереження кількості виграних ігр в змійку
    fun putSnakeWonCount() {
        sharedPreferences.edit {
            apply {
                putInt("snake_won_count", getSnakeWonCount() + 1)
            }
        }
    }

    // Оновлення статистики після гри
    fun getTanksPlayedCount(): Int = sharedPreferences.getInt("tanks_played_count", 0)

    fun putTanksPlayedCount() {
        sharedPreferences.edit {
            apply {
                putInt("tanks_played_count", getTanksPlayedCount() + 1)
            }
        }
    }

    fun getTanksWonCount(): Int = sharedPreferences.getInt("tanks_won_count", 0)

    fun putTanksWonCount() {
        sharedPreferences.edit {
            apply {
                putInt("tanks_won_count", getTanksWonCount() + 1)
            }
        }
    }

    /**
     * Stage progress lives in the game data file, not the statistics file, so that
     * "reset statistics" cannot silently re-lock every tank stage.
     */
    fun getTanksHighestCompletedStage(): Int {
        val stored = sharedPreferences2.getInt(KEY_TANKS_HIGHEST_COMPLETED_STAGE, -1)
        if (stored >= 0) return stored

        // One-off migration from the old location.
        val legacy = sharedPreferences.getInt(KEY_TANKS_HIGHEST_COMPLETED_STAGE, 0)
        sharedPreferences2.edit { putInt(KEY_TANKS_HIGHEST_COMPLETED_STAGE, legacy) }
        return legacy
    }

    fun updateTanksHighestCompletedStage(stageNumber: Int) {
        val currentStage = getTanksHighestCompletedStage()
        if (stageNumber <= currentStage) return

        sharedPreferences2.edit {
            putInt(KEY_TANKS_HIGHEST_COMPLETED_STAGE, stageNumber)
        }
    }

    fun updateStatistics(result: GameResult) {
        sharedPreferences.edit().apply {
            putInt("total_games", getTotalGames() + 1)
            if (result.equals(GameResult.PLAYER_WIN)) putInt("player_wins", getPlayerWins() + 1)
            if (result.equals(GameResult.DRAW)) putInt("draws", getDraws() + 1)
        }.apply()
    }

    // Отримання кількості токенів
    fun getInGameTokens(): Int = sharedPreferences2.getInt(KEY_IN_GAME_TOKENS, 0)

    // оновлення / додавання кількості токенів
    fun putInGameTokens(count: Int){
        sharedPreferences2.edit().apply {
            putInt(KEY_IN_GAME_TOKENS, getInGameTokens() + count)
        }.apply()
    }
    // оновлення / віднімання кількості токенів
    fun delInGameTokens(count: Int){
        sharedPreferences2.edit().apply {
            putInt(KEY_IN_GAME_TOKENS, getInGameTokens() - count)
        }.apply()
    }

    // Отримання кількості преміум токенів
    fun getInGamePremiumTokens(): Int = sharedPreferences2.getInt("inGamePremiumTokens", 0)

    // оновлення / додавання кількості преміум токенів
    fun putInGamePremiumTokens(count: Int){
        sharedPreferences2.edit().apply {
            putInt("inGamePremiumTokens", getInGamePremiumTokens() + count)
        }.apply()
    }
    // оновлення / віднімання кількості преміум токенів
    fun delInGamePremiumTokens(count: Int){
        sharedPreferences2.edit().apply {
            putInt("inGamePremiumTokens", getInGamePremiumTokens() - count)
        }.apply()
    }


    // Скидання статистики
    fun resetStatistics() {
        sharedPreferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_IN_GAME_TOKENS = "inGameTokens"
        const val KEY_DEBUG_INSTALL_TOKENS_ADDED = "debugInstallTokensAdded"
        const val KEY_TANKS_HIGHEST_COMPLETED_STAGE = "tanks_highest_completed_stage"
    }
}
