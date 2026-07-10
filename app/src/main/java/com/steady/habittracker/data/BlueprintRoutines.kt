package com.steady.habittracker.data

/**
 * Bryan Johnson Blueprint–inspired default exercise routines (#22).
 * Templates only — users edit freely. Ids are stable so re-load skips duplicates.
 */
object BlueprintRoutines {

    fun templates(): List<ExerciseRoutine> = listOf(
        fullBodyStrength(),
        zone2Cardio(),
        hiitIntervals(),
        mobilityNsdr()
    )

    private fun ex(
        id: String,
        name: String,
        sets: Int = 3,
        reps: String = "8-12",
        restSec: Int = 90,
        notes: String = "",
        muscle: String = "",
        order: Int = 0
    ) = ExerciseDef(id, name, sets, reps, restSec, notes, muscle, order)

    private fun fullBodyStrength() = ExerciseRoutine(
        id = "rt_full_body",
        name = "Full Body Strength",
        description = "Template — edit to your level. Full-body pull/push/squat/core (Blueprint-style).",
        estimatedDurationMin = 50,
        tags = listOf("Strength", "Movement"),
        showPreset = ShowPreset.CUSTOM_DAYS,
        weekdays = setOf(1, 3, 5), // Mon Wed Fri
        order = 0,
        exercises = listOf(
            ex("ex_fb_pull", "Pull-ups / Chin-ups / Rows", 3, "5-10", 90, "Full ROM; band assist ok", "Back", 0),
            ex("ex_fb_push", "Push-ups / Dips / Press", 3, "8-15", 90, "Chest/shoulders", "Chest", 1),
            ex("ex_fb_squat", "Squat / Lunge / Split squat", 3, "8-12", 90, "Legs + glutes", "Legs", 2),
            ex("ex_fb_hinge", "Hip hinge / RDL / Good morning", 3, "8-12", 90, "Posterior chain", "Legs", 3),
            ex("ex_fb_ohp", "Overhead press / Pike push-up", 3, "6-10", 90, "Shoulders", "Shoulders", 4),
            ex("ex_fb_core", "Core: plank / leg raise / hollow", 3, "30-60s or 8-15", 60, "Brace, breathe", "Core", 5),
            ex("ex_fb_face", "Face pulls / band pull-apart", 2, "12-20", 45, "Shoulder health", "Back", 6),
            ex("ex_fb_calf", "Calf raises", 2, "12-20", 45, "", "Legs", 7)
        )
    )

    private fun zone2Cardio() = ExerciseRoutine(
        id = "rt_zone2",
        name = "Zone 2 Cardio",
        description = "Low-intensity steady state. Conversational pace (Zone 2 HR).",
        estimatedDurationMin = 45,
        tags = listOf("Cardio", "Movement"),
        showPreset = ShowPreset.CUSTOM_DAYS,
        weekdays = setOf(2, 4, 6), // Tue Thu Sat
        order = 1,
        exercises = listOf(
            ex("ex_z2_main", "Brisk walk / Cycle / Row / Elliptical", 1, "30-60 min", 0,
                "Stay Zone 2: can talk in full sentences. Nose breathing preferred.", "Cardio", 0)
        )
    )

    private fun hiitIntervals() = ExerciseRoutine(
        id = "rt_hiit",
        name = "HIIT / VO2max Intervals",
        description = "Short hard efforts. Warm-up fully. 1–2× per week max.",
        estimatedDurationMin = 18,
        tags = listOf("HIIT", "Cardio", "Movement"),
        showPreset = ShowPreset.CUSTOM_DAYS,
        weekdays = setOf(2), // e.g. Tuesday
        order = 2,
        exercises = listOf(
            ex("ex_hi_warm", "Warm-up easy pace", 1, "5 min", 0, "Elevate HR gradually", "Cardio", 0),
            ex("ex_hi_work", "Hard interval (bike/row/sprint/burpee)", 6, "20-30s max", 60,
                "All-out effort; full recovery between", "Cardio", 1),
            ex("ex_hi_cool", "Cool-down easy", 1, "3-5 min", 0, "", "Cardio", 2)
        )
    )

    private fun mobilityNsdr() = ExerciseRoutine(
        id = "rt_mobility",
        name = "Mobility + NSDR",
        description = "Joints, stretch flow, optional non-sleep deep rest.",
        estimatedDurationMin = 20,
        tags = listOf("Mobility", "Movement"),
        showPreset = ShowPreset.DAILY,
        weekdays = setOf(1, 2, 3, 4, 5, 6, 7),
        order = 3,
        exercises = listOf(
            ex("ex_mob_hips", "Hip openers / 90-90", 2, "45-60s/side", 15, "", "Hips", 0),
            ex("ex_mob_spine", "Cat-cow / thoracic rotations", 2, "8-12", 15, "", "Spine", 1),
            ex("ex_mob_shoulders", "Shoulder CARs / band dislocates", 2, "8-10", 15, "", "Shoulders", 2),
            ex("ex_mob_nsdr", "NSDR / body scan / 4-7-8 breath", 1, "10 min", 0,
                "Optional deep rest after mobility", "Recovery", 3)
        )
    )
}
