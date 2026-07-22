package com.steady.habittracker.data

/**
 * Pure helpers for the universal Oral Hygiene block.
 * Syncs step habits into morning + evening planner groups.
 */
object OralHygieneBlock {

    data class StepDef(
        val key: String,
        val defaultName: String,
        val icon: String,
        val blurb: String
    )

    fun stepDefs(prefs: OralHygienePrefs): List<StepDef> = listOf(
        StepDef(
            OralHygieneSteps.BRUSH,
            "Brush teeth",
            "🪥",
            "${prefs.brushMinutes.coerceIn(1, 5)} min · full coverage"
        ),
        StepDef(OralHygieneSteps.FLOSS, "Floss", "🧵", "Between all teeth"),
        StepDef(OralHygieneSteps.TONGUE, "Tongue scrape", "👅", "Gentle full surface"),
        StepDef(
            OralHygieneSteps.WATER,
            prefs.waterFlushLabel.ifBlank { "Water floss / rinse" },
            "💦",
            "Water flosser or thorough rinse"
        ),
        StepDef(OralHygieneSteps.MOUTHWASH, "Mouthwash", "🧴", "Optional antimicrobial rinse")
    )

    fun defsForEnabled(prefs: OralHygienePrefs): List<StepDef> {
        val keys = prefs.activeStepKeys().toSet()
        return stepDefs(prefs).filter { it.key in keys }
    }

    /**
     * Resolve morning + evening group ids. Creates an Evening group if only one
     * suitable group exists so steps always have two slots.
     */
    fun resolveMorningEveningGroups(data: AppData): Pair<AppData, Pair<String, String>>? {
        val active = data.groups.filter { !it.archived }.sortedBy { it.order }
        if (active.isEmpty()) return null

        fun find(hint: String, nameParts: List<String>): Group? =
            active.firstOrNull { it.timeHint.equals(hint, true) }
                ?: active.firstOrNull { g ->
                    nameParts.any { g.name.contains(it, ignoreCase = true) }
                }

        val morning = find("MORNING", listOf("morning", "wake", "am"))
            ?: active.first()
        var evening = find("BEDTIME", listOf("bed", "wind", "evening", "night", "pm"))
            ?: find("EVENING", listOf("evening", "night"))

        var next = data
        if (evening == null || evening.id == morning.id) {
            // Prefer an existing non-morning group before creating
            evening = active.firstOrNull {
                it.id != morning.id &&
                    (it.timeHint.equals("BEDTIME", true) ||
                        it.timeHint.equals("EVENING", true) ||
                        it.name.contains("even", true) ||
                        it.name.contains("bed", true) ||
                        it.name.contains("night", true))
            }
            if (evening == null || evening.id == morning.id) {
                val existing = active.firstOrNull { it.id == "g_even" || it.id == "g_oral_even" }
                if (existing != null && existing.id != morning.id) {
                    evening = existing
                } else {
                    val newG = Group(
                        id = "g_oral_even",
                        name = "Evening Routine",
                        timeHint = "BEDTIME",
                        order = (active.maxOfOrNull { it.order } ?: 0) + 1,
                        icon = "🌙"
                    )
                    next = next.withAddedGroup(newG)
                    evening = newG
                }
            }
        }
        return next to (morning.id to evening!!.id)
    }

    /**
     * Apply prefs: enable → upsert step habits in morning+evening;
     * disable → archive oral hygiene habits.
     */
    fun apply(data: AppData, prefs: OralHygienePrefs): AppData {
        if (!prefs.enabled) {
            val archived = data.habits.map { h ->
                if (!h.archived && h.extensionType == ExtensionType.ORAL_HYGIENE) {
                    h.copy(archived = true)
                } else h
            }
            return data.copy(habits = archived, oralHygienePrefs = prefs)
        }

        val resolved = resolveMorningEveningGroups(data) ?: return data.copy(oralHygienePrefs = prefs)
        var next = resolved.first.withOralHygienePrefs(prefs)
        val (mornId, evenId) = resolved.second
        val enabledDefs = defsForEnabled(prefs)
        val enabledKeys = enabledDefs.map { it.key }.toSet()
        val hygieneTag = next.tags.firstOrNull {
            it.id == TagIds.HYGIENE || it.name.equals("Hygiene", true)
        }?.id ?: TagIds.HYGIENE

        // Ensure hygiene tag exists
        if (next.tags.none { it.id == hygieneTag }) {
            next = next.withAddedTag(
                Tag(id = TagIds.HYGIENE, name = "Hygiene", order = next.tags.size)
            )
        }

        val pts = prefs.pointValue.coerceIn(1, 50)
        val canSkip = !prefs.essential
        val stack = prefs.stackOrder

        // Stable order for stacking
        var previousId: String? = null
        val byId = next.habits.associateBy { it.id }.toMutableMap()

        for (def in enabledDefs) {
            val id = OralHygieneSteps.stableHabitId(def.key)
            val desc = when (def.key) {
                OralHygieneSteps.BRUSH ->
                    "${prefs.brushMinutes.coerceIn(1, 5)} min brush · morning & evening"
                else -> "${def.blurb} · morning & evening"
            }
            val existing = byId[id]
            val baseOrder = existing?.order
                ?: (byId.values.filter { it.groupId == mornId }.maxOfOrNull { it.order }?.plus(1) ?: 0)
            val additional = if (prefs.morningAndEvening && evenId != mornId) {
                listOf(evenId)
            } else {
                emptyList()
            }
            val habit = Habit(
                id = id,
                name = def.defaultName,
                description = desc,
                groupId = mornId,
                type = HabitType.CHECKBOX,
                order = baseOrder,
                canSkip = canSkip,
                archived = false,
                tags = listOf(hygieneTag),
                showPreset = ShowPreset.DAILY,
                additionalGroupIds = additional,
                icon = def.icon,
                afterHabitId = if (stack) previousId else null,
                extensionType = ExtensionType.ORAL_HYGIENE,
                extensionConfig = ExtensionConfig(oralStepKey = def.key),
                pointValue = pts
            )
            byId[id] = habit
            previousId = id
        }

        // Archive steps that are no longer enabled
        for ((id, h) in byId.toList()) {
            if (h.extensionType == ExtensionType.ORAL_HYGIENE &&
                h.extensionConfig.oralStepKey !in enabledKeys &&
                !h.archived
            ) {
                byId[id] = h.copy(archived = true)
            }
        }

        // If somehow zero steps selected, keep prefs but no active oral habits
        next = next.copy(habits = byId.values.toList())
        return next
    }

    fun isEnabled(data: AppData): Boolean =
        data.oralHygienePrefs.enabled ||
            data.habits.any { !it.archived && it.extensionType == ExtensionType.ORAL_HYGIENE }
}
