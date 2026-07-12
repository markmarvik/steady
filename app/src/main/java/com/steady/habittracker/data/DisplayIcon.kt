package com.steady.habittracker.data

/**
 * Display helpers for optional habit/group emojis (#29).
 * Empty icon → first letter of [name], or a neutral bullet.
 */
object DisplayIcon {
    fun glyph(icon: String?, name: String): String {
        val trimmed = icon?.trim().orEmpty()
        if (trimmed.isNotEmpty()) return trimmed
        val c = name.trim().firstOrNull()
        return c?.uppercaseChar()?.toString() ?: "•"
    }

    fun label(icon: String?, name: String): String {
        val trimmed = icon?.trim().orEmpty()
        return if (trimmed.isNotEmpty()) "$trimmed $name" else name
    }

    /** Curated set for longevity / habits / day parts (emoji picker). */
    val CURATED: List<String> = listOf(
        // Day parts / groups
        "🌅", "☀️", "🌤️", "🌙", "⭐", "💤", "🛏️", "⏰",
        // Movement
        "💪", "🏃", "🚶", "🧘", "🏋️", "🚴", "🏊", "🤸",
        // Nutrition / supplements
        "💊", "🥗", "🥩", "💧", "🍵", "☕", "🍎", "🥦",
        // Mind / focus
        "🧠", "📖", "✍️", "🎯", "💡", "🎧", "🙏", "❤️",
        // Hygiene / body
        "🪥", "🚿", "🧼", "🩺", "🌡️", "🧬", "🛡️", "✨",
        // Misc
        "🔥", "⚡", "🌱", "🏆", "📌", "✓", "•", "◆"
    )
}

fun Habit.displayGlyph(): String = DisplayIcon.glyph(icon, name)
fun Habit.displayLabel(): String = DisplayIcon.label(icon, name)
fun Group.displayGlyph(): String = DisplayIcon.glyph(icon, name)
fun Group.displayLabel(): String = DisplayIcon.label(icon, name)
