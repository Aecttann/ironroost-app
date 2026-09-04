package com.aectann.classicgames.data

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.core.content.edit
import com.aectann.classicgames.R
import java.util.Locale
import java.util.UUID

class PreferencesManager {

    fun updateLanguage(context: Context, language: String) {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("app_language", language).apply()

        val config = Configuration(context.resources.configuration)
        val locale = Locale(language)
        Locale.setDefault(locale)
        config.setLocale(locale)

        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    @Composable
    fun getButtonColors(isSelected: Boolean): ButtonColors {
        val baseColor = colorResource(id = R.color.purple_200)
        return ButtonDefaults.buttonColors(
            containerColor = if (isSelected) baseColor.copy(alpha = 1f) else baseColor.copy(alpha = 0.7f),
            contentColor = Color.White
        )
    }

    fun getSelectedLanguage(context: Context): String {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("app_language", "en") ?: "en"
    }

    fun getSelectedDifficulty(context: Context): DifficultyLevel {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val level = sharedPreferences.getString("difficulty_level", DifficultyLevel.EASY.name)
        return DifficultyLevel.valueOf(level ?: DifficultyLevel.EASY.name)
    }

    fun getSelectedBoardSize(context: Context): Int {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("board_size", 5)
    }

    fun saveBoardSize(context: Context, size: Int) {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("board_size", size).apply()
    }

    fun hasSeenWelcomeMessage(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("has_seen_welcome", false)
    }

    fun markWelcomeMessageSeen(context: Context) {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("has_seen_welcome", true).apply()
    }

    fun hasSeenInformationMessage(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("has_seen_information", false)
    }

    fun markInformationMessageSeen(context: Context) {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("has_seen_information", true).apply()
    }

    fun hasSeenSnakeControlsMessage(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("has_seen_SnakeControls", false)
    }

    fun markSnakeControlsMessageSeen(context: Context) {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("has_seen_SnakeControls", true).apply()
    }

    fun shouldShowTanksFullscreenPrompt(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return !sharedPreferences.getBoolean("skip_tanks_fullscreen_prompt", false)
    }

    fun disableTanksFullscreenPrompt(context: Context) {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("skip_tanks_fullscreen_prompt", true).apply()
    }

    fun isSoundEnabled(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("sound_enabled", true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit { putBoolean("sound_enabled", enabled) }
    }

    fun getUserId(context: Context): String {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var userId = sharedPreferences.getString("user_id", null)

        if (userId == null) {
            userId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("user_id", userId).apply()
        }

        return userId
    }

}
