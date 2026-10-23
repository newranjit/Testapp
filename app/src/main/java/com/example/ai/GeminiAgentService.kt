package com.example.ai

import android.util.Log
import com.example.data.model.ActionType
import com.example.data.model.AgentPlan
import com.example.data.model.AutomatedActionStep
import com.example.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiAgentService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
        private const val TAG = "GeminiAgentService"
    }

    /**
     * Test if a Gemini API key is working.
     */
    suspend fun testApiKey(apiKey: String, model: String = "gemini-3.5-flash"): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext Pair(false, "API Key is empty")
            }
            try {
                val url = "$BASE_URL$model:generateContent?key=$apiKey"
                val jsonPayload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "Respond with 'OK' if you can read this.")
                                })
                            })
                        })
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        Pair(true, "API Key is valid and connected successfully!")
                    } else {
                        val errMsg = extractErrorMessage(body)
                        Pair(false, "Connection error (${response.code}): $errMsg")
                    }
                }
            } catch (e: Exception) {
                Pair(false, "Network error: ${e.message}")
            }
        }

    /**
     * Analyze user instruction with device context and decompose into an autonomous execution plan.
     */
    suspend fun planAutomation(
        userPrompt: String,
        apiKey: String,
        model: String = "gemini-3.5-flash",
        installedApps: List<InstalledAppInfo>,
        deviceStatus: String
    ): Result<Pair<AgentPlan, String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API Key is not configured. Please add your key in Settings.")
            )
        }

        try {
            val appListSummary = installedApps.take(60).joinToString(", ") { "${it.appName} (${it.packageName})" }

            val systemInstructionText = """
You are AgentFlow, an autonomous Android Phone Automation AI Agent.
Your goal is to parse user natural language requests (or voice commands), analyze current device context and available apps, and generate a precise, executable multi-step action plan to accomplish the user's task across applications.

Current Device Context: $deviceStatus
Installed Applications on Device:
$appListSummary

Available Automation Action Types:
1. OPEN_APP: Launches an installed app. (Parameters: packageName, appName)
2. OPEN_URL: Opens any URL in the browser. (Parameters: url)
3. SEARCH_WEB: Searches Google for a query. (Parameters: query)
4. MAKE_CALL: Dials a phone number or contact. (Parameters: phoneNumber)
5. SEND_SMS: Opens SMS messaging with prefilled text. (Parameters: phoneNumber, messageText)
6. NAVIGATE: Opens turn-by-turn directions or maps location. (Parameters: location)
7. SET_ALARM: Sets device alarm clock. (Parameters: hour [0-23], minute [0-59], description)
8. SET_TIMER: Starts a countdown timer in seconds. (Parameters: seconds, description)
9. PLAY_MUSIC: Searches and plays music/video on YouTube or media player. (Parameters: query)
10. OPEN_SETTINGS: Opens device settings screen ("wifi", "bluetooth", "sound", "battery", "display", "all"). (Parameters: settingsType)
11. TOGGLE_FLASHLIGHT: Turns device flashlight on/off. (Parameters: enableToggle [true/false])
12. COPY_CLIPBOARD: Copies generated or processed text to clipboard. (Parameters: messageText)
13. CALENDAR_EVENT: Drafts a calendar event. (Parameters: query [title], messageText [description])
14. SPEAK: Speaks verbal information to the user. (Parameters: description)

You MUST respond strictly with valid JSON conforming to this schema:
{
  "userGoal": "string summary of the user's objective",
  "thoughtProcess": "explanation of how you are solving this with device actions",
  "verbalAnnouncement": "friendly 1-2 sentence spoken feedback to announce execution",
  "steps": [
    {
      "actionType": "OPEN_APP | OPEN_URL | SEARCH_WEB | MAKE_CALL | SEND_SMS | NAVIGATE | SET_ALARM | SET_TIMER | PLAY_MUSIC | OPEN_SETTINGS | TOGGLE_FLASHLIGHT | COPY_CLIPBOARD | CALENDAR_EVENT | SPEAK",
      "description": "Readable description of this step",
      "packageName": "optional package name e.g. com.google.android.youtube",
      "appName": "optional app name",
      "query": "optional query string",
      "url": "optional url",
      "phoneNumber": "optional phone number",
      "messageText": "optional text/message",
      "location": "optional location",
      "hour": 7,
      "minute": 30,
      "seconds": 60,
      "settingsType": "wifi | bluetooth | sound | battery | display",
      "enableToggle": true
    }
  ]
}
If the user asks an informational question that requires no phone hardware actions (e.g. "What is photosynthesis?"), use actionType "SPEAK" and provide the answer in "description" and "verbalAnnouncement".
Always keep steps actionable, accurate, and optimized for Android.
""".trimIndent()

            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "User Automation Request: \"$userPrompt\"")
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstructionText)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val url = "$BASE_URL$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorDetail = extractErrorMessage(responseStr)
                    return@withContext Result.failure(
                        Exception("Gemini API Error (${response.code}): $errorDetail")
                    )
                }

                val planResult = parsePlanFromJson(userPrompt, responseStr)
                Result.success(planResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Planning failed", e)
            Result.failure(e)
        }
    }

    /**
     * AI Routine Generator: Takes user's plain English description and generates a full VoiceRoutine
     */
    suspend fun generateVoiceRoutine(
        userIdea: String,
        apiKey: String,
        model: String = "gemini-3.5-flash"
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API key missing"))
        }
        try {
            val prompt = """
Generate a custom voice automation routine for an Android phone automation app based on this user idea: "$userIdea"
Return JSON with:
{
  "title": "Short title (2-4 words)",
  "triggerPhrase": "Voice trigger phrase user can say hands-free (e.g. 'Commute to office')",
  "promptCommand": "Detailed multi-step instruction for AgentFlow to execute",
  "iconName": "Bolt | DirectionsCar | WbSunny | FitnessCenter | Bedtime | FlashlightOn | MusicNote | Coffee | Work",
  "colorHex": "#38BDF8 | #10B981 | #F59E0B | #818CF8 | #EC4899"
}
""".trimIndent()

            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            val url = "$BASE_URL$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(extractErrorMessage(body)))
                }
                val json = JSONObject(body)
                val rawText = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                Result.success(JSONObject(rawText))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePlanFromJson(originalGoal: String, responseJson: String): Pair<AgentPlan, String> {
        val root = JSONObject(responseJson)
        val candidates = root.optJSONArray("candidates")
        val content = candidates?.optJSONObject(0)?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val text = parts?.optJSONObject(0)?.optString("text") ?: "{}"

        val planObj = JSONObject(text)
        val userGoal = planObj.optString("userGoal", originalGoal)
        val thoughtProcess = planObj.optString("thoughtProcess", "Planning automation steps.")
        val verbalAnnouncement = planObj.optString("verbalAnnouncement", "Executing requested automation.")
        val stepsArray = planObj.optJSONArray("steps") ?: JSONArray()

        val stepList = mutableListOf<AutomatedActionStep>()
        for (i in 0 until stepsArray.length()) {
            val sObj = stepsArray.getJSONObject(i)
            val actionTypeName = sObj.optString("actionType", "OPEN_APP")
            val actionType = try {
                ActionType.valueOf(actionTypeName)
            } catch (e: Exception) {
                ActionType.SEARCH_WEB
            }

            val step = AutomatedActionStep(
                actionType = actionType,
                description = sObj.optString("description", "Execute action"),
                packageName = if (sObj.has("packageName") && !sObj.isNull("packageName")) sObj.getString("packageName") else null,
                appName = if (sObj.has("appName") && !sObj.isNull("appName")) sObj.getString("appName") else null,
                query = if (sObj.has("query") && !sObj.isNull("query")) sObj.getString("query") else null,
                url = if (sObj.has("url") && !sObj.isNull("url")) sObj.getString("url") else null,
                phoneNumber = if (sObj.has("phoneNumber") && !sObj.isNull("phoneNumber")) sObj.getString("phoneNumber") else null,
                messageText = if (sObj.has("messageText") && !sObj.isNull("messageText")) sObj.getString("messageText") else null,
                location = if (sObj.has("location") && !sObj.isNull("location")) sObj.getString("location") else null,
                hour = if (sObj.has("hour") && !sObj.isNull("hour")) sObj.getInt("hour") else null,
                minute = if (sObj.has("minute") && !sObj.isNull("minute")) sObj.getInt("minute") else null,
                seconds = if (sObj.has("seconds") && !sObj.isNull("seconds")) sObj.getInt("seconds") else null,
                settingsType = if (sObj.has("settingsType") && !sObj.isNull("settingsType")) sObj.getString("settingsType") else null,
                enableToggle = sObj.optBoolean("enableToggle", true)
            )
            stepList.add(step)
        }

        val plan = AgentPlan(
            userGoal = userGoal,
            thoughtProcess = thoughtProcess,
            steps = stepList
        )
        return Pair(plan, verbalAnnouncement)
    }

    private fun extractErrorMessage(responseBody: String): String {
        return try {
            val obj = JSONObject(responseBody)
            obj.optJSONObject("error")?.optString("message") ?: responseBody
        } catch (e: Exception) {
            responseBody
        }
    }
}
