package com.coc.autobot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.coc.autobot.util.HumanBehaviorSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DISCLAIMER: This project is strictly for educational and research purposes only.
 * Using automation tools violates Clash of Clans Terms of Service and may result in permanent account bans.
 * The user assumes all risks associated with using this software.
 *
 * AutomationAccessibilityService extends AccessibilityService to perform simulated touch inputs.
 * This service uses the Android Accessibility API to dispatch gestures (clicks and swipes)
 * on behalf of the user.
 *
 * CRITICAL: All touch coordinates are randomized using Gaussian distribution, and
 * all actions include randomized timing delays to simulate human behavior and reduce
 * detection risk.
 */
class AutomationAccessibilityService : AccessibilityService() {

    companion object {
        // Singleton instance for external access
        @Volatile
        private var instance: AutomationAccessibilityService? = null

        fun getInstance(): AutomationAccessibilityService? = instance
    }

    private val behaviorSimulator = HumanBehaviorSimulator()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Touch duration for gestures (milliseconds)
    private val touchDurationMin = 80L
    private val touchDurationMax = 200L

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used - we dispatch gestures programmatically rather than reacting to events
    }

    override fun onInterrupt() {
        // Cancel any pending gesture operations
        serviceScope.cancel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    /**
     * Performs a simulated tap/click at the specified screen coordinates.
     * Coordinates are randomized using Gaussian distribution to avoid detection.
     * A random delay is applied before the click to simulate human reaction time.
     *
     * @param x Target X coordinate
     * @param y Target Y coordinate
     * @param callback Optional callback invoked when the gesture completes
     */
    fun performClick(x: Float, y: Float, callback: (() -> Unit)? = null) {
        serviceScope.launch {
            // Apply randomized delay before click (human reaction time simulation)
            val delay = behaviorSimulator.generateClickDelay()
            delay(delay)

            // Randomize coordinates using Gaussian distribution
            val (randomX, randomY) = behaviorSimulator.generateRandomizedCoordinates(x, y)

            // Create click gesture path (a tiny stroke simulating a tap)
            val path = Path().apply {
                moveTo(randomX, randomY)
                lineTo(randomX + 1, randomY + 1) // Minimal movement to register as gesture
            }

            val touchDuration = (touchDurationMin..touchDurationMax).random()

            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path, 0, touchDuration
                    )
                )
                .build()

            withContext(Dispatchers.Main) {
                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        callback?.invoke()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        callback?.invoke()
                    }
                }, mainHandler)
            }
        }
    }

    /**
     * Performs a simulated long-press at the specified coordinates.
     * Long-presses are used for deploying troops or holding buttons in the game.
     *
     * @param x Target X coordinate
     * @param y Target Y coordinate
     * @param duration Long-press duration in milliseconds (default 1000ms)
     * @param callback Optional callback invoked when the gesture completes
     */
    fun performLongPress(x: Float, y: Float, duration: Long = 1000L, callback: (() -> Unit)? = null) {
        serviceScope.launch {
            val delay = behaviorSimulator.generateClickDelay()
            delay(delay)

            val (randomX, randomY) = behaviorSimulator.generateRandomizedCoordinates(x, y)

            val path = Path().apply {
                moveTo(randomX, randomY)
            }

            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path, 0, duration
                    )
                )
                .build()

            withContext(Dispatchers.Main) {
                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        callback?.invoke()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        callback?.invoke()
                    }
                }, mainHandler)
            }
        }
    }

    /**
     * Performs a simulated swipe gesture from start to end coordinates.
     * The swipe path includes slight curvature to mimic human finger movement.
     *
     * @param startX Starting X coordinate
     * @param startY Starting Y coordinate
     * @param endX Ending X coordinate
     * @param endY Ending Y coordinate
     * @param callback Optional callback invoked when the gesture completes
     */
    fun performSwipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        callback: (() -> Unit)? = null
    ) {
        serviceScope.launch {
            val delay = behaviorSimulator.generateSwipeDelay()
            delay(delay)

            // Generate curved path with randomized intermediate points
            val pathPoints = behaviorSimulator.generateCurvedSwipePath(startX, startY, endX, endY)

            val path = Path().apply {
                if (pathPoints.isNotEmpty()) {
                    moveTo(pathPoints[0].first, pathPoints[0].second)
                    for (i in 1 until pathPoints.size) {
                        lineTo(pathPoints[i].first, pathPoints[i].second)
                    }
                }
            }

            val swipeDuration = (400L..900L).random()

            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path, 0, swipeDuration
                    )
                )
                .build()

            withContext(Dispatchers.Main) {
                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        callback?.invoke()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        callback?.invoke()
                    }
                }, mainHandler)
            }
        }
    }

    /**
     * Performs a multi-tap sequence at the specified coordinates.
     * Used for rapid deployment of multiple troops at the same location.
     *
     * @param x Target X coordinate
     * @param y Target Y coordinate
     * @param tapCount Number of taps to perform
     * @param onComplete Callback invoked when all taps are complete
     */
    fun performMultiTap(x: Float, y: Float, tapCount: Int, onComplete: (() -> Unit)? = null) {
        serviceScope.launch {
            repeat(tapCount) {
                val (randomX, randomY) = behaviorSimulator.generateRandomizedCoordinates(x, y)
                val delay = behaviorSimulator.generateClickDelay()
                delay(delay)

                val path = Path().apply {
                    moveTo(randomX, randomY)
                    lineTo(randomX + 1, randomY + 1)
                }

                val touchDuration = (touchDurationMin..touchDurationMax).random()

                val gesture = GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(path, 0, touchDuration)
                    )
                    .build()

                withContext(Dispatchers.Main) {
                    dispatchGesture(gesture, null, mainHandler)
                }

                // Small delay between consecutive taps
                delay((50L..200L).random())
            }
            onComplete?.invoke()
        }
    }

    /**
     * Performs a "thinking" delay to simulate human decision-making time.
     * Used between major actions or state transitions.
     */
    suspend fun performThinkingDelay() {
        val delay = behaviorSimulator.generateThinkingDelay()
        delay(delay)
    }
}
