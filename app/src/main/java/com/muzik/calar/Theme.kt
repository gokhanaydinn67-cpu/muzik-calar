package com.muzik.calar

import android.content.Context
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class AppBackground(val label: String, val colors: List<Color>) {
    NIGHT("Gece", listOf(Color(0xFF0B1A3A), Color(0xFF0B0F1E))),
    PURPLE("Mor", listOf(Color(0xFF3B1E6E), Color(0xFF0E0E12))),
    FOREST("Orman", listOf(Color(0xFF0E3B2E), Color(0xFF0A0F0D))),
    SUNSET("Gün batımı", listOf(Color(0xFF6A2C3E), Color(0xFF14080C))),
    OCEAN("Okyanus", listOf(Color(0xFF0F4C75), Color(0xFF0B1620))),
    BLACK("Siyah", listOf(Color(0xFF000000), Color(0xFF000000)));

    fun brush(): Brush = Brush.verticalGradient(colors)
}

object ThemeStore {
    private const val PREFS = "theme"
    private const val KEY = "background"

    fun load(context: Context): AppBackground {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        return AppBackground.values().firstOrNull { it.name == name } ?: AppBackground.NIGHT
    }

    fun save(context: Context, bg: AppBackground) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, bg.name)
            .apply()
    }
}
