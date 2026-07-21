package com.coc.autobot.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * DISCLAIMER: This project is strictly for educational and research purposes only.
 * Using automation tools violates Clash of Clans Terms of Service and may result in permanent account bans.
 * The user assumes all risks associated with using this software.
 *
 * HumanBehaviorSimulator generates human-like random delays and coordinate offsets
 * to make automated actions appear more natural and less detectable.
 *
 * Uses Gaussian (normal) distribution for coordinate randomization and
 * exponential distribution with jitter for timing delays.
 */
class HumanBehaviorSimulator {

    companion object {
        // Standard deviation for click coordinate offsets (in pixels)
        private const val CLICK_OFFSET_STD_DEV = 8.0

        // Minimum and maximum delay multipliers for different action types
        private const val MIN_CLICK_DELAY_MS = 150L
        private const val MAX_CLICK_DELAY_MS = 800L
        private const val MIN_SWIPE_DELAY_MS = 300L
        private const val MAX_SWIPE_DELAY_MS = 1200L
        private const val MIN_THINKING_DELAY_MS = 500L
        private const val MAX_THINKING_DELAY_MS = 3000L

        // Rest period configuration (simulating player breaks)
        private const val MIN_REST_MINUTES = 15
        private const val MAX_REST_MINUTES = 30
    }

    private val random = Random(System.currentTimeMillis())

    /**
     * Generates a random coordinate offset using Gaussian (normal) distribution.
     * This creates a bell-curve pattern of clicks around the target point,
     * mimicking human imprecision in touch input.
     *
     * @param centerX The target X coordinate
     * @param centerY The target Y coordinate
     * @return A Pair of (randomizedX, randomizedY) coordinates
     */
    fun generateRandomizedCoordinates(centerX: Float, centerY: Float): Pair<Float, Float> {
        val offsetX = generateGaussianRandom(0.0, CLICK_OFFSET_STD_DEV).toFloat()
        val offsetY = generateGaussianRandom(0.0, CLICK_OFFSET_STD_DEV).toFloat()
        return Pair(centerX + offsetX, centerY + offsetY)
    }

    /**
     * Generates a random delay before performing a click action.
     * Uses a combination of base delay and random jitter to simulate
     * human reaction time variability.
     *
     * @return Delay in milliseconds
     */
    fun generateClickDelay(): Long {
        val baseDelay = random.nextLong(MIN_CLICK_DELAY_MS, MAX_CLICK_DELAY_MS)
        val jitter = generateGaussianRandom(0.0, 50.0).toLong().coerceAtLeast(-100)
        return (baseDelay + jitter).coerceAtLeast(MIN_CLICK_DELAY_MS)
    }

    /**
     * Generates a random delay before performing a swipe gesture.
     * Swipes typically take longer than simple clicks.
     *
     * @return Delay in milliseconds
     */
    fun generateSwipeDelay(): Long {
        val baseDelay = random.nextLong(MIN_SWIPE_DELAY_MS, MAX_SWIPE_DELAY_MS)
        val jitter = generateGaussianRandom(0.0, 100.0).toLong().coerceAtLeast(-200)
        return (baseDelay + jitter).coerceAtLeast(MIN_SWIPE_DELAY_MS)
    }

    /**
     * Generates a "thinking" delay to simulate human decision-making time.
     * Used between major state transitions or before important actions.
     *
     * @return Delay in milliseconds
     */
    fun generateThinkingDelay(): Long {
        val baseDelay = random.nextLong(MIN_THINKING_DELAY_MS, MAX_THINKING_DELAY_MS)
        val jitter = generateGaussianRandom(0.0, 200.0).toLong().coerceAtLeast(-300)
        return (baseDelay + jitter).coerceAtLeast(MIN_THINKING_DELAY_MS)
    }

    /**
     * Generates a random rest period duration (in minutes).
     * Called when the bot needs to simulate a player taking a break.
     *
     * @return Rest duration in minutes
     */
    fun generateRestPeriod(): Int {
        return random.nextInt(MIN_REST_MINUTES, MAX_REST_MINUTES + 1)
    }

    /**
     * Generates a random session duration within the user's configured maximum.
     * Adds variance so sessions don't always end at exactly the same time.
     *
     * @param maxHours User-configured maximum online time in hours
     * @return Actual session duration in milliseconds
     */
    fun generateSessionDuration(maxHours: Int): Long {
        val maxMs = maxHours * 60L * 60L * 1000L
        // Vary by +/- 10% to avoid predictable patterns
        val variance = (maxMs * 0.1).toLong()
        val jitter = random.nextLong(-variance, variance)
        return (maxMs + jitter).coerceAtLeast(maxMs / 2)
    }

    /**
     * Generates a random swipe path with slight curvature to mimic human finger movement.
     * Real human swipes are rarely perfectly straight lines.
     *
     * @param startX Starting X coordinate
     * @param startY Starting Y coordinate
     * @param endX Ending X coordinate
     * @param endY Ending Y coordinate
     * @param steps Number of intermediate points in the path
     * @return List of coordinate pairs forming the swipe path
     */
    fun generateCurvedSwipePath(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        steps: Int = 10
    ): List<Pair<Float, Float>> {
        val path = mutableListOf<Pair<Float, Float>>()
        val curveAmount = generateGaussianRandom(0.0, 20.0).toFloat()

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val linearX = startX + (endX - startX) * t
            val linearY = startY + (endY - startY) * t

            // Add slight curve using sine function
            val curveOffset = curveAmount * kotlin.math.sin(t * PI.toFloat())
            val perpX = -(endY - startY)
            val perpY = (endX - startX)
            val length = sqrt(perpX * perpX + perpY * perpY)
            val normalizedPerpX = if (length > 0) perpX / length else 0f
            val normalizedPerpY = if (length > 0) perpY / length else 0f

            val finalX = linearX + normalizedPerpX * curveOffset
            val finalY = linearY + normalizedPerpY * curveOffset

            path.add(Pair(finalX, finalY))
        }
        return path
    }

    /**
     * Generates a random value from a Gaussian (normal) distribution using the Box-Muller transform.
     * This produces values clustered around the mean with the specified standard deviation,
     * creating a natural bell-curve distribution.
     *
     * @param mean The center value of the distribution
     * @param stdDev The standard deviation (spread) of the distribution
     * @return A random double following normal distribution
     */
    private fun generateGaussianRandom(mean: Double, stdDev: Double): Double {
        val u1 = random.nextDouble()
        val u2 = random.nextDouble()
        val z0 = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
        return mean + z0 * stdDev
    }
}
