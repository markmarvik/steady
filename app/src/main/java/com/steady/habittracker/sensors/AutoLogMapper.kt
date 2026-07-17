package com.steady.habittracker.sensors

import com.steady.habittracker.data.AutoLogMode
import com.steady.habittracker.data.AutoSource
import com.steady.habittracker.data.AutoSuggestion
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitType

/**
 * Pure mapping from raw sensor readings → habit values + notes.
 * No Android APIs — unit-testable.
 */
object AutoLogMapper {

    data class Reading(
        val source: AutoSource,
        /** Primary numeric observation (minutes, lux, steps, dB, …). */
        val raw: Double,
        val label: String = ""
    )

    data class Mapped(
        val value: Double,
        val note: String
    )

    fun sourceLabel(source: AutoSource): String = when (source) {
        AutoSource.NONE -> "Manual"
        AutoSource.SCREEN_MINUTES -> "Screen time"
        AutoSource.SCREEN_AFTER_WINDDOWN -> "Evening screen"
        AutoSource.LIGHT_BEDTIME_AVG -> "Bedtime light"
        AutoSource.LIGHT_DARK_CHECK -> "Dark bedroom"
        AutoSource.NOISE_EVENING_DB -> "Ambient noise"
        AutoSource.PHONE_STEPS -> "Phone steps"
        AutoSource.GADGETBRIDGE_STEPS -> "Gadgetbridge steps"
        AutoSource.EXTERNAL_METRIC -> "External metric"
    }

    fun mapToHabitValue(habit: Habit, reading: Reading): Mapped? {
        if (habit.autoSource == AutoSource.NONE) return null
        // Reading source should match the habit link (engine always pairs them)
        if (reading.source != habit.autoSource) return null
        val src = sourceLabel(habit.autoSource)
        return when (habit.autoSource) {
            AutoSource.NONE -> null

            AutoSource.SCREEN_MINUTES -> {
                val minutes = reading.raw.coerceAtLeast(0.0)
                when (habit.type) {
                    HabitType.DURATION_MIN, HabitType.COUNTER ->
                        Mapped(minutes, "auto · $src · ${minutes.toInt()} min")
                    HabitType.CHECKBOX -> {
                        val maxOk = habit.autoThreshold ?: habit.target ?: 120.0
                        val done = if (minutes <= maxOk) 1.0 else 0.0
                        Mapped(done, "auto · $src · ${minutes.toInt()} min (limit ${maxOk.toInt()})")
                    }
                    else -> Mapped(minutes, "auto · $src")
                }
            }

            AutoSource.SCREEN_AFTER_WINDDOWN -> {
                val minutes = reading.raw.coerceAtLeast(0.0)
                when (habit.type) {
                    HabitType.DURATION_MIN, HabitType.COUNTER ->
                        Mapped(minutes, "auto · $src · ${minutes.toInt()} min after wind-down")
                    HabitType.CHECKBOX -> {
                        // Done = phone-free enough (under threshold)
                        val maxOk = habit.autoThreshold ?: habit.target ?: 15.0
                        val done = if (minutes <= maxOk) 1.0 else 0.0
                        Mapped(done, "auto · $src · ${minutes.toInt()} min (≤${maxOk.toInt()} to pass)")
                    }
                    else -> Mapped(minutes, "auto · $src")
                }
            }

            AutoSource.LIGHT_BEDTIME_AVG -> {
                val lux = reading.raw.coerceAtLeast(0.0)
                when (habit.type) {
                    HabitType.COUNTER, HabitType.DURATION_MIN ->
                        Mapped(lux, "auto · $src · ${lux.toInt()} lux")
                    HabitType.SCALE_1_5 -> {
                        // Map lux to 1–5 darkness scale (5 = very dark)
                        val scale = when {
                            lux < 5 -> 5.0
                            lux < 20 -> 4.0
                            lux < 50 -> 3.0
                            lux < 150 -> 2.0
                            else -> 1.0
                        }
                        Mapped(scale, "auto · $src · ${lux.toInt()} lux → $scale/5 dark")
                    }
                    HabitType.CHECKBOX -> {
                        val maxLux = habit.autoThreshold ?: 15.0
                        Mapped(if (lux <= maxLux) 1.0 else 0.0, "auto · $src · ${lux.toInt()} lux")
                    }
                    else -> Mapped(lux, "auto · $src")
                }
            }

            AutoSource.LIGHT_DARK_CHECK -> {
                val lux = reading.raw.coerceAtLeast(0.0)
                val maxLux = habit.autoThreshold ?: 15.0
                val done = if (lux <= maxLux) 1.0 else 0.0
                Mapped(done, "auto · dark check · ${lux.toInt()} lux (≤${maxLux.toInt()})")
            }

            AutoSource.NOISE_EVENING_DB -> {
                val db = reading.raw.coerceAtLeast(0.0)
                when (habit.type) {
                    HabitType.COUNTER, HabitType.DURATION_MIN ->
                        Mapped(db, "auto · $src · ~${db.toInt()} dB")
                    HabitType.SCALE_1_5 -> {
                        val scale = when {
                            db < 30 -> 1.0
                            db < 45 -> 2.0
                            db < 60 -> 3.0
                            db < 75 -> 4.0
                            else -> 5.0
                        }
                        Mapped(scale, "auto · $src · ~${db.toInt()} dB → $scale/5")
                    }
                    HabitType.CHECKBOX -> {
                        val maxDb = habit.autoThreshold ?: 50.0
                        Mapped(if (db <= maxDb) 1.0 else 0.0, "auto · quiet · ~${db.toInt()} dB")
                    }
                    else -> Mapped(db, "auto · $src")
                }
            }

            AutoSource.PHONE_STEPS,
            AutoSource.GADGETBRIDGE_STEPS -> {
                val steps = reading.raw.coerceAtLeast(0.0)
                when (habit.type) {
                    HabitType.COUNTER, HabitType.DURATION_MIN ->
                        Mapped(steps, "auto · $src · ${steps.toInt()} steps")
                    HabitType.CHECKBOX -> {
                        val goal = habit.autoThreshold ?: habit.target ?: 8000.0
                        Mapped(if (steps >= goal) 1.0 else 0.0, "auto · $src · ${steps.toInt()}/${goal.toInt()}")
                    }
                    else -> Mapped(steps, "auto · $src")
                }
            }

            AutoSource.EXTERNAL_METRIC -> {
                val v = reading.raw
                Mapped(v, "auto · external · ${habit.autoMetricKey.ifBlank { reading.label }} · $v")
            }
        }
    }

    fun toSuggestion(
        habit: Habit,
        date: String,
        reading: Reading,
        nowMs: Long = System.currentTimeMillis()
    ): AutoSuggestion? {
        val mapped = mapToHabitValue(habit, reading) ?: return null
        return AutoSuggestion(
            habitId = habit.id,
            date = date,
            value = mapped.value,
            note = mapped.note,
            source = habit.autoSource,
            observedAt = nowMs,
            resolved = false
        )
    }

    fun shouldAutoApply(habit: Habit): Boolean =
        habit.autoSource != AutoSource.NONE && habit.autoMode == AutoLogMode.AUTO_APPLY
}
