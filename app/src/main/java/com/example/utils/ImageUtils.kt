package com.example.utils

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object ImageUtils {
    private const val TAG = "ImageUtils"

    // Download an image from a URL and save it to the internal files folder
    suspend fun downloadAndSaveImage(context: Context, urlString: String, fileName: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, fileName)
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doInput = true
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext file.absolutePath
            } else {
                Log.e(TAG, "Server returned response code " + connection.responseCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image: ${e.message}", e)
        }
        return@withContext ""
    }

    // Save a Bitmap to local files directory
    suspend fun saveBitmapToInternal(context: Context, bitmap: Bitmap, fileName: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            return@withContext file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap: ${e.message}", e)
        }
        return@withContext ""
    }

    // Load Uri into Bitmap
    suspend fun loadUriToBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                return@withContext BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap from Uri: ${e.message}", e)
        }
        return@withContext null
    }

    // On-device Face blending compositor
    fun blendFaceSwap(
        sourceFace: Bitmap,
        targetBackground: Bitmap,
        xOffsetPct: Float,    // Center X of target face (0.0 to 1.0)
        yOffsetPct: Float,    // Center Y of target face (0.0 to 1.0)
        scale: Float,         // Scaling factor for source face
        rotation: Float,      // Rotation of source face in degrees
        brightness: Float,    // Brightness offset (-255 to 255)
        contrast: Float,      // Contrast weight (0.5 to 2.0)
        feather: Float,       // Edge feathering size (0.0f to 1.0f)
        skinColorRed: Float = 1.0f,  // Skin color tone R adjustment
        skinColorGreen: Float = 1.0f,// Skin color tone G adjustment
        skinColorBlue: Float = 1.0f  // Skin color tone B adjustment
    ): Bitmap {
        val width = targetBackground.width
        val height = targetBackground.height
        
        // Create working composited bitmap
        val composited = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composited)
        
        // 1. Draw target background
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(targetBackground, 0f, 0f, backgroundPaint)

        // 2. Prepare source face with modifications
        // We will crop details into an oval
        val cropSize = Math.min(sourceFace.width, sourceFace.height)
        val faceOverlay = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)
        val faceCanvas = Canvas(faceOverlay)
        
        // Draw source face centered
        val srcRect = Rect(
            (sourceFace.width - cropSize) / 2,
            (sourceFace.height - cropSize) / 2,
            (sourceFace.width + cropSize) / 2,
            (sourceFace.height + cropSize) / 2
        )
        val destRect = Rect(0, 0, cropSize, cropSize)
        
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        faceCanvas.drawBitmap(sourceFace, srcRect, destRect, facePaint)

        // Apply feathered oval alpha mask
        val maskBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBitmap)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        maskPaint.color = Color.BLACK
        
        // Build soft gradient oval
        val cx = cropSize / 2f
        val cy = cropSize / 2f
        val rx = cropSize * 0.4f
        val ry = cropSize * 0.46f

        // Creating feathering using RadialGradient on mask
        val radialGrad = RadialGradient(
            cx, cy, Math.max(rx, ry),
            intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(0.0f, Math.max(0.0f, 1.0f - feather), 1.0f),
            Shader.TileMode.CLAMP
        )
        maskPaint.shader = radialGrad
        
        // Draw the oval mask
        val oval = RectF(cx - rx, cy - ry, cx + rx, cy + ry)
        maskCanvas.drawOval(oval, maskPaint)
        
        // Overlay the face and the mask using DST_IN
        val blendPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        blendPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        faceCanvas.drawBitmap(maskBitmap, 0f, 0f, blendPaint)

        // 3. Draw face overlay on target canvas with transformations (Scale, Rotate, Translate)
        val transformMatrix = Matrix()
        
        // Match the target center position
        val targetX = width * xOffsetPct
        val targetY = height * yOffsetPct
        
        // Move to origin (crop center) for scaling/rotation
        transformMatrix.postTranslate(-cx, -cy)
        
        // Scale and rotation
        transformMatrix.postScale(scale, scale)
        transformMatrix.postRotate(rotation)
        
        // Move to the target alignment point
        transformMatrix.postTranslate(targetX, targetY)

        // 4. Set lighting adjustment Color Filters (Contrast, Brightness, and Temperature adjustment)
        val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        
        // Contrast & Brightness Matrix:
        // R' = contrast * R + brightness
        // G' = contrast * G + brightness
        // B' = contrast * B + brightness
        val cm = ColorMatrix(floatArrayOf(
            contrast * skinColorRed, 0f, 0f, 0f, brightness,
            0f, contrast * skinColorGreen, 0f, 0f, brightness,
            0f, 0f, contrast * skinColorBlue, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        filterPaint.colorFilter = ColorMatrixColorFilter(cm)

        canvas.drawBitmap(faceOverlay, transformMatrix, filterPaint)
        
        // Recycle helper bitmaps
        faceOverlay.recycle()
        maskBitmap.recycle()
        
        return composited
    }
}

// Data class representing high quality visual template presets for Face Swap
data class PresetTemplate(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String,
    val defaultX: Float,
    val defaultY: Float,
    val defaultScale: Float
) {
    companion object {
        fun getPresets(): List<PresetTemplate> {
            return listOf(
                PresetTemplate(
                    id = "astronaut",
                    name = "Lunar Explorer",
                    description = "An astronaut floating in high orbit with Earth visible in the helmet visor reflection.",
                    imageUrl = "https://images.unsplash.com/photo-1614728894747-a83421e2b9c9?w=600&auto=format&fit=crop&q=80",
                    defaultX = 0.50f,
                    defaultY = 0.32f,
                    defaultScale = 0.28f
                ),
                PresetTemplate(
                    id = "noble_knight",
                    name = "Vanguard Royal Knight",
                    description = "A knight in shining silver armor standing on a misty medieval battlefield.",
                    imageUrl = "https://images.unsplash.com/photo-1599727497184-fa8669b77549?w=600&auto=format&fit=crop&q=80",
                    defaultX = 0.49f,
                    defaultY = 0.23f,
                    defaultScale = 0.24f
                ),
                PresetTemplate(
                    id = "cyberpunk",
                    name = "Neo-Neon Hacker",
                    description = "A futuristic character with bright cyan and magenta overlays on a neon Tokyo street background.",
                    imageUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600&auto=format&fit=crop&q=80",
                    defaultX = 0.51f,
                    defaultY = 0.32f,
                    defaultScale = 0.32f
                ),
                PresetTemplate(
                    id = "oil_painting",
                    name = "Classical Masterpiece Portrait",
                    description = "An fine art renaissance oil painting style portrait with warm ambient lighting.",
                    imageUrl = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=600&auto=format&fit=crop&q=80",
                    defaultX = 0.50f,
                    defaultY = 0.38f,
                    defaultScale = 0.42f
                ),
                PresetTemplate(
                    id = "ancient_emperor",
                    name = "Roman Laurel Emperor",
                    description = "Laurel wreath crowning an emperor standing before classical white Roman marble columns.",
                    imageUrl = "https://images.unsplash.com/photo-1608155686393-8fdd966d784d?w=600&auto=format&fit=crop&q=80",
                    defaultX = 0.50f,
                    defaultY = 0.25f,
                    defaultScale = 0.30f
                )
            )
        }
    }
}
