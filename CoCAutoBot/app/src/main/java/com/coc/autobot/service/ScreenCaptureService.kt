package com.coc.autobot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.coc.autobot.R
import com.coc.autobot.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * DISCLAIMER: This project is strictly for educational and research purposes only.
 * Using automation tools violates Clash of Clans Terms of Service and may result in permanent account bans.
 * The user assumes all risks associated with using this software.
 *
 * ScreenCaptureService is a foreground service that continuously captures the device screen
 * using the MediaProjection API. Captured frames are emitted as Bitmap objects via a SharedFlow
 * for consumption by the GameObjectDetector.
 *
 * This service runs as a foreground service to ensure it remains active during bot operation.
 */
class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.coc.autobot.START_CAPTURE"
        const val ACTION_STOP = "com.coc.autobot.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val NOTIFICATION_CHANNEL_ID = "screen_capture_channel"
        const val NOTIFICATION_ID = 1001

        // Frame capture interval in milliseconds (approx 2 FPS to reduce CPU load)
        private const val CAPTURE_INTERVAL_MS = 500L

        // SharedFlow for broadcasting captured frames to subscribers
        private val _screenFrames = MutableSharedFlow<Bitmap>(replay = 1)
        val screenFrames: SharedFlow<Bitmap> = _screenFrames
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var screenDensity: Int = 0

    override fun onCreate() {
        super.onCreate()
        initializeScreenMetrics()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode != -1 && resultData != null) {
                    startCapture(resultCode, resultData)
                }
            }
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Initializes screen dimensions and density from the WindowManager.
     * These values are used to configure the VirtualDisplay and ImageReader.
     */
    private fun initializeScreenMetrics() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    /**
     * Starts the screen capture session using MediaProjection.
     * Creates a VirtualDisplay that mirrors the device screen into an ImageReader.
     *
     * @param resultCode The result code from MediaProjection permission request
     * @param resultData The Intent data from MediaProjection permission request
     */
    private fun startCapture(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        // Create ImageReader to receive captured frames
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        // Create VirtualDisplay that captures the screen
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "CoCAutoBotScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )

        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, buildNotification())

        // Begin periodic frame capture
        serviceScope.launch {
            while (isActive) {
                captureFrame()
                delay(CAPTURE_INTERVAL_MS)
            }
        }
    }

    /**
     * Captures a single frame from the ImageReader and converts it to a Bitmap.
     * The Bitmap is then emitted via the SharedFlow for processing by the vision module.
     */
    private fun captureFrame() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val bitmap = imageToBitmap(image)
            if (bitmap != null) {
                serviceScope.launch {
                    _screenFrames.emit(bitmap)
                }
            }
        } finally {
            image.close()
        }
    }

    /**
     * Converts an Image object (from ImageReader) to a Bitmap.
     * Handles the plane buffer extraction and pixel format conversion.
     *
     * @param image The Image object acquired from ImageReader
     * @return A Bitmap representation of the image, or null if conversion fails
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null

        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    /**
     * Stops the screen capture session and releases all resources.
     * Called when the bot is stopped or the service is destroyed.
     */
    private fun stopCapture() {
        serviceScope.cancel()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
    }

    /**
     * Creates the notification channel required for Android O+ foreground services.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Captures screen for CoCAutoBot vision processing"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the foreground service notification.
     *
     * @return Notification instance for the foreground service
     */
    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("CoCAutoBot Running")
            .setContentText("Screen capture active for bot vision")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
