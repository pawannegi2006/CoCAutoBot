package com.coc.autobot.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coc.autobot.bot.BotStateMachine
import com.coc.autobot.service.AutomationAccessibilityService
import com.coc.autobot.service.ScreenCaptureService
import com.coc.autobot.util.HumanBehaviorSimulator
import com.coc.autobot.vision.GameObjectDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * DISCLAIMER: This project is strictly for educational and research purposes only.
 * Using automation tools violates Clash of Clans Terms of Service and may result in permanent account bans.
 * The user assumes all risks associated with using this software.
 *
 * BotViewModel is the ViewModel layer in the MVVM architecture.
 * It acts as the intermediary between the UI (Jetpack Compose) and the bot logic.
 * Manages bot state, settings, and coordinates permission flows for MediaProjection and AccessibilityService.
 */
class BotViewModel : ViewModel() {

    companion object {
        // Request codes for permission activities
        const val REQUEST_MEDIA_PROJECTION = 1001
        const val REQUEST_ACCESSIBILITY = 1002
    }

    // Bot state exposed to UI
    private val _botState = MutableStateFlow(BotStateMachine.BotState.IDLE)
    val botState: StateFlow<BotStateMachine.BotState> = _botState

    // Current action description for log display
    private val _currentAction = MutableStateFlow("Ready to start")
    val currentAction: StateFlow<String> = _currentAction

    // Bot running status
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    // Log messages for the UI text area
    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages

    // Bot settings
    private val _settings = MutableStateFlow(BotStateMachine.BotSettings())
    val settings: StateFlow<BotStateMachine.BotSettings> = _settings

    // Permission status
    private val _screenCaptureGranted = MutableStateFlow(false)
    val screenCaptureGranted: StateFlow<Boolean> = _screenCaptureGranted

    private val _accessibilityGranted = MutableStateFlow(false)
    val accessibilityGranted: StateFlow<Boolean> = _accessibilityGranted

    // Bot components
    private var botStateMachine: BotStateMachine? = null
    private val humanBehaviorSimulator = HumanBehaviorSimulator()
    private val gameObjectDetector = GameObjectDetector()

    // MediaProjection data stored after permission grant
    private var mediaProjectionResultCode: Int = -1
    private var mediaProjectionResultData: Intent? = null

    /**
     * Checks if the Accessibility Service is enabled.
     * The service must be manually enabled in Android Settings.
     *
     * @param context Application context
     * @return true if the accessibility service is enabled
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    /**
     * Opens the Accessibility Settings page so the user can enable the service.
     *
     * @param activity The current activity for starting the intent
     */
    fun openAccessibilitySettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        activity.startActivityForResult(intent, REQUEST_ACCESSIBILITY)
    }

    /**
     * Requests MediaProjection permission for screen capture.
     * This shows a system dialog asking the user to allow screen recording.
     *
     * @param activity The current activity for starting the intent
     */
    fun requestScreenCapturePermission(activity: Activity) {
        val projectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()
        activity.startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
    }

    /**
     * Called when the MediaProjection permission result is received.
     * Stores the result and starts the ScreenCaptureService if granted.
     *
     * @param resultCode The result code from the permission dialog
     * @param data The Intent data containing the MediaProjection token
     */
    fun onScreenCaptureResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            mediaProjectionResultCode = resultCode
            mediaProjectionResultData = data
            _screenCaptureGranted.value = true
            addLogMessage("Screen capture permission granted")
        } else {
            _screenCaptureGranted.value = false
            addLogMessage("Screen capture permission denied")
        }
    }

    /**
     * Called when returning from Accessibility Settings.
     * Checks if the service was enabled by the user.
     *
     * @param context Application context
     */
    fun onAccessibilityResult(context: Context) {
        val enabled = isAccessibilityServiceEnabled(context)
        _accessibilityGranted.value = enabled
        if (enabled) {
            addLogMessage("Accessibility service enabled")
        } else {
            addLogMessage("Accessibility service not enabled")
        }
    }

    /**
     * Starts the bot if all required permissions are granted.
     * Initializes the BotStateMachine and begins automation.
     *
     * @param context Application context for starting services
     */
    fun startBot(context: Context) {
        if (!screenCaptureGranted.value) {
            addLogMessage("ERROR: Screen capture permission required")
            return
        }
        if (!accessibilityGranted.value) {
            addLogMessage("ERROR: Accessibility service required")
            return
        }

        // Start ScreenCaptureService
        val captureIntent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, mediaProjectionResultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, mediaProjectionResultData)
        }
        context.startForegroundService(captureIntent)

        // Initialize and start BotStateMachine
        botStateMachine = BotStateMachine(
            objectDetector = gameObjectDetector,
            behaviorSimulator = humanBehaviorSimulator,
            settings = _settings.value
        )

        // Observe bot state changes
        viewModelScope.launch {
            botStateMachine?.currentState?.collect { state ->
                _botState.value = state
            }
        }

        viewModelScope.launch {
            botStateMachine?.currentAction?.collect { action ->
                _currentAction.value = action
                addLogMessage(action)
            }
        }

        // Start the bot with the accessibility service
        AutomationAccessibilityService.getInstance()?.let { service ->
            botStateMachine?.start(service)
            _isRunning.value = true
            addLogMessage("Bot started successfully")
        } ?: run {
            addLogMessage("ERROR: Accessibility service not connected")
        }
    }

    /**
     * Stops the bot and all associated services.
     *
     * @param context Application context for stopping services
     */
    fun stopBot(context: Context) {
        botStateMachine?.stop()
        botStateMachine = null

        val stopIntent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        context.startService(stopIntent)

        _isRunning.value = false
        _botState.value = BotStateMachine.BotState.IDLE
        _currentAction.value = "Bot stopped"
        addLogMessage("Bot stopped")
    }

    /**
     * Updates the bot settings.
     *
     * @param newSettings The new settings to apply
     */
    fun updateSettings(newSettings: BotStateMachine.BotSettings) {
        _settings.value = newSettings
        addLogMessage("Settings updated: Strategy=${newSettings.attackStrategy.displayName}, MaxTime=${newSettings.maxOnlineHours}h")
    }

    /**
     * Adds a message to the log history.
     * Maintains a maximum of 100 log entries to prevent memory issues.
     *
     * @param message The log message to add
     */
    private fun addLogMessage(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val formattedMessage = "[$timestamp] $message"

        val currentLogs = _logMessages.value.toMutableList()
        currentLogs.add(formattedMessage)
        // Keep only last 100 messages
        if (currentLogs.size > 100) {
            currentLogs.removeAt(0)
        }
        _logMessages.value = currentLogs
    }

    override fun onCleared() {
        super.onCleared()
        botStateMachine?.stop()
        gameObjectDetector.release()
    }
}
