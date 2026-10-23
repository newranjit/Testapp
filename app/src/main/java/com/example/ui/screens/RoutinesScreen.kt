package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VoiceRoutine
import com.example.ui.AgentUiState
import com.example.ui.AgentViewModel
import com.example.ui.MainScreenTab
import com.example.ui.components.RoutineCard

@Composable
fun RoutinesScreen(
    viewModel: AgentViewModel,
    uiState: AgentUiState,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAiGeneratorDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier.testTag("add_routine_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Voice Routine",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = "Voice Routines",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Custom Voice Commands",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        IconButton(
                            onClick = { viewModel.resetRoutinesToDefault() },
                            modifier = Modifier.size(32.dp).testTag("reset_routines_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Presets",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = "Train custom phrases for hands-free automation across multiple apps. When you speak any trigger phrase, AgentFlow executes the configured task sequence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // AI Routine Generator Hero Card
            item {
                AiRoutineGeneratorCard(
                    onOpenAiGenerator = { showAiGeneratorDialog = true }
                )
            }

            // Routine List
            item {
                Text(
                    text = "ACTIVE VOICE SHORTCUTS (${uiState.customRoutines.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(uiState.customRoutines, key = { it.id }) { routine ->
                RoutineCard(
                    routine = routine,
                    onRun = {
                        viewModel.selectTab(MainScreenTab.ASSISTANT)
                        viewModel.submitPrompt(routine.promptCommand)
                    },
                    onToggleEnabled = { viewModel.toggleRoutineEnabled(routine) },
                    onDelete = { viewModel.deleteRoutine(routine.id) }
                )
            }
        }
    }

    // Manual Create Routine Dialog
    if (showCreateDialog) {
        CreateRoutineDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { routine ->
                viewModel.addCustomRoutine(routine)
                showCreateDialog = false
            }
        )
    }

    // AI Routine Generator Dialog
    if (showAiGeneratorDialog) {
        AiGenerateRoutineModal(
            viewModel = viewModel,
            onDismiss = { showAiGeneratorDialog = false },
            onSave = { routine ->
                viewModel.addCustomRoutine(routine)
                showAiGeneratorDialog = false
            }
        )
    }
}

@Composable
private fun AiRoutineGeneratorCard(onOpenAiGenerator: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("ai_routine_generator_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Routine Generator",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI Voice Routine Builder",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Describe what you want hands-free, and Gemini will craft the voice trigger and multi-step actions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onOpenAiGenerator,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("ai_generator_btn")
            ) {
                Text(
                    text = "Create with AI",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun CreateRoutineDialog(
    onDismiss: () -> Unit,
    onSave: (VoiceRoutine) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var triggerPhrase by remember { mutableStateOf("") }
    var promptCommand by remember { mutableStateOf("") }
    val colors = listOf("#38BDF8", "#10B981", "#F59E0B", "#818CF8", "#EC4899")
    var selectedColor by remember { mutableStateOf(colors[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Voice Routine",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Routine Title") },
                    placeholder = { Text("e.g. Study Mode") },
                    modifier = Modifier.fillMaxWidth().testTag("routine_title_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = triggerPhrase,
                    onValueChange = { triggerPhrase = it },
                    label = { Text("Voice Trigger Phrase") },
                    placeholder = { Text("e.g. Start study session") },
                    modifier = Modifier.fillMaxWidth().testTag("routine_trigger_input"),
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                )

                OutlinedTextField(
                    value = promptCommand,
                    onValueChange = { promptCommand = it },
                    label = { Text("Automation Instructions") },
                    placeholder = { Text("e.g. Open Spotify, play study playlist, set 50m timer, open sound settings to mute") },
                    modifier = Modifier.fillMaxWidth().testTag("routine_prompt_input"),
                    minLines = 3,
                    maxLines = 5
                )

                Text(
                    text = "Accent Color",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colors.forEach { hex ->
                        val parsed = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parsed)
                                .clickable { selectedColor = hex }
                                .padding(2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && triggerPhrase.isNotBlank() && promptCommand.isNotBlank()) {
                        onSave(
                            VoiceRoutine(
                                title = title.trim(),
                                triggerPhrase = triggerPhrase.trim(),
                                promptCommand = promptCommand.trim(),
                                colorHex = selectedColor,
                                isPreset = false
                            )
                        )
                    }
                },
                enabled = title.isNotBlank() && triggerPhrase.isNotBlank() && promptCommand.isNotBlank(),
                modifier = Modifier.testTag("save_routine_btn")
            ) {
                Text("Save Routine")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AiGenerateRoutineModal(
    viewModel: AgentViewModel,
    onDismiss: () -> Unit,
    onSave: (VoiceRoutine) -> Unit
) {
    var userIdea by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedRoutine by remember { mutableStateOf<VoiceRoutine?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "AI Voice Routine Generator",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Describe what you want this voice command to do in plain English:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = userIdea,
                    onValueChange = { userIdea = it },
                    placeholder = { Text("e.g. When I'm cooking, set a 15m timer, play kitchen upbeat music on YouTube, and open YouTube") },
                    modifier = Modifier.fillMaxWidth().testTag("ai_idea_input"),
                    minLines = 3,
                    maxLines = 4
                )

                if (isGenerating) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Gemini is building your routine…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (generatedRoutine != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Generated Routine: ${generatedRoutine!!.title}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Voice Trigger: \"${generatedRoutine!!.triggerPhrase}\"",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = generatedRoutine!!.promptCommand,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (generatedRoutine != null) {
                Button(
                    onClick = { onSave(generatedRoutine!!) },
                    modifier = Modifier.testTag("accept_generated_routine_btn")
                ) {
                    Text("Save Routine")
                }
            } else {
                Button(
                    onClick = {
                        if (userIdea.isNotBlank()) {
                            isGenerating = true
                            errorMessage = null
                            viewModel.generateRoutineWithAi(userIdea) { result ->
                                isGenerating = false
                                if (result != null) {
                                    generatedRoutine = result
                                } else {
                                    errorMessage = "Could not generate routine. Ensure Gemini API key is configured in Settings."
                                }
                            }
                        }
                    },
                    enabled = userIdea.isNotBlank() && !isGenerating,
                    modifier = Modifier.testTag("generate_ai_routine_btn")
                ) {
                    Text("Generate with Gemini")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
