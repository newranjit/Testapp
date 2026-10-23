package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActionType
import com.example.data.model.AutomatedActionStep
import com.example.ui.AgentUiState
import com.example.ui.AgentViewModel
import com.example.ui.components.AppItemCard

@Composable
fun InstalledAppsScreen(
    viewModel: AgentViewModel,
    uiState: AgentUiState,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isFlashlightOn by remember { mutableStateOf(false) }

    val filteredApps = remember(uiState.installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.installedApps
        } else {
            uiState.installedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = "Installed Apps",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Apps & Device Tools",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${uiState.installedApps.size} applications discoverable by AgentFlow",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.refreshInstalledApps() },
                    modifier = Modifier.testTag("refresh_apps_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Apps",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Quick Device Hardware & System Tools Grid
        item {
            Text(
                text = "DIRECT HARDWARE & SYSTEM TOOLS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            QuickToolsGrid(
                onFlashlightToggle = {
                    isFlashlightOn = !isFlashlightOn
                    viewModel.automationManager.toggleFlashlight(isFlashlightOn)
                },
                isFlashlightOn = isFlashlightOn,
                onOpenSettings = { type ->
                    viewModel.automationManager.openDeviceSettings(type)
                },
                onSetAlarm = {
                    viewModel.automationManager.setAlarm(8, 0, "Test Alarm")
                },
                onSetTimer = {
                    viewModel.automationManager.setTimer(120, "2 Min Timer")
                },
                onOpenDialer = {
                    viewModel.automationManager.dialNumber("")
                },
                onOpenSms = {
                    viewModel.automationManager.sendSms("", "Hello from AgentFlow!")
                },
                onOpenMaps = {
                    viewModel.automationManager.navigateToLocation("Nearest Coffee Shop")
                }
            )
        }

        // Search Bar for installed apps
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SEARCH INSTALLED APPLICATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name or package...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_apps_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    singleLine = true
                )
            }
        }

        // List of Apps
        items(filteredApps, key = { it.packageName }) { app ->
            AppItemCard(
                app = app,
                onLaunch = {
                    viewModel.automationManager.launchApp(app.packageName, app.appName)
                }
            )
        }
    }
}

@Composable
private fun QuickToolsGrid(
    onFlashlightToggle: () -> Unit,
    isFlashlightOn: Boolean,
    onOpenSettings: (String) -> Unit,
    onSetAlarm: () -> Unit,
    onSetTimer: () -> Unit,
    onOpenDialer: () -> Unit,
    onOpenSms: () -> Unit,
    onOpenMaps: () -> Unit
) {
    val tools = listOf(
        QuickTool("Flashlight", Icons.Default.FlashlightOn, isFlashlightOn) { onFlashlightToggle() },
        QuickTool("Wi-Fi Settings", Icons.Default.Wifi) { onOpenSettings("wifi") },
        QuickTool("Bluetooth", Icons.Default.Bluetooth) { onOpenSettings("bluetooth") },
        QuickTool("Sound Settings", Icons.AutoMirrored.Filled.VolumeUp) { onOpenSettings("sound") },
        QuickTool("Battery Saver", Icons.Default.BatteryChargingFull) { onOpenSettings("battery") },
        QuickTool("Set 8AM Alarm", Icons.Default.Alarm) { onSetAlarm() },
        QuickTool("Start 2m Timer", Icons.Default.HourglassTop) { onSetTimer() },
        QuickTool("Open Dialer", Icons.Default.Phone) { onOpenDialer() },
        QuickTool("Draft SMS", Icons.AutoMirrored.Filled.Message) { onOpenSms() },
        QuickTool("Open Maps", Icons.Default.Map) { onOpenMaps() }
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.chunked(2).forEach { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTools.forEach { tool ->
                    Surface(
                        onClick = tool.action,
                        shape = RoundedCornerShape(12.dp),
                        color = if (tool.isActive)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (tool.isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tool_${tool.name.replace(" ", "_").lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = tool.name,
                                tint = if (tool.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = tool.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (rowTools.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class QuickTool(
    val name: String,
    val icon: ImageVector,
    val isActive: Boolean = false,
    val action: () -> Unit
)
