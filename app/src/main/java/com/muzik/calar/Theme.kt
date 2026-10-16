package com.muzik.calar

import android.content.Context
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class AppBackground(
    val label: String,
    val colors: List<Color>,
    val light: Boolean = false
) {
    NIGHT("Gece", listOf(Color(0xFF0B1A3A), Color(0xFF0B0F1E))),
    PURPLE("Mor", listOf(Color(0xFF3B1E6E), Color(0xFF0E0E12))),
    FOREST("Orman", listOf(Color(0xFF0E3B2E), Color(0xFF0A0F0D))),
    SUNSET("Gün batımı", listOf(Color(0xFF6A2C3E), Color(0xFF14080C))),
    OCEAN("Okyanus", listOf(Color(0xFF0F4C75), Color(0xFF0B1620))),
    BLACK("Siyah", listOf(Color(0xFF000000), Color(0xFF000000))),
    SKY("Gökyüzü", listOf(Color(0xFFBFE3FF), Color(0xFFEAF6FF)), light = true),
    CREAM("Krem", listOf(Color(0xFFF7EBCB), Color(0xFFFFF8E6)), light = true),
    ROSE("Gül", listOf(Color(0xFFF8C8D8), Color(0xFFFDEEF3)), light = true),
    MINT("Nane", listOf(Color(0xFFC6EED8), Color(0xFFEEFAF3)), light = true),
    LAVENDER("Lavanta", listOf(Color(0xFFD9CCF5), Color(0xFFF4F0FC)), light = true),
    PEACH("Şeftali", listOf(Color(0xFFFFD7B5), Color(0xFFFFF3EA)), light = true),
    WHITE("Beyaz", listOf(Color(0xFFFFFFFF), Color(0xFFF4F4F6)), light = true);

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
