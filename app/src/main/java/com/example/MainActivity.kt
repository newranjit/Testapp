package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.AgentForegroundService
import com.example.ui.AgentViewModel
import com.example.ui.MainScreenTab
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.InstalledAppsScreen
import com.example.ui.screens.RoutinesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AgentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start background notification panel assistant service
        AgentForegroundService.start(this)

        // Save AgentFlow.apk directly to device/emulator storage
        com.example.automation.ApkExportManager.saveApkToEmulatorStorage(this)

        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshOverlayStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("START_VOICE_LISTENING", false) == true) {
            viewModel.selectTab(MainScreenTab.ASSISTANT)
            viewModel.startVoiceListening()
        }
    }
}

@Composable
fun MainAppContent(viewModel: AgentViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Request permissions: Record Audio, Camera, and POST_NOTIFICATIONS on Android 13+
    val permissionsToRequest = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshOverlayStatus()
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
    }

    // Back handler: pop back to Assistant tab if on sub-screens
    BackHandler(enabled = uiState.activeTab != MainScreenTab.ASSISTANT) {
        viewModel.selectTab(MainScreenTab.ASSISTANT)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val navItems = listOf(
                    NavigationItem(MainScreenTab.ASSISTANT, "Assistant", Icons.Default.Bolt),
                    NavigationItem(MainScreenTab.ROUTINES, "Routines", Icons.Default.RecordVoiceOver),
                    NavigationItem(MainScreenTab.APPS, "Apps & Tools", Icons.Default.Apps),
                    NavigationItem(MainScreenTab.SETTINGS, "Settings", Icons.Default.Settings)
                )

                navItems.forEach { item ->
                    val isSelected = uiState.activeTab == item.tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(item.tab) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_${item.tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.activeTab) {
                MainScreenTab.ASSISTANT -> {
                    AssistantScreen(viewModel = viewModel, uiState = uiState)
                }
                MainScreenTab.ROUTINES -> {
                    RoutinesScreen(viewModel = viewModel, uiState = uiState)
                }
                MainScreenTab.APPS -> {
                    InstalledAppsScreen(viewModel = viewModel, uiState = uiState)
                }
                MainScreenTab.SETTINGS -> {
                    SettingsScreen(viewModel = viewModel, uiState = uiState)
                }
            }
        }
    }
}

private data class NavigationItem(
    val tab: MainScreenTab,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
