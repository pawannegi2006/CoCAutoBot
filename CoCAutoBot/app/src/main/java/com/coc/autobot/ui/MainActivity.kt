package com.coc.autobot.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.coc.autobot.bot.BotStateMachine
import com.coc.autobot.ui.theme.CoCAutoBotTheme
import com.coc.autobot.viewmodel.BotViewModel

/**
 * DISCLAIMER: This project is strictly for educational and research purposes only.
 * Using automation tools violates Clash of Clans Terms of Service and may result in permanent account bans.
 * The user assumes all risks associated with using this software.
 *
 * MainActivity is the single-activity UI for CoCAutoBot.
 * Provides a minimalist interface with:
 * - Large Start/Stop button
 * - Scrollable log area
 * - Settings drawer for configuration
 */
class MainActivity : ComponentActivity() {

    private val viewModel: BotViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoCAutoBotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BotControlScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAccessibilityResult(this)
    }
}

/**
 * Main composable screen containing all UI elements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotControlScreen(viewModel: BotViewModel) {
    val context = LocalContext.current

    val isRunning by viewModel.isRunning.collectAsState()
    val botState by viewModel.botState.collectAsState()
    val currentAction by viewModel.currentAction.collectAsState()
    val logMessages by viewModel.logMessages.collectAsState()
    val screenCaptureGranted by viewModel.screenCaptureGranted.collectAsState()
    val accessibilityGranted by viewModel.accessibilityGranted.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(logMessages.size) {
        if (logMessages.isNotEmpty()) {
            listState.animateScrollToItem(logMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CoCAutoBot") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            StatusCard(
                botState = botState,
                currentAction = currentAction,
                screenCaptureGranted = screenCaptureGranted,
                accessibilityGranted = accessibilityGranted
            )

            Spacer(modifier = Modifier.height(16.dp))

            LogDisplay(
                logMessages = logMessages,
                listState = listState,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            ControlButtons(
                isRunning = isRunning,
                screenCaptureGranted = screenCaptureGranted,
                accessibilityGranted = accessibilityGranted,
                onStartClick = {
                    if (!screenCaptureGranted) {
                        viewModel.requestScreenCapturePermission(context as Activity)
                    } else if (!accessibilityGranted) {
                        viewModel.openAccessibilitySettings(context as Activity)
                    } else {
                        viewModel.startBot(context)
                    }
                },
                onStopClick = {
                    viewModel.stopBot(context)
                }
            )
        }
    }

    if (showSettings) {
        SettingsDrawer(
            currentSettings = settings,
            onDismiss = { showSettings = false },
            onSettingsChanged = { newSettings ->
                viewModel.updateSettings(newSettings)
            }
        )
    }
}

@Composable
fun StatusCard(
    botState: BotStateMachine.BotState,
    currentAction: String,
    screenCaptureGranted: Boolean,
    accessibilityGranted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Status: ${botState.name}",
                style = MaterialTheme.typography.titleMedium,
                color = when (botState) {
                    BotStateMachine.BotState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                    BotStateMachine.BotState.RESTING -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            Text(
                text = currentAction,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PermissionIndicator(label = "Screen Capture", granted = screenCaptureGranted)
                PermissionIndicator(label = "Accessibility", granted = accessibilityGranted)
            }
        }
    }
}

@Composable
fun PermissionIndicator(label: String, granted: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(8.dp)
        ) {}
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun LogDisplay(
    logMessages: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(logMessages) { message ->
                Text(
                    text = message,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun ControlButtons(
    isRunning: Boolean,
    screenCaptureGranted: Boolean,
    accessibilityGranted: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isRunning) {
            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                val buttonText = when {
                    !screenCaptureGranted -> "Grant Screen Capture"
                    !accessibilityGranted -> "Enable Accessibility"
                    else -> "START BOT"
                }
                Text(text = buttonText, fontSize = 18.sp)
            }
        } else {
            Button(
                onClick = onStopClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "STOP BOT", fontSize = 18.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawer(
    currentSettings: BotStateMachine.BotSettings,
    onDismiss: () -> Unit,
    onSettingsChanged: (BotStateMachine.BotSettings) -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(currentSettings.attackStrategy) }
    var maxOnlineHours by remember { mutableFloatStateOf(currentSettings.maxOnlineHours.toFloat()) }
    var skipUpgrade by remember { mutableStateOf(currentSettings.skipUpgradeIfLowResources) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(text = "Bot Settings", style = MaterialTheme.typography.headlineSmall)

            Text(text = "Attack Strategy", style = MaterialTheme.typography.titleSmall)
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedStrategy.displayName,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    BotStateMachine.AttackStrategy.entries.forEach { strategy ->
                        DropdownMenuItem(
                            text = { Text(strategy.displayName) },
                            onClick = {
                                selectedStrategy = strategy
                                expanded = false
                            }
                        )
                    }
                }
            }

            Text(text = "Max Online Time: ${maxOnlineHours.toInt()} hours", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = maxOnlineHours,
                onValueChange = { maxOnlineHours = it },
                valueRange = 1f..6f,
                steps = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Skip Upgrade if Low Resources", style = MaterialTheme.typography.titleSmall)
                Switch(checked = skipUpgrade, onCheckedChange = { skipUpgrade = it })
            }

            Button(
                onClick = {
                    onSettingsChanged(
                        BotStateMachine.BotSettings(
                            attackStrategy = selectedStrategy,
                            maxOnlineHours = maxOnlineHours.toInt(),
                            skipUpgradeIfLowResources = skipUpgrade
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }
        }
    }
}
