package com.example.service

import android.content.Context
import android.util.Log
import com.example.ai.GeminiAgentService
import com.example.automation.AgentVoiceSpeaker
import com.example.automation.DeviceAutomationManager
import com.example.data.model.AgentPlan
import com.example.data.model.AutomatedActionStep
import com.example.data.repository.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object BackgroundAgentExecutor {

    private const val TAG = "BackgroundAgentExecutor"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _lastExecutionResult = MutableStateFlow<String?>("Ready for commands")
    val lastExecutionResult: StateFlow<String?> = _lastExecutionResult.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    fun executePrompt(context: Context, promptText: String, onFinished: (String) -> Unit = {}) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isBlank()) return

        scope.launch {
            _isExecuting.value = true
            _lastExecutionResult.value = "Planning: \"$cleanPrompt\""

            val settingsRepo = AppSettingsRepository(context.applicationContext)
            val apiKey = settingsRepo.geminiApiKeyFlow.first()
            val model = settingsRepo.selectedModelFlow.first()
            val ttsEnabled = settingsRepo.ttsEnabledFlow.first()
            val speechRate = settingsRepo.speechRateFlow.first()
            val autoExecute = settingsRepo.autoExecuteFlow.first()

            val automationManager = DeviceAutomationManager(context.applicationContext)
            val voiceSpeaker = AgentVoiceSpeaker(context.applicationContext)
            val geminiService = GeminiAgentService()

            // Update Notification to show executing state
            AgentNotificationController.updateStatus(
                context.applicationContext,
                "Planning: $cleanPrompt",
                isBusy = true
            )

            if (apiKey.isBlank()) {
                val errorMsg = "Gemini API key is not configured. Please open Settings."
                _lastExecutionResult.value = errorMsg
                _isExecuting.value = false
                AgentNotificationController.updateStatus(context.applicationContext, errorMsg, isBusy = false)
                if (ttsEnabled) voiceSpeaker.speak(errorMsg, speechRate)
                onFinished(errorMsg)
                return@launch
            }

            try {
                val apps = automationManager.getInstalledApps()
                val status = automationManager.getDeviceStatusSnapshot()

                val planResult = geminiService.planAutomation(
                    userPrompt = cleanPrompt,
                    apiKey = apiKey,
                    model = model,
                    installedApps = apps,
                    deviceStatus = status
                )

                planResult.onSuccess { (plan, announcement) ->
                    if (ttsEnabled && announcement.isNotBlank()) {
                        voiceSpeaker.speak(announcement, speechRate)
                    }

                    // Execute each step
                    for (index in plan.steps.indices) {
                        val step = plan.steps[index]
                        _lastExecutionResult.value = "Running (${index + 1}/${plan.steps.size}): ${step.description}"
                        AgentNotificationController.updateStatus(
                            context.applicationContext,
                            "Step ${index + 1}/${plan.steps.size}: ${step.description}",
                            isBusy = true
                        )

                        automationManager.executeStep(step)
                        delay(700)
                    }

                    val finalMsg = "Completed: ${plan.userGoal}"
                    _lastExecutionResult.value = finalMsg
                    _isExecuting.value = false
                    AgentNotificationController.updateStatus(
                        context.applicationContext,
                        finalMsg,
                        isBusy = false
                    )

                    if (ttsEnabled) {
                        voiceSpeaker.speak("Automation complete.", speechRate)
                    }
                    onFinished(finalMsg)
                }.onFailure { error ->
                    val errMsg = "Failed: ${error.message}"
                    _lastExecutionResult.value = errMsg
                    _isExecuting.value = false
                    AgentNotificationController.updateStatus(
                        context.applicationContext,
                        errMsg,
                        isBusy = false
                    )
                    if (ttsEnabled) {
                        voiceSpeaker.speak("Could not execute request. ${error.message}", speechRate)
                    }
                    onFinished(errMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Execution failed", e)
                val errMsg = "Error: ${e.message}"
                _lastExecutionResult.value = errMsg
                _isExecuting.value = false
                AgentNotificationController.updateStatus(context.applicationContext, errMsg, isBusy = false)
                onFinished(errMsg)
            }
        }
    }
}
