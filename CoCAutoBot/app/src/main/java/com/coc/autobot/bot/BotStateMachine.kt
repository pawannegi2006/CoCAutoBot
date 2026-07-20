package com.coc.autobot.bot

import android.util.Log
import com.coc.autobot.service.AutomationAccessibilityService
import com.coc.autobot.util.HumanBehaviorSimulator
import com.coc.autobot.vision.GameObjectDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * DISCLAIMER: This project is strictly for educational and research purposes only.
 * Using automation tools violates Clash of Clans Terms of Service and may result in permanent account bans.
 * The user assumes all risks associated with using this software.
 *
 * BotStateMachine manages the bot's behavior using a Finite State Machine (FSM).
 * It transitions between states based on game conditions and executes appropriate actions.
 *
 * States:
 * - IDLE: Bot is inactive, waiting for start command
 * - COLLECTING_RESOURCES: Automatically collects resources from collectors
 * - SEARCHING_OPPONENT: Searches for attack targets
 * - DEPLOYING_TROOPS: Deploys troops during an attack
 * - UPGRADING_BUILDINGS: Upgrades buildings and walls when resources are sufficient
 * - RESTING: Simulating a player break (anti-ban measure)
 */
class BotStateMachine(
    private val objectDetector: GameObjectDetector,
    private val behaviorSimulator: HumanBehaviorSimulator,
    private val settings: BotSettings
) {

    companion object {
        private const val TAG = "BotStateMachine"

        // Maximum attempts for a single action before giving up
        private const val MAX_ACTION_ATTEMPTS = 5

        // Delay between state checks when no action is taken
        private const val IDLE_CHECK_INTERVAL_MS = 2000L

        // Minimum resources required before considering an upgrade (in millions)
        private const val MIN_GOLD_FOR_UPGRADE = 1_000_000L
        private const val MIN_ELIXIR_FOR_UPGRADE = 1_000_000L
    }

    /**
     * Enum representing all possible bot states.
     */
    enum class BotState {
        IDLE,
        COLLECTING_RESOURCES,
        SEARCHING_OPPONENT,
        DEPLOYING_TROOPS,
        UPGRADING_BUILDINGS,
        RESTING
    }

    /**
     * Data class holding bot configuration settings.
     */
    data class BotSettings(
        val attackStrategy: AttackStrategy = AttackStrategy.BARCH,
        val maxOnlineHours: Int = 3,
        val skipUpgradeIfLowResources: Boolean = true,
        val enableAntiBan: Boolean = true
    )

    /**
     * Enum representing available attack strategies.
     */
    enum class AttackStrategy(val displayName: String) {
        BARCH("Barbarians & Archers"),
        GIANT_HEALER("Giant & Healer"),
        DRAGON_RUSH("Dragon Rush"),
        GO_WI_PE("GoWiPe"),
        GOBLIN_SNIPING("Goblin Sniping")
    }

    private val botScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var accessibilityService: AutomationAccessibilityService? = null

    // Current state exposed as StateFlow for UI observation
    private val _currentState = MutableStateFlow(BotState.IDLE)
    val currentState: StateFlow<BotState> = _currentState

    // Current action description for logging
    private val _currentAction = MutableStateFlow("Bot is idle")
    val currentAction: StateFlow<String> = _currentAction

    // Session tracking for anti-ban rest periods
    private var sessionStartTime: Long = 0L
    private var isRunning = false

    /**
     * Starts the bot state machine.
     * Initializes the session timer and begins the main state loop.
     *
     * @param accessibilityService The accessibility service for touch simulation
     */
    fun start(accessibilityService: AutomationAccessibilityService) {
        if (isRunning) return

        this.accessibilityService = accessibilityService
        isRunning = true
        sessionStartTime = System.currentTimeMillis()

        _currentState.value = BotState.IDLE
        _currentAction.value = "Bot started - entering main loop"

        botScope.launch {
            while (isActive && isRunning) {
                // Check if max online time has been reached (anti-ban)
                if (shouldTakeRest()) {
                    transitionToState(BotState.RESTING)
                    performRestPeriod()
                    sessionStartTime = System.currentTimeMillis()
                    continue
                }

                // Execute logic based on current state
                when (_currentState.value) {
                    BotState.IDLE -> handleIdleState()
                    BotState.COLLECTING_RESOURCES -> handleCollectingResources()
                    BotState.UPGRADING_BUILDINGS -> handleUpgradingBuildings()
                    BotState.SEARCHING_OPPONENT -> handleSearchingOpponent()
                    BotState.DEPLOYING_TROOPS -> handleDeployingTroops()
                    BotState.RESTING -> { /* Handled by shouldTakeRest check */ }
                }

                delay(IDLE_CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * Stops the bot state machine and cancels all coroutines.
     */
    fun stop() {
        isRunning = false
        botScope.cancel()
        _currentState.value = BotState.IDLE
        _currentAction.value = "Bot stopped"
        accessibilityService = null
    }

    /**
     * Handles the IDLE state.
     * Determines the next action: collect resources, upgrade, or search for opponent.
     */
    private suspend fun handleIdleState() {
        _currentAction.value = "Deciding next action..."
        accessibilityService?.performThinkingDelay()

        // Priority 1: Collect available resources
        transitionToState(BotState.COLLECTING_RESOURCES)
    }

    /**
     * Handles resource collection by finding and clicking on resource collectors.
     * Searches for Gold Mines, Elixir Collectors, and Dark Elixir Drills.
     */
    private suspend fun handleCollectingResources() {
        _currentAction.value = "Collecting resources..."
        Log.d(TAG, "Collecting resources")

        // Note: In a real implementation, this would process the latest screenshot
        // from ScreenCaptureService and use GameObjectDetector to find collectors.
        // For this educational implementation, we demonstrate the logic flow.

        val service = accessibilityService ?: return

        // Simulate finding and clicking on resource collectors
        // In production, these coordinates would come from GameObjectDetector
        val collectors = listOf(
            Pair(200f, 400f),  // Gold Mine 1
            Pair(500f, 350f),  // Elixir Collector 1
            Pair(800f, 500f),  // Gold Mine 2
            Pair(300f, 700f),  // Dark Elixir Drill
            Pair(700f, 200f)   // Elixir Collector 2
        )

        for ((index, collector) in collectors.withIndex()) {
            if (!isRunning) break

            _currentAction.value = "Collecting from collector ${index + 1}/${collectors.size}"

            // Randomized click on collector
            service.performClick(collector.first, collector.second)

            // Random delay between collections
            val delay = behaviorSimulator.generateClickDelay()
            delay(delay)
        }

        _currentAction.value = "Resource collection complete"

        // After collecting, decide next action
        if (shouldUpgradeBuildings()) {
            transitionToState(BotState.UPGRADING_BUILDINGS)
        } else {
            transitionToState(BotState.SEARCHING_OPPONENT)
        }
    }

    /**
     * Handles building and wall upgrades when sufficient resources are available.
     * Checks resource levels and initiates upgrade sequences.
     */
    private suspend fun handleUpgradingBuildings() {
        _currentAction.value = "Checking for upgrade opportunities..."
        Log.d(TAG, "Upgrading buildings")

        val service = accessibilityService ?: return

        if (settings.skipUpgradeIfLowResources && !hasSufficientResources()) {
            _currentAction.value = "Insufficient resources for upgrade, skipping..."
            transitionToState(BotState.SEARCHING_OPPONENT)
            return
        }

        // Simulate finding and clicking on upgradeable buildings
        // In production, GameObjectDetector would identify buildings with green upgrade indicators
        val upgradeTargets = listOf(
            Pair(400f, 300f),  // Town Hall
            Pair(600f, 450f),  // Wall section
            Pair(250f, 550f)   // Defense building
        )

        for ((index, target) in upgradeTargets.withIndex()) {
            if (!isRunning) break

            _currentAction.value = "Upgrading building ${index + 1}/${upgradeTargets.size}"

            // Click on building
            service.performClick(target.first, target.second)
            delay(behaviorSimulator.generateClickDelay())

            // Click upgrade button
            service.performClick(600f, 800f) // Upgrade button position
            delay(behaviorSimulator.generateClickDelay())

            // Confirm upgrade
            service.performClick(500f, 700f) // Confirm button position
            delay(behaviorSimulator.generateThinkingDelay())
        }

        _currentAction.value = "Upgrade sequence complete"
        transitionToState(BotState.IDLE)
    }

    /**
     * Handles searching for opponents to attack.
     * Clicks the Attack button, then Find Match, and evaluates bases.
     */
    private suspend fun handleSearchingOpponent() {
        _currentAction.value = "Searching for opponent..."
        Log.d(TAG, "Searching for opponent")

        val service = accessibilityService ?: return

        // Click Attack button
        service.performClick(900f, 80f)
        delay(behaviorSimulator.generateThinkingDelay())

        // Click Find Match (Multiplayer)
        service.performClick(500f, 400f)
        delay(behaviorSimulator.generateThinkingDelay() * 2) // Wait for matchmaking

        // Evaluate opponent base (in production, would analyze screenshot)
        val shouldAttack = evaluateOpponentBase()

        if (shouldAttack) {
            _currentAction.value = "Opponent found - preparing to attack"
            transitionToState(BotState.DEPLOYING_TROOPS)
        } else {
            _currentAction.value = "Base too strong, searching for next opponent"
            // Click Next button
            service.performClick(900f, 500f)
            delay(behaviorSimulator.generateThinkingDelay())
        }
    }

    /**
     * Handles troop deployment during an attack.
     * Deploys troops according to the selected attack strategy.
     */
    private suspend fun handleDeployingTroops() {
        _currentAction.value = "Deploying troops - Strategy: ${settings.attackStrategy.displayName}"
        Log.d(TAG, "Deploying troops with strategy: ${settings.attackStrategy.displayName}")

        val service = accessibilityService ?: return

        // Deploy troops based on selected strategy
        when (settings.attackStrategy) {
            AttackStrategy.BARCH -> deployBarchStrategy(service)
            AttackStrategy.GIANT_HEALER -> deployGiantHealerStrategy(service)
            AttackStrategy.DRAGON_RUSH -> deployDragonRushStrategy(service)
            AttackStrategy.GO_WI_PE -> deployGoWiPeStrategy(service)
            AttackStrategy.GOBLIN_SNIPING -> deployGoblinSnipingStrategy(service)
        }

        // Wait for battle to complete
        _currentAction.value = "Battle in progress..."
        delay(120_000L) // 2 minutes battle duration

        // End battle
        service.performClick(900f, 100f) // End Battle button
        delay(behaviorSimulator.generateThinkingDelay())

        // Return home
        service.performClick(500f, 600f) // Return Home button
        delay(behaviorSimulator.generateThinkingDelay())

        _currentAction.value = "Battle complete - returning to base"
        transitionToState(BotState.IDLE)
    }

    /**
     * Deploys Barbarians and Archers (BARCH) strategy.
     * Drops barbarians first to absorb damage, then archers from the sides.
     */
    private suspend fun deployBarchStrategy(service: AutomationAccessibilityService) {
        _currentAction.value = "Deploying Barbarians..."

        // Deploy Barbarians in a line
        val barbarianDrops = listOf(
            Pair(100f, 300f), Pair(200f, 280f), Pair(300f, 290f),
            Pair(400f, 310f), Pair(500f, 300f)
        )
        for (drop in barbarianDrops) {
            service.performClick(drop.first, drop.second)
            delay((300L..800L).random())
        }

        delay(behaviorSimulator.generateThinkingDelay())
        _currentAction.value = "Deploying Archers..."

        // Deploy Archers behind Barbarians
        val archerDrops = listOf(
            Pair(120f, 350f), Pair(220f, 330f), Pair(320f, 340f),
            Pair(420f, 360f), Pair(520f, 350f)
        )
        for (drop in archerDrops) {
            service.performClick(drop.first, drop.second)
            delay((300L..800L).random())
        }
    }

    /**
     * Deploys Giant and Healer strategy.
     * Giants tank damage while Healers keep them alive.
     */
    private suspend fun deployGiantHealerStrategy(service: AutomationAccessibilityService) {
        _currentAction.value = "Deploying Giants..."

        val giantDrops = listOf(
            Pair(300f, 250f), Pair(350f, 260f), Pair(400f, 255f)
        )
        for (drop in giantDrops) {
            service.performClick(drop.first, drop.second)
            delay((500L..1000L).random())
        }

        delay(behaviorSimulator.generateThinkingDelay() * 2)
        _currentAction.value = "Deploying Healers..."

        val healerDrops = listOf(
            Pair(320f, 300f), Pair(380f, 310f)
        )
        for (drop in healerDrops) {
            service.performClick(drop.first, drop.second)
            delay((500L..1000L).random())
        }
    }

    /**
     * Deploys Dragon Rush strategy.
     * Mass dragon deployment for high-damage attacks.
     */
    private suspend fun deployDragonRushStrategy(service: AutomationAccessibilityService) {
        _currentAction.value = "Deploying Dragons..."

        val dragonDrops = listOf(
            Pair(150f, 200f), Pair(250f, 210f), Pair(350f, 205f),
            Pair(450f, 215f), Pair(550f, 200f), Pair(650f, 210f)
        )
        for (drop in dragonDrops) {
            service.performClick(drop.first, drop.second)
            delay((800L..1500L).random())
        }
    }

    /**
     * Deploys GoWiPe (Golem, Wizards, Pekka) strategy.
     * Heavy ground attack with tanking and damage support.
     */
    private suspend fun deployGoWiPeStrategy(service: AutomationAccessibilityService) {
        _currentAction.value = "Deploying Golems..."

        service.performClick(300f, 250f)
        delay(behaviorSimulator.generateThinkingDelay())

        _currentAction.value = "Deploying Wizards..."
        val wizardDrops = listOf(
            Pair(280f, 300f), Pair(320f, 310f), Pair(360f, 295f)
        )
        for (drop in wizardDrops) {
            service.performClick(drop.first, drop.second)
            delay((400L..900L).random())
        }

        delay(behaviorSimulator.generateThinkingDelay())
        _currentAction.value = "Deploying PEKKAs..."
        service.performClick(340f, 280f)
    }

    /**
     * Deploys Goblin Sniping strategy.
     * Targets resource collectors on the outside of the base.
     */
    private suspend fun deployGoblinSnipingStrategy(service: AutomationAccessibilityService) {
        _currentAction.value = "Sniping resources with Goblins..."

        val goblinDrops = listOf(
            Pair(100f, 400f), Pair(800f, 400f), Pair(100f, 600f), Pair(800f, 600f)
        )
        for (drop in goblinDrops) {
            service.performMultiTap(drop.first, drop.second, 5)
            delay((500L..1000L).random())
        }
    }

    /**
     * Performs a rest period to simulate human breaks (anti-ban measure).
     * The bot pauses for 15-30 minutes before resuming.
     */
    private suspend fun performRestPeriod() {
        val restMinutes = behaviorSimulator.generateRestPeriod()
        _currentAction.value = "Taking a break for $restMinutes minutes (anti-ban measure)"
        Log.d(TAG, "Resting for $restMinutes minutes")

        val restMs = restMinutes * 60L * 1000L
        delay(restMs)

        _currentAction.value = "Break complete - resuming bot operations"
        transitionToState(BotState.IDLE)
    }

    /**
     * Checks if the bot should take a rest period based on session duration.
     *
     * @return true if max online time has been reached
     */
    private fun shouldTakeRest(): Boolean {
        if (!settings.enableAntiBan) return false

        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        val maxDuration = behaviorSimulator.generateSessionDuration(settings.maxOnlineHours)
        return sessionDuration >= maxDuration
    }

    /**
     * Determines if the bot has sufficient resources for upgrades.
     *
     * @return true if resources exceed minimum thresholds
     */
    private fun hasSufficientResources(): Boolean {
        // In production, this would read resource counters from the game screen
        // using GameObjectDetector. For this implementation, we use a probabilistic check.
        return kotlin.random.Random.nextFloat() > 0.3f
    }

    /**
     * Determines if buildings should be upgraded based on settings and resources.
     *
     * @return true if upgrade logic should execute
     */
    private fun shouldUpgradeBuildings(): Boolean {
        if (settings.skipUpgradeIfLowResources && !hasSufficientResources()) {
            return false
        }
        // Random chance to prioritize upgrades vs attacks
        return kotlin.random.Random.nextFloat() > 0.6f
    }

    /**
     * Evaluates whether the current opponent base is worth attacking.
     * In production, this would analyze the base layout and available loot.
     *
     * @return true if the base should be attacked
     */
    private fun evaluateOpponentBase(): Boolean {
        // Simulate base evaluation - in production would analyze screenshot
        // Check for exposed Town Hall, available loot, weak defenses, etc.
        return kotlin.random.Random.nextFloat() > 0.2f // 80% chance to attack
    }

    /**
     * Transitions the bot to a new state and logs the change.
     *
     * @param newState The state to transition to
     */
    private fun transitionToState(newState: BotState) {
        val oldState = _currentState.value
        _currentState.value = newState
        Log.d(TAG, "State transition: $oldState -> $newState")
    }
}
