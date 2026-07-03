package com.steady.habittracker.data

import kotlinx.serialization.Serializable

@Serializable
data class Habit(
    val id: String,
    val name: String,
    val why: String = ""
)

@Serializable
data class AppData(
    val habits: List<Habit> = emptyList(),
    val completions: Map<String, List<String>> = emptyMap() // date -> list of habit ids
)