package com.steady.habittracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "steady_prefs")

class HabitRepository(private val context: Context) {

    private val json = Json { 
        prettyPrint = true 
        ignoreUnknownKeys = true 
    }
    
    private val DATA_KEY = stringPreferencesKey("app_data")

    val appDataFlow: Flow<AppData> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[DATA_KEY] ?: ""
            if (jsonString.isBlank()) {
                getDefaultData()
            } else {
                try {
                    json.decodeFromString<AppData>(jsonString)
                } catch (e: Exception) {
                    getDefaultData()
                }
            }
        }

    suspend fun saveData(data: AppData) {
        context.dataStore.edit { preferences ->
            preferences[DATA_KEY] = json.encodeToString(data)
        }
    }

    private fun getDefaultData(): AppData {
        val defaultHabits = listOf(
            Habit("sun", "Morning Sunlight", "Aligns circadian rhythm, boosts mood and energy."),
            Habit("move", "Move Your Body", "20+ min walk or exercise for brain and longevity."),
            Habit("protein", "Protein First", "Prioritize protein for muscle, satiety and metabolism."),
            Habit("focus", "Deep Focus Block", "Undistracted meaningful work or learning session."),
            Habit("winddown", "Wind Down Routine", "Protects sleep quality with consistent bedtime."),
            Habit("hydrate", "Stay Hydrated", "Better energy, focus and recovery."),
            Habit("reflect", "Gratitude / Reflection", "Note things you're grateful for.")
        )
        return AppData(habits = defaultHabits)
    }
}