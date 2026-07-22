package com.steady.habittracker.data

/**
 * Catalog of default exercises for workout routines — calisthenics, gym, longevity,
 * and stretching. Used when creating / editing routines and seeding Blueprint templates.
 */
object ExerciseLibrary {

    data class CatalogItem(
        val id: String,
        val name: String,
        val category: String,
        val sets: Int = 3,
        val reps: String = "8-12",
        val restSec: Int = 60,
        val notes: String = "",
        val muscleGroup: String = "",
        /** Suggested tracking: reps | time | weight */
        val metric: String = "reps"
    )

    fun toDef(item: CatalogItem, order: Int = 0) = ExerciseDef(
        id = item.id,
        name = item.name,
        sets = item.sets,
        reps = item.reps,
        restSec = item.restSec,
        notes = item.notes,
        muscleGroup = item.muscleGroup,
        order = order
    )

    val ALL: List<CatalogItem> by lazy {
        calisthenics() + gymStrength() + longevity() + stretching() + cardio()
    }

    fun byCategory(): Map<String, List<CatalogItem>> =
        ALL.groupBy { it.category }

    private fun calisthenics() = listOf(
        CatalogItem("lib_pu", "Push-ups", "Calisthenics", 3, "8-20", 60, "Full ROM; elevate feet to progress", "Chest", "reps"),
        CatalogItem("lib_dips", "Dips (parallel bar / bench)", "Calisthenics", 3, "6-12", 90, "Shoulders down & back", "Chest", "reps"),
        CatalogItem("lib_pull", "Pull-ups", "Calisthenics", 3, "3-10", 120, "Band assist or negatives ok", "Back", "reps"),
        CatalogItem("lib_chin", "Chin-ups", "Calisthenics", 3, "3-10", 120, "Supinated grip", "Back", "reps"),
        CatalogItem("lib_invrow", "Inverted rows", "Calisthenics", 3, "8-15", 60, "Body under bar / rings", "Back", "reps"),
        CatalogItem("lib_pike", "Pike push-ups", "Calisthenics", 3, "6-12", 90, "Vertical press progression", "Shoulders", "reps"),
        CatalogItem("lib_hs", "Handstand hold (wall)", "Calisthenics", 3, "20-45s", 60, "Hollow body", "Shoulders", "time"),
        CatalogItem("lib_squat_bw", "Bodyweight squats", "Calisthenics", 3, "12-25", 60, "Depth you control", "Legs", "reps"),
        CatalogItem("lib_lunge", "Walking / reverse lunges", "Calisthenics", 3, "8-12/side", 60, "", "Legs", "reps"),
        CatalogItem("lib_split", "Split squats / Bulgarian", "Calisthenics", 3, "8-12/side", 75, "Rear foot elevated ok", "Legs", "reps"),
        CatalogItem("lib_shrimp", "Shrimp squat progression", "Calisthenics", 3, "3-8/side", 90, "Single-leg strength", "Legs", "reps"),
        CatalogItem("lib_pistol", "Pistol squat progression", "Calisthenics", 3, "3-8/side", 90, "Box / TRX assist first", "Legs", "reps"),
        CatalogItem("lib_hip_bridge", "Glute bridge / hip thrust (BW)", "Calisthenics", 3, "10-15", 45, "Posterior chain", "Legs", "reps"),
        CatalogItem("lib_plank", "Front plank", "Calisthenics", 3, "30-90s", 45, "Brace, breathe", "Core", "time"),
        CatalogItem("lib_side_plank", "Side plank", "Calisthenics", 2, "20-45s/side", 30, "", "Core", "time"),
        CatalogItem("lib_hollow", "Hollow body hold", "Calisthenics", 3, "20-40s", 45, "Low back pressed down", "Core", "time"),
        CatalogItem("lib_legraise", "Hanging / lying leg raises", "Calisthenics", 3, "8-15", 60, "", "Core", "reps"),
        CatalogItem("lib_superman", "Superman / back extension", "Calisthenics", 3, "10-15", 45, "Spinal health", "Back", "reps"),
        CatalogItem("lib_burpee", "Burpees", "Calisthenics", 3, "8-15", 60, "Optional for conditioning", "Full body", "reps")
    )

    private fun gymStrength() = listOf(
        CatalogItem("lib_sq", "Barbell back squat", "Gym", 3, "5-8", 150, "Longevity compound", "Legs", "weight"),
        CatalogItem("lib_fsq", "Front squat / goblet squat", "Gym", 3, "6-10", 120, "", "Legs", "weight"),
        CatalogItem("lib_dl", "Deadlift (conventional / trap)", "Gym", 3, "3-6", 180, "Leave 1–2 reps in reserve", "Legs", "weight"),
        CatalogItem("lib_rdl", "Romanian deadlift", "Gym", 3, "6-10", 120, "Hamstrings + hinge pattern", "Legs", "weight"),
        CatalogItem("lib_bp", "Bench press", "Gym", 3, "5-8", 150, "", "Chest", "weight"),
        CatalogItem("lib_ohp", "Overhead press", "Gym", 3, "5-8", 120, "Standing preferred", "Shoulders", "weight"),
        CatalogItem("lib_row", "Barbell / chest-supported row", "Gym", 3, "6-10", 90, "Balance pressing volume", "Back", "weight"),
        CatalogItem("lib_lat", "Lat pulldown / assisted pull-up", "Gym", 3, "8-12", 90, "", "Back", "weight"),
        CatalogItem("lib_rdl_db", "DB RDL / single-leg RDL", "Gym", 3, "8-12", 75, "Balance + hinge", "Legs", "weight"),
        CatalogItem("lib_farmers", "Farmer carries", "Gym", 3, "30-60s", 60, "Grip + posture longevity", "Full body", "time"),
        CatalogItem("lib_calf", "Calf raises (standing)", "Gym", 3, "12-20", 45, "Ankle resilience", "Legs", "reps"),
        CatalogItem("lib_face", "Face pulls", "Gym", 3, "12-20", 45, "Shoulder health", "Shoulders", "reps"),
        CatalogItem("lib_curl", "Biceps curls", "Gym", 2, "8-15", 45, "", "Arms", "weight"),
        CatalogItem("lib_tri", "Triceps pressdown / extension", "Gym", 2, "8-15", 45, "", "Arms", "weight"),
        CatalogItem("lib_legpress", "Leg press", "Gym", 3, "8-12", 90, "Joint-friendly volume", "Legs", "weight"),
        CatalogItem("lib_hipthrust", "Barbell hip thrust", "Gym", 3, "8-12", 90, "Glute strength", "Legs", "weight")
    )

    private fun longevity() = listOf(
        CatalogItem("lib_zone2", "Zone 2 cardio (walk / bike / row)", "Longevity", 1, "30-60 min", 0,
            "Conversational pace; nose breathing preferred", "Cardio", "time"),
        CatalogItem("lib_vo2", "VO2 max intervals", "Longevity", 4, "1-4 min hard", 120,
            "1–2×/week max after warm-up", "Cardio", "time"),
        CatalogItem("lib_rucking", "Ruck / weighted walk", "Longevity", 1, "20-45 min", 0,
            "Light pack; upright posture", "Cardio", "time"),
        CatalogItem("lib_balance", "Single-leg balance", "Longevity", 2, "30-45s/side", 15,
            "Eyes open then progress closed", "Balance", "time"),
        CatalogItem("lib_sitstand", "Sit-to-stand (from floor / chair)", "Longevity", 2, "5-10", 30,
            "Independence metric", "Legs", "reps"),
        CatalogItem("lib_grip", "Grip holds / towel hang", "Longevity", 3, "20-40s", 45,
            "Grip correlates with longevity markers", "Arms", "time"),
        CatalogItem("lib_carry", "Suitcase / farmer carry", "Longevity", 3, "40-60s", 60,
            "Anti-lateral-flexion core", "Full body", "time"),
        CatalogItem("lib_stepup", "Step-ups", "Longevity", 3, "8-12/side", 60,
            "Stair resilience", "Legs", "reps"),
        CatalogItem("lib_push_lon", "Push pattern (push-up or press)", "Longevity", 3, "6-12", 90,
            "Maintain upper push strength", "Chest", "reps"),
        CatalogItem("lib_pull_lon", "Pull pattern (row or pull-up)", "Longevity", 3, "6-12", 90,
            "Balance posture", "Back", "reps")
    )

    private fun stretching() = listOf(
        CatalogItem("lib_90_90", "90/90 hip stretch", "Stretching", 2, "45-60s/side", 10, "Hip capsule mobility", "Hips", "time"),
        CatalogItem("lib_couch", "Couch stretch / hip flexor", "Stretching", 2, "45-60s/side", 10, "Counter sitting", "Hips", "time"),
        CatalogItem("lib_pigeon", "Pigeon / figure-4", "Stretching", 2, "45-60s/side", 10, "", "Hips", "time"),
        CatalogItem("lib_ham", "Hamstring stretch (supine strap)", "Stretching", 2, "45s/side", 10, "No bounce", "Legs", "time"),
        CatalogItem("lib_calf_str", "Calf / soleus wall stretch", "Stretching", 2, "45s/side", 10, "", "Legs", "time"),
        CatalogItem("lib_catcow", "Cat-cow", "Stretching", 2, "8-12", 5, "Spinal wave", "Spine", "reps"),
        CatalogItem("lib_thoracic", "Thoracic openers / open book", "Stretching", 2, "6-10/side", 10, "Desk posture", "Spine", "reps"),
        CatalogItem("lib_world_great", "World's greatest stretch", "Stretching", 2, "4-6/side", 10, "Full-body mobility flow", "Full body", "reps"),
        CatalogItem("lib_child", "Child's pose", "Stretching", 1, "60-90s", 0, "", "Recovery", "time"),
        CatalogItem("lib_thread", "Thread the needle", "Stretching", 2, "6-8/side", 10, "T-spine rotation", "Spine", "reps"),
        CatalogItem("lib_doorway", "Doorway chest stretch", "Stretching", 2, "30-45s", 10, "Open anterior chain", "Chest", "time"),
        CatalogItem("lib_neck", "Neck CARs / gentle mobility", "Stretching", 1, "5/side", 5, "Slow controlled circles", "Neck", "reps"),
        CatalogItem("lib_shoulder_cars", "Shoulder CARs", "Stretching", 1, "5/side", 10, "Largest pain-free circle", "Shoulders", "reps"),
        CatalogItem("lib_hip_cars", "Hip CARs", "Stretching", 1, "5/side", 10, "", "Hips", "reps"),
        CatalogItem("lib_forward", "Standing forward fold", "Stretching", 1, "45-60s", 0, "Soft knees if needed", "Legs", "time"),
        CatalogItem("lib_quad", "Standing / side-lying quad stretch", "Stretching", 2, "30-45s/side", 10, "", "Legs", "time")
    )

    private fun cardio() = listOf(
        CatalogItem("lib_easy_run", "Easy jog / run", "Cardio", 1, "20-40 min", 0, "Conversational", "Cardio", "time"),
        CatalogItem("lib_row", "Rowing machine", "Cardio", 1, "15-30 min", 0, "", "Cardio", "time"),
        CatalogItem("lib_bike", "Stationary / outdoor bike", "Cardio", 1, "20-45 min", 0, "", "Cardio", "time"),
        CatalogItem("lib_swim", "Swim", "Cardio", 1, "20-40 min", 0, "Low impact", "Cardio", "time")
    )
}
