package com.coc.autobot.vision

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * DISCLAIMER: This project is strictly for educational and research purposes only.
 * Using automation tools violates Clash of Clans Terms of Service and may result in permanent account bans.
 * The user assumes all risks associated with using this software.
 *
 * GameObjectDetector wraps Google ML Kit's Object Detection API to identify game elements
 * on the screen. It processes screenshots from ScreenCaptureService and returns bounding
 * box coordinates for detected game objects.
 *
 * Detected objects are mapped to game-specific labels such as "Attack_Button", "Gold_Mine",
 * "Upgrade_Button", etc. These labels are used by the BotStateMachine to make decisions.
 */
class GameObjectDetector {

    companion object {
        // Confidence threshold for object detection (0.0 - 1.0)
        private const val CONFIDENCE_THRESHOLD = 0.65f

        // Minimum object size to filter out noise (as percentage of screen area)
        private const val MIN_OBJECT_AREA_PERCENT = 0.001f
    }

    /**
     * Data class representing a detected game object with its label and bounding box.
     *
     * @param label The identified game object label (e.g., "Attack_Button")
     * @param boundingBox The rectangle coordinates of the detected object on screen
     * @param confidence The confidence score of the detection (0.0 - 1.0)
     */
    data class DetectedGameObject(
        val label: String,
        val boundingBox: RectF,
        val confidence: Float
    )

    /**
     * Enum of all recognizable game objects in Clash of Clans.
     * These labels are used to identify UI elements and in-game objects.
     */
    enum class GameObjectLabel(val displayName: String) {
        ATTACK_BUTTON("Attack_Button"),
        FIND_MATCH_BUTTON("Find_Match_Button"),
        NEXT_BUTTON("Next_Button"),
        END_BATTLE_BUTTON("End_Battle_Button"),
        RETURN_HOME_BUTTON("Return_Home_Button"),
        GOLD_MINE("Gold_Mine"),
        ELIXIR_COLLECTOR("Elixir_Collector"),
        DARK_ELIXIR_DRILL("Dark_Elixir_Drill"),
        GOLD_STORAGE("Gold_Storage"),
        ELIXIR_STORAGE("Elixir_Storage"),
        DARK_ELIXIR_STORAGE("Dark_Elixir_Storage"),
        TOWN_HALL("Town_Hall"),
        CLAN_CASTLE("Clan_Castle"),
        BUILDER_HUT("Builder_Hut"),
        ARMY_CAMP("Army_Camp"),
        BARRACKS("Barracks"),
        SPELL_FACTORY("Spell_Factory"),
        LABORATORY("Laboratory"),
        UPGRADE_BUTTON("Upgrade_Button"),
        CONFIRM_UPGRADE_BUTTON("Confirm_Upgrade_Button"),
        TRAIN_TROOPS_BUTTON("Train_Troops_Button"),
        TROOP_ICON_BARBARIAN("Troop_Barbarian"),
        TROOP_ICON_ARCHER("Troop_Archer"),
        TROOP_ICON_GIANT("Troop_Giant"),
        TROOP_ICON_WIZARD("Troop_Wizard"),
        TROOP_ICON_DRAGON("Troop_Dragon"),
        TROOP_ICON_PEKKA("Troop_Pekka"),
        DEPLOY_ZONE("Deploy_Zone"),
        RESOURCE_COUNTER_GOLD("Resource_Counter_Gold"),
        RESOURCE_COUNTER_ELIXIR("Resource_Counter_Elixir"),
        RESOURCE_COUNTER_DARK_ELIXIR("Resource_Counter_Dark_Elixir"),
        SHIELD_STATUS("Shield_Status"),
        SHOP_BUTTON("Shop_Button"),
        SETTINGS_BUTTON("Settings_Button"),
        CLAN_BUTTON("Clan_Button"),
        CHAT_BUTTON("Chat_Button"),
        UNKNOWN("Unknown")
    }

    // ML Kit Object Detector instance with stream mode for real-time processing
    private val objectDetector: ObjectDetector

    init {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .enableMultipleObjects()
            .build()
        objectDetector = ObjectDetection.getClient(options)
    }

    /**
     * Processes a screenshot bitmap and detects game objects within it.
     * Uses ML Kit Object Detection to identify elements, then maps them to game-specific labels.
     *
     * @param bitmap The screenshot bitmap to analyze
     * @return List of DetectedGameObject containing labels and bounding boxes
     */
    suspend fun detectObjects(bitmap: Bitmap): List<DetectedGameObject> = withContext(Dispatchers.Default) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val screenArea = bitmap.width * bitmap.height

        try {
            val detectedObjects = detectObjectsAsync(inputImage)
            detectedObjects.mapNotNull { obj ->
                mapToGameObject(obj, screenArea)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Suspends until ML Kit object detection completes.
     * Wraps the ML Kit callback-based API in a coroutine-friendly suspend function.
     *
     * @param inputImage The InputImage to process
     * @return List of ML Kit DetectedObject results
     */
    private suspend fun detectObjectsAsync(inputImage: InputImage): List<DetectedObject> {
        return suspendCancellableCoroutine { continuation ->
            objectDetector.process(inputImage)
                .addOnSuccessListener { objects ->
                    continuation.resume(objects)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
                .addOnCanceledListener {
                    continuation.cancel()
                }
        }
    }

    /**
     * Maps an ML Kit DetectedObject to a game-specific DetectedGameObject.
     * Applies confidence and size filtering to reduce false positives.
     *
     * @param detectedObject The raw detection result from ML Kit
     * @param screenArea Total screen area for size filtering
     * @return DetectedGameObject if the detection passes filters, null otherwise
     */
    private fun mapToGameObject(
        detectedObject: DetectedObject,
        screenArea: Int
    ): DetectedGameObject? {
        val boundingBox = detectedObject.boundingBox
        val objectArea = boundingBox.width() * boundingBox.height()

        // Filter out objects that are too small (likely noise)
        if (objectArea < screenArea * MIN_OBJECT_AREA_PERCENT) {
            return null
        }

        // Get the label with highest confidence from ML Kit's classification
        val labels = detectedObject.labels
        val bestLabel = labels.maxByOrNull { it.confidence }

        val confidence = bestLabel?.confidence ?: 0f
        if (confidence < CONFIDENCE_THRESHOLD) {
            return null
        }

        // Map ML Kit label to game-specific label
        // In production, this would use a custom trained model. For this implementation,
        // we demonstrate the mapping structure. A custom TFLite model with game-specific
        // labels would replace this heuristic mapping.
        val gameLabel = mapMlLabelToGameLabel(bestLabel?.text ?: "", boundingBox)

        return DetectedGameObject(
            label = gameLabel.displayName,
            boundingBox = RectF(
                boundingBox.left.toFloat(),
                boundingBox.top.toFloat(),
                boundingBox.right.toFloat(),
                boundingBox.bottom.toFloat()
            ),
            confidence = confidence
        )
    }

    /**
     * Maps ML Kit's generic labels to game-specific labels based on heuristics.
     * In a production bot, this would be replaced by a custom-trained TFLite model
     * that directly outputs game-specific labels.
     *
     * @param mlLabel The label returned by ML Kit
     * @param boundingBox The bounding box of the detected object
     * @return The corresponding GameObjectLabel
     */
    private fun mapMlLabelToGameLabel(mlLabel: String, boundingBox: android.graphics.Rect): GameObjectLabel {
        // This is a demonstration mapping. In practice, you would:
        // 1. Train a custom object detection model on Clash of Clans screenshots
        // 2. Export it as a TFLite model
        // 3. Use it with ML Kit's custom model API
        //
        // For this educational implementation, we demonstrate the mapping structure.
        // The actual detection would rely on a custom model trained on game screenshots.

        return when {
            mlLabel.contains("button", ignoreCase = true) -> {
                when {
                    boundingBox.centerY() < 200 -> GameObjectLabel.ATTACK_BUTTON
                    boundingBox.centerX() > 800 -> GameObjectLabel.NEXT_BUTTON
                    else -> GameObjectLabel.UPGRADE_BUTTON
                }
            }
            mlLabel.contains("building", ignoreCase = true) -> {
                when {
                    boundingBox.width() > 150 -> GameObjectLabel.TOWN_HALL
                    else -> GameObjectLabel.GOLD_MINE
                }
            }
            mlLabel.contains("icon", ignoreCase = true) -> GameObjectLabel.TROOP_ICON_BARBARIAN
            else -> GameObjectLabel.UNKNOWN
        }
    }

    /**
     * Finds the center point of a detected object.
     * Used by the bot logic to determine where to click.
     *
     * @param detectedObject The detected game object
     * @return Pair of (centerX, centerY) coordinates
     */
    fun getObjectCenter(detectedObject: DetectedGameObject): Pair<Float, Float> {
        val centerX = (detectedObject.boundingBox.left + detectedObject.boundingBox.right) / 2f
        val centerY = (detectedObject.boundingBox.top + detectedObject.boundingBox.bottom) / 2f
        return Pair(centerX, centerY)
    }

    /**
     * Finds all objects matching a specific label in the detection results.
     *
     * @param objects List of detected objects
     * @param label The label to search for
     * @return List of matching DetectedGameObject instances
     */
    fun findObjectsByLabel(
        objects: List<DetectedGameObject>,
        label: String
    ): List<DetectedGameObject> {
        return objects.filter { it.label == label }
    }

    /**
     * Finds the object with the highest confidence matching a specific label.
     *
     * @param objects List of detected objects
     * @param label The label to search for
     * @return The highest-confidence matching object, or null if not found
     */
    fun findMostConfidentObject(
        objects: List<DetectedGameObject>,
        label: String
    ): DetectedGameObject? {
        return objects
            .filter { it.label == label }
            .maxByOrNull { it.confidence }
    }

    /**
     * Releases the ML Kit object detector resources.
     * Should be called when the bot is stopped to free memory.
     */
    fun release() {
        objectDetector.close()
    }
}
