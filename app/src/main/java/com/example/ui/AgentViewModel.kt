package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiAgentService
import com.example.automation.AgentVoiceSpeaker
import com.example.automation.DeviceAutomationManager
import com.example.automation.VoiceCommandManager
import com.example.automation.VoiceState
import com.example.data.model.ActionType
import com.example.data.model.AgentPlan
import com.example.data.model.AutomatedActionStep
import com.example.data.model.ExecutionLog
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LogLevel
import com.example.data.model.StepStatus
import com.example.data.model.VoiceRoutine
import com.example.data.repository.AppSettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainScreenTab(val title: String) {
    ASSISTANT("Assistant"),
    ROUTINES("Routines"),
    APPS("Apps & Tools"),
    SETTINGS("Settings")
}

data class AgentUiState(
    val activeTab: MainScreenTab = MainScreenTab.ASSISTANT,
    val promptInput: String = "",
    val isPlanning: Boolean = false,
    val isExecuting: Boolean = false,
    val currentPlan: AgentPlan? = null,
    val currentStepIndex: Int = 0,
    val executionLogs: List<ExecutionLog> = emptyList(),
    val voiceState: VoiceState = VoiceState.IDLE,
    val voiceRmsLevel: Float = 0f,
    val voicePartialText: String = "",
    val isSpeaking: Boolean = false,
    val apiKey: String = "",
    val selectedModel: String = AppSettingsRepository.DEFAULT_MODEL,
    val autoExecute: Boolean = true,
    val ttsEnabled: Boolean = true,
    val speechRate: Float = 1.0f,
    val customRoutines: List<VoiceRoutine> = emptyList(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val installedAppsSearchQuery: String = "",
    val isTestingApiKey: Boolean = false,
    val apiKeyTestResult: Pair<Boolean, String>? = null,
    val waitingConfirmationStep: AutomatedActionStep? = null,
    val bannerMessage: String? = null,
    val isFloatingOverlayActive: Boolean = false
)

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = AppSettingsRepository(application)
    val automationManager = DeviceAutomationManager(application)
    val voiceSpeaker = AgentVoiceSpeaker(application)
    private val geminiService = GeminiAgentService()

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private var voiceManager: VoiceCommandManager? = null

    init {
        // Collect repository preferences
        viewModelScope.launch {
            settingsRepo.geminiApiKeyFlow.collect { key ->
                _uiState.value = _uiState.value.copy(apiKey = key)
            }
        }
        viewModelScope.launch {
            settingsRepo.selectedModelFlow.collect { model ->
                _uiState.value = _uiState.value.copy(selectedModel = model)
            }
        }
        viewModelScope.launch {
            settingsRepo.autoExecuteFlow.collect { auto ->
                _uiState.value = _uiState.value.copy(autoExecute = auto)
            }
        }
        viewModelScope.launch {
            settingsRepo.ttsEnabledFlow.collect { tts ->
                _uiState.value = _uiState.value.copy(ttsEnabled = tts)
            }
        }
        viewModelScope.launch {
            settingsRepo.speechRateFlow.collect { rate ->
                _uiState.value = _uiState.value.copy(speechRate = rate)
            }
        }
        viewModelScope.launch {
            settingsRepo.customRoutinesFlow.collect { routines ->
                _uiState.value = _uiState.value.copy(customRoutines = routines)
            }
        }
        viewModelScope.launch {
            voiceSpeaker.isSpeaking.collect { speaking ->
                _uiState.value = _uiState.value.copy(isSpeaking = speaking)
            }
        }

        // Initialize voice manager
        initVoiceManager()

        // Scan installed apps
        refreshInstalledApps()

        addLog(LogLevel.INFO, "AgentFlow", "AgentFlow initialized. Ready for phone automation.")
    }

    private fun initVoiceManager() {
        voiceManager = VoiceCommandManager(
            context = getApplication(),
            onVoiceResult = { text ->
                _uiState.value = _uiState.value.copy(
                    promptInput = text,
                    voiceState = VoiceState.IDLE
                )
                submitPrompt(text)
            },
            onError = { errMsg ->
                _uiState.value = _uiState.value.copy(
                    voiceState = VoiceState.ERROR,
                    bannerMessage = errMsg
                )
                addLog(LogLevel.ERROR, "Voice", errMsg)
            }
        )

        // Observe voice manager flows
        viewModelScope.launch {
            voiceManager?.voiceState?.collect { state ->
                _uiState.value = _uiState.value.copy(voiceState = state)
            }
        }
        viewModelScope.launch {
            voiceManager?.partialText?.collect { text ->
                _uiState.value = _uiState.value.copy(voicePartialText = text)
            }
        }
        viewModelScope.launch {
            voiceManager?.rmsLevel?.collect { rms ->
                _uiState.value = _uiState.value.copy(voiceRmsLevel = rms)
            }
        }
    }

    fun selectTab(tab: MainScreenTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun updatePromptInput(input: String) {
        _uiState.value = _uiState.value.copy(promptInput = input)
    }

    fun updateAppsSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(installedAppsSearchQuery = query)
    }

    fun dismissBanner() {
        _uiState.value = _uiState.value.copy(bannerMessage = null)
    }

    fun startVoiceListening() {
        voiceSpeaker.stop()
        voiceManager?.startListening()
    }

    fun stopVoiceListening() {
        voiceManager?.stopListening()
    }

    fun cancelVoiceListening() {
        voiceManager?.cancel()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = automationManager.getInstalledApps()
            _uiState.value = _uiState.value.copy(installedApps = apps)
        }
    }

    /**
     * Submit user request (from text or voice). Checks routines or asks Gemini.
     */
    fun submitPrompt(prompt: String = _uiState.value.promptInput) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) return

        // Check if matches custom voice routine trigger phrase
        val matchedRoutine = _uiState.value.customRoutines.firstOrNull { routine ->
            routine.isEnabled && (
                cleanPrompt.equals(routine.triggerPhrase, ignoreCase = true) ||
                cleanPrompt.contains(routine.triggerPhrase, ignoreCase = true)
            )
        }

        val effectivePrompt = matchedRoutine?.promptCommand ?: cleanPrompt
        if (matchedRoutine != null) {
            addLog(
                LogLevel.INFO,
                "Routine",
                "Triggered voice routine: '${matchedRoutine.title}' (phrase: '${matchedRoutine.triggerPhrase}')"
            )
        }

        planAndExecute(effectivePrompt)
    }

    private fun planAndExecute(prompt: String) {
        val state = _uiState.value
        val apiKey = state.apiKey

        if (apiKey.isBlank()) {
            val msg = "Please enter your Gemini API Key in Settings to enable phone automation."
            _uiState.value = _uiState.value.copy(bannerMessage = msg)
            addLog(LogLevel.WARNING, "Security", msg)
            if (state.ttsEnabled) voiceSpeaker.speak(msg, state.speechRate)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isPlanning = true,
                currentPlan = null,
                currentStepIndex = 0,
                waitingConfirmationStep = null
            )
            addLog(LogLevel.INFO, "Planner", "Analyzing request: \"$prompt\"")

            val deviceStatus = automationManager.getDeviceStatusSnapshot()
            val result = geminiService.planAutomation(
                userPrompt = prompt,
                apiKey = apiKey,
                model = state.selectedModel,
                installedApps = state.installedApps,
                deviceStatus = deviceStatus
            )

            _uiState.value = _uiState.value.copy(isPlanning = false)

            result.onSuccess { (plan, announcement) ->
                _uiState.value = _uiState.value.copy(
                    currentPlan = plan,
                    currentStepIndex = 0
                )
                addLog(LogLevel.SUCCESS, "Planner", "Plan decomposed into ${plan.steps.size} step(s). Goal: ${plan.userGoal}")

                if (state.ttsEnabled && announcement.isNotBlank()) {
                    voiceSpeaker.speak(announcement, state.speechRate)
                }

                // Begin step-by-step execution
                executeNextStepSequence(plan)
            }.onFailure { error ->
                val errorMsg = error.message ?: "Failed to generate automation plan."
                _uiState.value = _uiState.value.copy(bannerMessage = errorMsg)
                addLog(LogLevel.ERROR, "Planner", errorMsg)
                if (state.ttsEnabled) {
                    voiceSpeaker.speak("Automation plan could not be created. $errorMsg", state.speechRate)
                }
            }
        }
    }

    private fun executeNextStepSequence(plan: AgentPlan) {
        val autoExecute = _uiState.value.autoExecute
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExecuting = true)

            for (index in plan.steps.indices) {
                val step = plan.steps[index]
                _uiState.value = _uiState.value.copy(currentStepIndex = index)

                // If step is high-impact (calls, SMS, etc.) and autoExecute is disabled, pause for confirmation
                val isSensitive = step.actionType == ActionType.MAKE_CALL || step.actionType == ActionType.SEND_SMS
                if (!autoExecute && isSensitive) {
                    step.status = StepStatus.WAITING_CONFIRMATION
                    _uiState.value = _uiState.value.copy(
                        waitingConfirmationStep = step,
                        isExecuting = false
                    )
                    addLog(LogLevel.WARNING, "Safety", "Waiting for confirmation: ${step.description}")
                    return@launch
                }

                // Execute the step
                executeStep(step)
                delay(800) // Small pacing delay between multi-app launches
            }

            _uiState.value = _uiState.value.copy(
                isExecuting = false,
                currentPlan = plan.copy(isCompleted = true)
            )
            addLog(LogLevel.SUCCESS, "Executor", "All automation tasks completed successfully!")

            if (_uiState.value.ttsEnabled) {
                voiceSpeaker.speak("Task complete.", _uiState.value.speechRate)
            }
        }
    }

    /**
     * Executes a single step and updates its UI state.
     */
    fun executeStep(step: AutomatedActionStep) {
        step.status = StepStatus.RUNNING
        addLog(LogLevel.INFO, "Action", "Executing [${step.actionType.displayName}]: ${step.description}")

        val (success, message) = automationManager.executeStep(step)

        if (success) {
            step.status = StepStatus.SUCCESS
            step.resultMessage = message
            addLog(LogLevel.SUCCESS, "Action", message)
        } else {
            step.status = StepStatus.FAILED
            step.resultMessage = message
            addLog(LogLevel.ERROR, "Action", message)
        }

        // Trigger recomposition by updating currentPlan
        val plan = _uiState.value.currentPlan
        if (plan != null) {
            _uiState.value = _uiState.value.copy(currentPlan = plan.copy())
        }
    }

    /**
     * User approves waiting confirmation step.
     */
    fun approveWaitingStep() {
        val step = _uiState.value.waitingConfirmationStep ?: return
        _uiState.value = _uiState.value.copy(waitingConfirmationStep = null)
        viewModelScope.launch {
            executeStep(step)
            val plan = _uiState.value.currentPlan ?: return@launch
            val nextIndex = _uiState.value.currentStepIndex + 1
            if (nextIndex < plan.steps.size) {
                val remainingPlan = plan.copy(steps = plan.steps.drop(nextIndex))
                executeNextStepSequence(remainingPlan)
            } else {
                _uiState.value = _uiState.value.copy(
                    isExecuting = false,
                    currentPlan = plan.copy(isCompleted = true)
                )
            }
        }
    }

    /**
     * User skips waiting confirmation step.
     */
    fun skipWaitingStep() {
        val step = _uiState.value.waitingConfirmationStep ?: return
        step.status = StepStatus.SKIPPED
        step.resultMessage = "Skipped by user"
        _uiState.value = _uiState.value.copy(waitingConfirmationStep = null)
        val plan = _uiState.value.currentPlan ?: return
        _uiState.value = _uiState.value.copy(currentPlan = plan.copy())
    }

    fun stopExecution() {
        _uiState.value = _uiState.value.copy(isExecuting = false)
        voiceSpeaker.stop()
        addLog(LogLevel.WARNING, "Executor", "Automation paused by user.")
    }

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(executionLogs = emptyList())
    }

    private fun addLog(level: LogLevel, tag: String, message: String) {
        val log = ExecutionLog(level = level, tag = tag, message = message)
        _uiState.value = _uiState.value.copy(
            executionLogs = (listOf(log) + _uiState.value.executionLogs).take(100)
        )
    }

    // --- Settings and Routines management ---

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsRepo.saveApiKey(key)
            _uiState.value = _uiState.value.copy(apiKey = key)
            addLog(LogLevel.INFO, "Settings", "Gemini API Key updated.")
        }
    }

    fun saveModel(model: String) {
        viewModelScope.launch {
            settingsRepo.saveModel(model)
            _uiState.value = _uiState.value.copy(selectedModel = model)
        }
    }

    fun saveAutoExecute(auto: Boolean) {
        viewModelScope.launch {
            settingsRepo.saveAutoExecute(auto)
            _uiState.value = _uiState.value.copy(autoExecute = auto)
        }
    }

    fun saveTtsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.saveTtsEnabled(enabled)
            _uiState.value = _uiState.value.copy(ttsEnabled = enabled)
        }
    }

    fun saveSpeechRate(rate: Float) {
        viewModelScope.launch {
            settingsRepo.saveSpeechRate(rate)
            _uiState.value = _uiState.value.copy(speechRate = rate)
        }
    }

    fun testApiKeyConnection() {
        val key = _uiState.value.apiKey
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingApiKey = true, apiKeyTestResult = null)
            val result = geminiService.testApiKey(key, _uiState.value.selectedModel)
            _uiState.value = _uiState.value.copy(
                isTestingApiKey = false,
                apiKeyTestResult = result
            )
            val logMsg = if (result.first) "API key validation passed." else "API key test failed: ${result.second}"
            addLog(if (result.first) LogLevel.SUCCESS else LogLevel.ERROR, "API Test", logMsg)
        }
    }

    fun addCustomRoutine(routine: VoiceRoutine) {
        viewModelScope.launch {
            settingsRepo.addRoutine(routine)
            addLog(LogLevel.INFO, "Routine", "Created custom voice routine: ${routine.title}")
        }
    }

    fun deleteRoutine(routineId: String) {
        viewModelScope.launch {
            settingsRepo.deleteRoutine(routineId)
            addLog(LogLevel.INFO, "Routine", "Deleted routine.")
        }
    }

    fun toggleRoutineEnabled(routine: VoiceRoutine) {
        viewModelScope.launch {
            val updated = _uiState.value.customRoutines.map {
                if (it.id == routine.id) it.copy(isEnabled = !it.isEnabled) else it
            }
            settingsRepo.saveRoutines(updated)
        }
    }

    fun resetRoutinesToDefault() {
        viewModelScope.launch {
            settingsRepo.resetRoutinesToDefault()
            addLog(LogLevel.INFO, "Routine", "Reset routines to default presets.")
        }
    }

    fun generateRoutineWithAi(idea: String, onComplete: (VoiceRoutine?) -> Unit) {
        viewModelScope.launch {
            val result = geminiService.generateVoiceRoutine(
                userIdea = idea,
                apiKey = _uiState.value.apiKey,
                model = _uiState.value.selectedModel
            )
            result.onSuccess { json ->
                val routine = VoiceRoutine(
                    title = json.optString("title", "Custom Routine"),
                    triggerPhrase = json.optString("triggerPhrase", idea),
                    promptCommand = json.optString("promptCommand", idea),
                    iconName = json.optString("iconName", "Bolt"),
                    colorHex = json.optString("colorHex", "#38BDF8"),
                    isEnabled = true,
                    isPreset = false
                )
                onComplete(routine)
            }.onFailure {
                onComplete(null)
            }
        }
    }

    fun refreshOverlayStatus() {
        val running = com.example.service.FloatingAgentOverlayService.isOverlayRunning
        _uiState.value = _uiState.value.copy(isFloatingOverlayActive = running)
    }

    fun toggleFloatingOverlay(context: android.content.Context) {
        com.example.service.FloatingAgentOverlayService.toggle(context)
        // Give service brief moment to update flag
        viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            refreshOverlayStatus()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager?.destroy()
        voiceSpeaker.shutdown()
    }
}
