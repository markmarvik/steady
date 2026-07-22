package com.steady.habittracker.data

/**
 * Minimal phone-use habits for morning + bedtime routines.
 * Separate habit ids (not multi-group) so AM/PM never share a checkbox.
 */
object SleepPhoneBlock {

    fun isEnabled(data: AppData): Boolean =
        data.sleepPhonePrefs.enabled ||
            data.habits.any { !it.archived && it.extensionType == ExtensionType.SLEEP_PHONE }

    /**
     * Upsert or archive AM/PM phone-guard habits from prefs.
     * Creates Evening group when needed (same approach as oral hygiene).
     */
    fun apply(data: AppData, prefs: SleepPhonePrefs): AppData {
        if (!prefs.enabled) {
            val archived = data.habits.map { h ->
                if (!h.archived && h.extensionType == ExtensionType.SLEEP_PHONE) {
                    h.copy(archived = true)
                } else {
                    h
                }
            }
            return data.copy(habits = archived, sleepPhonePrefs = prefs)
        }

        val resolved = OralHygieneBlock.resolveMorningEveningGroups(data)
            ?: return data.copy(sleepPhonePrefs = prefs)
        var next = resolved.first.copy(sleepPhonePrefs = prefs)
        val (mornId, evenId) = resolved.second
        val pts = prefs.pointValue.coerceIn(1, 50)
        val canSkip = !prefs.essential
        val byId = next.habits.associateBy { it.id }.toMutableMap()
        val sleepTag = next.tags.firstOrNull {
            it.id == TagIds.SLEEP || it.name.equals("Sleep", true)
        }?.id ?: TagIds.SLEEP
        if (next.tags.none { it.id == sleepTag }) {
            next = next.withAddedTag(
                Tag(id = TagIds.SLEEP, name = "Sleep", order = next.tags.size)
            )
        }

        fun upsert(slot: String, name: String, groupId: String, icon: String, desc: String) {
            val id = SleepPhoneSlots.stableHabitId(slot)
            val existing = byId[id]
            val order = existing?.order
                ?: (byId.values.filter { it.groupId == groupId }.maxOfOrNull { it.order }?.plus(1) ?: 0)
            byId[id] = Habit(
                id = id,
                name = name,
                description = desc,
                groupId = groupId,
                type = HabitType.CHECKBOX,
                order = order,
                canSkip = canSkip,
                archived = false,
                tags = listOf(sleepTag),
                showPreset = ShowPreset.DAILY,
                additionalGroupIds = emptyList(),
                icon = icon,
                extensionType = ExtensionType.SLEEP_PHONE,
                extensionConfig = ExtensionConfig(sleepPhoneSlot = slot),
                pointValue = pts
            )
        }

        val desired = mutableSetOf<String>()
        if (prefs.morningEnabled) {
            desired += SleepPhoneSlots.MORNING
            upsert(
                slot = SleepPhoneSlots.MORNING,
                name = prefs.morningName.ifBlank { "Phone later" },
                groupId = mornId,
                icon = "📵",
                desc = "Delay first phone use · track screen until ${prefs.morningTrackUntilHour}:00"
            )
        }
        if (prefs.eveningEnabled) {
            desired += SleepPhoneSlots.EVENING
            upsert(
                slot = SleepPhoneSlots.EVENING,
                name = prefs.eveningName.ifBlank { "Phone parked" },
                groupId = evenId,
                icon = "🌙",
                desc = "Park phone before bed · track screen after ${prefs.eveningTrackFromHour}:00"
            )
        }

        for ((id, h) in byId.toList()) {
            if (h.extensionType == ExtensionType.SLEEP_PHONE &&
                h.extensionConfig.sleepPhoneSlot !in desired &&
                !h.archived
            ) {
                byId[id] = h.copy(archived = true)
            }
        }

        return next.copy(habits = byId.values.toList())
    }
}
