package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.data.model.VoiceRoutine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "agent_flow_prefs")

class AppSettingsRepository(private val context: Context) {

    companion object {
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val KEY_AUTO_EXECUTE = booleanPreferencesKey("auto_execute")
        val KEY_TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
        val KEY_CUSTOM_ROUTINES_JSON = stringPreferencesKey("custom_routines_json")

        const val DEFAULT_MODEL = "gemini-3.5-flash"
    }

    /**
     * Flow for Gemini API Key. If user configured in UI, use that.
     * Otherwise fallback to BuildConfig.GEMINI_API_KEY if present.
     */
    val geminiApiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val userKey = prefs[KEY_GEMINI_API_KEY]?.trim()
        if (!userKey.isNullOrEmpty()) {
            userKey
        } else {
            // Check BuildConfig fallback
            try {
                val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
                val buildKey = field.get(null) as? String
                if (!buildKey.isNullOrBlank() && buildKey != "MY_GEMINI_API_KEY") buildKey else ""
            } catch (e: Exception) {
                ""
            }
        }
    }

    val selectedModelFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GEMINI_MODEL] ?: DEFAULT_MODEL
    }

    val autoExecuteFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_EXECUTE] ?: true
    }

    val ttsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTS_ENABLED] ?: true
    }

    val speechRateFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_SPEECH_RATE] ?: 1.0f
    }

    val customRoutinesFlow: Flow<List<VoiceRoutine>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_CUSTOM_ROUTINES_JSON]
        if (json.isNullOrBlank()) {
            getDefaultPresetRoutines()
        } else {
            parseRoutinesFromJson(json)
        }
    }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GEMINI_API_KEY] = apiKey.trim()
        }
    }

    suspend fun saveModel(model: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GEMINI_MODEL] = model
        }
    }

    suspend fun saveAutoExecute(auto: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_EXECUTE] = auto
        }
    }

    suspend fun saveTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TTS_ENABLED] = enabled
        }
    }

    suspend fun saveSpeechRate(rate: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SPEECH_RATE] = rate
        }
    }

    suspend fun saveRoutines(routines: List<VoiceRoutine>) {
        val jsonArray = JSONArray()
        routines.forEach { routine ->
            val obj = JSONObject().apply {
                put("id", routine.id)
                put("title", routine.title)
                put("triggerPhrase", routine.triggerPhrase)
                put("promptCommand", routine.promptCommand)
                put("iconName", routine.iconName)
                put("colorHex", routine.colorHex)
                put("isEnabled", routine.isEnabled)
                put("isPreset", routine.isPreset)
            }
            jsonArray.put(obj)
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_ROUTINES_JSON] = jsonArray.toString()
        }
    }

    suspend fun addRoutine(routine: VoiceRoutine) {
        val current = parseRoutinesFromJson(
            context.dataStore.data.map { it[KEY_CUSTOM_ROUTINES_JSON] ?: "" }
                .let { flow ->
                    // read current
                    var res = ""
                    context.dataStore.edit { p -> res = p[KEY_CUSTOM_ROUTINES_JSON] ?: "" }
                    res
                }
        ).ifEmpty { getDefaultPresetRoutines() }

        val updated = current + routine
        saveRoutines(updated)
    }

    suspend fun deleteRoutine(routineId: String) {
        val current = parseRoutinesFromJson(
            context.dataStore.data.map { it[KEY_CUSTOM_ROUTINES_JSON] ?: "" }
                .let {
                    var res = ""
                    context.dataStore.edit { p -> res = p[KEY_CUSTOM_ROUTINES_JSON] ?: "" }
                    res
                }
        ).ifEmpty { getDefaultPresetRoutines() }

        val updated = current.filter { it.id != routineId }
        saveRoutines(updated)
    }

    suspend fun resetRoutinesToDefault() {
        saveRoutines(getDefaultPresetRoutines())
    }

    private fun parseRoutinesFromJson(jsonStr: String): List<VoiceRoutine> {
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<VoiceRoutine>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    VoiceRoutine(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        triggerPhrase = obj.optString("triggerPhrase"),
                        promptCommand = obj.optString("promptCommand"),
                        iconName = obj.optString("iconName", "Bolt"),
                        colorHex = obj.optString("colorHex", "#38BDF8"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isPreset = obj.optBoolean("isPreset", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            getDefaultPresetRoutines()
        }
    }

    fun getDefaultPresetRoutines(): List<VoiceRoutine> = listOf(
        VoiceRoutine(
            id = "preset-morning",
            title = "Morning Wake Up",
            triggerPhrase = "Good morning Agent",
            promptCommand = "Provide a morning briefing, check today's weather in Google, and open the Calendar app",
            iconName = "WbSunny",
            colorHex = "#F59E0B",
            isEnabled = true,
            isPreset = true
        ),
        VoiceRoutine(
            id = "preset-driving",
            title = "Commute & Drive",
            triggerPhrase = "Start driving mode",
            promptCommand = "Open Google Maps navigation to home and search for chill driving playlist on YouTube",
            iconName = "DirectionsCar",
            colorHex = "#38BDF8",
            isEnabled = true,
            isPreset = true
        ),
        VoiceRoutine(
            id = "preset-workout",
            title = "Gym & Workout",
            triggerPhrase = "Workout time",
            promptCommand = "Set a 45 minute timer, play workout energetic music on YouTube, and open YouTube",
            iconName = "FitnessCenter",
            colorHex = "#10B981",
            isEnabled = true,
            isPreset = true
        ),
        VoiceRoutine(
            id = "preset-flashlight",
            title = "Quick Light",
            triggerPhrase = "Turn on light",
            promptCommand = "Turn on the flashlight",
            iconName = "FlashlightOn",
            colorHex = "#818CF8",
            isEnabled = true,
            isPreset = true
        ),
        VoiceRoutine(
            id = "preset-bedtime",
            title = "Night Routine",
            triggerPhrase = "Good night",
            promptCommand = "Set an alarm for 7:00 AM tomorrow, turn off flashlight, and open sound settings to mute",
            iconName = "Bedtime",
            colorHex = "#6366F1",
            isEnabled = true,
            isPreset = true
        )
    )
}
