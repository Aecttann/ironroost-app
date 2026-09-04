package com.aectann.classicgames.data

import com.aectann.classicgames.R

enum class DifficultyLevel {
    EASY, MEDIUM, HARD
}
fun DifficultyLevel.getStringRes(): Int {
    return when (this) {
        DifficultyLevel.EASY -> R.string.select_difficulty_easy
        DifficultyLevel.MEDIUM -> R.string.select_difficulty_medium
        DifficultyLevel.HARD -> R.string.select_difficulty_hard
    }
}