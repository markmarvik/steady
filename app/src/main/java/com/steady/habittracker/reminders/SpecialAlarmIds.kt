package com.steady.habittracker.reminders

/** Stable reminder / alarm ids for non-group notifications (#32, #36). */
object SpecialAlarmIds {
    const val MOTIVATIONAL_QUOTE = "sys_motivational_quote"
    const val RANDOM_CHECKIN = "sys_random_checkin"
    const val MISSED_HABITS = "sys_missed_habits"
    const val HABIT_REMINDER_PREFIX = "habit_rem_"

    fun habitReminderId(habitId: String) = "$HABIT_REMINDER_PREFIX$habitId"
}
