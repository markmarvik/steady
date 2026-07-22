package com.steady.habittracker.data

/**
 * Default exercise routines (#22) — Blueprint-inspired plus calisthenics,
 * gym longevity, and dedicated stretch/mobility templates.
 * Ids are stable so re-load skips duplicates.
 */
object BlueprintRoutines {

    fun templates(): List<ExerciseRoutine> = listOf(
        fullBodyStrength(),
        calisthenicsFoundation(),
        gymLongevity(),
        zone2Cardio(),
        hiitIntervals(),
        mobilityStretch(),
        longevityDaily()
    )

    private fun fromLib(ids: List<String>, startOrder: Int = 0): List<ExerciseDef> {
        val byId = ExerciseLibrary.ALL.associateBy { it.id }
        return ids.mapIndexedNotNull { i, id ->
            byId[id]?.let { ExerciseLibrary.toDef(it, startOrder + i) }
        }
    }

    private fun fullBodyStrength() = ExerciseRoutine(
        id = "rt_full_body",
        name = "Full Body Strength",
        description = "Pull / push / squat / hinge / core — Blueprint-style template.",
        estimatedDurationMin = 50,
        tags = listOf("Strength", "Movement", "Gym"),
        showPreset = ShowPreset.CUSTOM_DAYS,
        weekdays = setOf(1, 3, 5),
        order = 0,
        exercises = fromLib(
            listOf("lib_pull", "lib_pu", "lib_sq", "lib_rdl", "lib_ohp", "lib_plank", "lib_face", "lib_calf")
        )
    )

    private fun calisthenicsFoundation() = ExerciseRoutine(
        id = "rt_calisthenics",
        name = "Calisthenics Foundation",
        description = "Bodyweight strength: push, pull, squat, core. Progress ROM and leverage.",
        estimatedDurationMin = 40,
        tags = listOf("Calisthenics", "Strength", "Movement"),
        showPreset = ShowPreset.CUSTOM_DAYS,
        weekdays = setOf(2, 4, 6),
        order = 1,
        exercises = fromLib(
            listOf(
                "lib_pu", "lib_invrow", "lib_squat_bw", "lib_dips", "lib_lunge",
                "lib_pike", "lib_plank", "lib_hollow", "lib_hip_bridge"
            )
        )
    )

    private fun gymLongevity() = ExerciseRoutine(
        id = "rt_gym_longevity",
        name = "Gym Longevity Strength",
        description = "Big compounds + carries for lifelong strength. Leave 1–2 RIR.",
        estimatedDurationMin = 55,
        tags = listOf("Gym", "Longevity", "Strength"),
        showPreset = ShowPreset.CUSTOM_DAYS,
        weekdays = setOf(1, 4),
        order = 2,
        exercises = fromLib(
            listOf(
                "lib_sq", "lib_bp", "lib_row", "lib_rdl", "lib_ohp",
                "lib_farmers", "lib_face", "lib_calf"
            )
        )
    )

    private fun zone2Cardio() = ExerciseRoutine(
        id = "rt_zone2",
        name = "Zone 2 Cardio",
        description = "Low-intensity steady state. Conversational pace (Zone 2 HR).",
        estimatedDurationMin = 45,
        tags = listOf("Cardio", "Longevity", "Movement"),
        showPreset = ShowPreset.CUSTOM_DAYS,
        weekdays = setOf(2, 4, 6),
        order = 3,
        exercises = fromLib(listOf("lib_zone2"))
    )

    private fun hiitIntervals() = ExerciseRoutine(
        id = "rt_hiit",
        name = "HIIT / VO2max Intervals",
        description = "Short hard efforts. Warm-up fully. 1–2× per week max.",
        estimatedDurationMin = 20,
        tags = listOf("HIIT", "Cardio", "Longevity"),
        showPreset = ShowPreset.CUSTOM_DAYS,
        weekdays = setOf(3),
        order = 4,
        exercises = listOf(
            ExerciseDef("ex_hi_warm", "Warm-up easy pace", 1, "5 min", 0, "Elevate HR gradually", "Cardio", 0),
            ExerciseLibrary.toDef(ExerciseLibrary.ALL.first { it.id == "lib_vo2" }, 1),
            ExerciseDef("ex_hi_cool", "Cool-down easy", 1, "3-5 min", 0, "", "Cardio", 2)
        )
    )

    private fun mobilityStretch() = ExerciseRoutine(
        id = "rt_mobility",
        name = "Mobility + Stretching",
        description = "Hips, spine, shoulders — daily joint health and recovery.",
        estimatedDurationMin = 20,
        tags = listOf("Mobility", "Stretching", "Longevity"),
        showPreset = ShowPreset.DAILY,
        weekdays = setOf(1, 2, 3, 4, 5, 6, 7),
        order = 5,
        exercises = fromLib(
            listOf(
                "lib_catcow", "lib_90_90", "lib_couch", "lib_thoracic",
                "lib_world_great", "lib_ham", "lib_doorway", "lib_child"
            )
        )
    )

    private fun longevityDaily() = ExerciseRoutine(
        id = "rt_longevity_daily",
        name = "Longevity Daily Minimum",
        description = "Short stack: balance, sit-to-stand, grip, easy Zone 2 walk.",
        estimatedDurationMin = 25,
        tags = listOf("Longevity", "Movement"),
        showPreset = ShowPreset.DAILY,
        weekdays = setOf(1, 2, 3, 4, 5, 6, 7),
        order = 6,
        exercises = fromLib(
            listOf("lib_balance", "lib_sitstand", "lib_grip", "lib_stepup", "lib_zone2")
        )
    )
}
