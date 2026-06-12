package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.AiEngine.ArtStyle
import com.example.data.AiEngine.VideoTheme
import com.example.utils.AudioSynth
import com.example.utils.ImageUtils
import com.example.utils.PresetTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

sealed class HubUiState {
    object Idle : HubUiState()
    object Loading : HubUiState()
    data class Success(val message: String) : HubUiState()
    data class Error(val exceptionMessage: String) : HubUiState()
}

class CreativeHubViewModel(private val repository: CreationRepository) : ViewModel() {
    private val TAG = "CreativeHubViewModel"

    // Creation list flow from database
    val creationHistory: StateFlow<List<Creation>> = repository.allCreations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current screen Tab navigation
    val currentTab = MutableStateFlow(Tab.IMAGE_CREATOR)

    enum class Tab {
        IMAGE_CREATOR, VIDEO_LAB, FACE_SWAP, GALLERY
    }

    // --- Image Creator State ---
    val imagePrompt = MutableStateFlow("")
    val imageArtStyle = MutableStateFlow(ArtStyle.NATURAL)
    val expandedImagePrompt = MutableStateFlow("")
    val generatedImageUrl = MutableStateFlow("")
    val imageUiState = MutableStateFlow<HubUiState>(HubUiState.Idle)

    // --- Video Lab State ---
    val videoPrompt = MutableStateFlow("")
    val videoTheme = MutableStateFlow(VideoTheme.SCIFI_SPACE)
    val videoUiState = MutableStateFlow<HubUiState>(HubUiState.Idle)
    val storyboardScenes = MutableStateFlow<List<StoryboardScene>>(emptyList())
    val videoKeyframeUrls = MutableStateFlow<Map<Int, String>>(emptyMap())
    
    // Playback state of compiled video
    val isPlayingVideo = MutableStateFlow(false)
    val currentFrameIndex = MutableStateFlow(0)

    // --- Face Swap Studio State ---
    val presetTemplates = MutableStateFlow(PresetTemplate.getPresets())
    val selectedPreset = MutableStateFlow<PresetTemplate?>(PresetTemplate.getPresets().first())
    val customTargetUri = MutableStateFlow<Uri?>(null) // User custom backgrounds
    
    val sourceFaceUri = MutableStateFlow<Uri?>(null)
    val sourceFaceBitmap = MutableStateFlow<Bitmap?>(null)
    val targetBaseBitmap = MutableStateFlow<Bitmap?>(null)
    
    // Sliders for pixel alignment and color correction
    val faceXOffset = MutableStateFlow(0.5f)
    val faceYOffset = MutableStateFlow(0.35f)
    val faceScale = MutableStateFlow(1.0f)
    val faceRotation = MutableStateFlow(0.0f)
    val faceFeather = MutableStateFlow(0.40f)
    val faceBrightness = MutableStateFlow(0.0f)
    val faceContrast = MutableStateFlow(1.0f)
    val faceColorR = MutableStateFlow(1.0f)
    val faceColorG = MutableStateFlow(1.0f)
    val faceColorB = MutableStateFlow(1.0f)
    
    val swappedBitmapResult = MutableStateFlow<Bitmap?>(null)
    val faceSwapUiState = MutableStateFlow<HubUiState>(HubUiState.Idle)

    // General app status notification
    val generalNotification = MutableStateFlow<String?>(null)

    // Toggle Tab selector
    fun setTab(tab: Tab) {
        currentTab.value = tab
        // Stop music automatically if leaving video lab
        if (tab != Tab.VIDEO_LAB) {
            stopVideoPlayback()
        }
    }

    // Clear notification
    fun clearNotification() {
        generalNotification.value = null
    }

    // ----------------------------------------------------
    // IMAGE CREATOR CONTROLLERS
    // ----------------------------------------------------
    fun generateImage() {
        if (imagePrompt.value.trim().isEmpty()) {
            imageUiState.value = HubUiState.Error("Please enter a visual prompt description first!")
            return
        }

        imageUiState.value = HubUiState.Loading
        viewModelScope.launch {
            try {
                // Step 1: Expand prompt with Gemini if available, for optimal details
                val expanded = AiEngine.expandPromptWithGemini(imagePrompt.value.trim())
                expandedImagePrompt.value = expanded

                // Step 2: Build actual image URL
                val seed = (100000L..999999L).random()
                val url = AiEngine.buildImageUrl(expanded, imageArtStyle.value, seed)
                generatedImageUrl.value = url
                
                imageUiState.value = HubUiState.Success("Image generated successfully!")
            } catch (e: Exception) {
                Log.e(TAG, "Error in generateImage", e)
                imageUiState.value = HubUiState.Error("Failed to trigger generation: ${e.message}")
            }
        }
    }

    fun saveGeneratedImageToHistory(context: Context) {
        val url = generatedImageUrl.value
        val promptText = imagePrompt.value
        if (url.isEmpty()) return

        imageUiState.value = HubUiState.Loading
        viewModelScope.launch {
            try {
                val uniqueId = UUID.randomUUID().toString().substring(0, 8)
                val fileName = "ai_image_$uniqueId.jpg"
                
                // Download from web and write local file inside filesDir
                val savedLocalPath = ImageUtils.downloadAndSaveImage(context, url, fileName)
                
                if (savedLocalPath.isNotEmpty()) {
                    val title = if (promptText.length > 25) promptText.take(22) + "..." else promptText
                    val creation = Creation(
                        title = "AI " + title.trim(),
                        prompt = promptText.trim(),
                        type = CreationType.IMAGE,
                        fileUri = savedLocalPath,
                        configurationJson = JSONObject().apply {
                            put("art_style", imageArtStyle.value.displayName)
                            put("expanded_prompt", expandedImagePrompt.value)
                        }.toString()
                    )
                    repository.insert(creation)
                    imageUiState.value = HubUiState.Success("Image saved to History!")
                    generalNotification.value = "Image saved to History successfully!"
                } else {
                    imageUiState.value = HubUiState.Error("Failed to download or write image file to device storage.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed storing generated image", e)
                imageUiState.value = HubUiState.Error("Failed to save image: ${e.message}")
            }
        }
    }

    // ----------------------------------------------------
    // VIDEO LAB CONTROLLERS
    // ----------------------------------------------------
    fun compileVideo() {
        if (videoPrompt.value.trim().isEmpty()) {
            videoUiState.value = HubUiState.Error("Please enter a video concept prompt first!")
            return
        }

        stopVideoPlayback()
        videoUiState.value = HubUiState.Loading
        viewModelScope.launch {
            try {
                // Step 1: Call Gemini to write a 4-scene storyboard screenplay
                val storyboard = AiEngine.generateVideoStoryboardWithGemini(videoPrompt.value.trim())
                storyboardScenes.value = storyboard

                // Step 2: For each scene, request Pollinations details according to theme
                val urls = mutableMapOf<Int, String>()
                val themeModifier = videoTheme.value.promptModifier
                
                storyboard.forEach { scene ->
                    val sceneStyledPrompt = "${scene.scenePrompt}, $themeModifier, ultra fluid parallax motion, cinematic video still"
                    val seed = (100000L..999999L).random()
                    // Encode url
                    val seedUrl = AiEngine.buildImageUrl(sceneStyledPrompt, ArtStyle.NATURAL, seed)
                    urls[scene.sceneNumber] = seedUrl
                }

                videoKeyframeUrls.value = urls
                currentFrameIndex.value = 0
                videoUiState.value = HubUiState.Success("Video compiled successfully! Tap Play to watch with ambient sound synth.")
                
                // Auto play video
                playVideo()
            } catch (e: Exception) {
                Log.e(TAG, "Error compiling video", e)
                videoUiState.value = HubUiState.Error("Failed compiling movie: ${e.message}")
            }
        }
    }

    fun playVideo() {
        if (videoKeyframeUrls.value.isEmpty()) return
        
        isPlayingVideo.value = true
        // Start Audio synthesis for background cinematic drone
        AudioSynth.start(videoTheme.value)
    }

    fun stopVideoPlayback() {
        isPlayingVideo.value = false
        AudioSynth.stop()
    }

    fun advanceVideoTick() {
        if (!isPlayingVideo.value) return
        val sceneCount = storyboardScenes.value.size
        if (sceneCount > 0) {
            val nextFrame = (currentFrameIndex.value + 1) % sceneCount
            currentFrameIndex.value = nextFrame
        }
    }

    fun saveVideoToHistory(context: Context) {
        val promptText = videoPrompt.value
        val urls = videoKeyframeUrls.value
        val scenes = storyboardScenes.value
        if (urls.isEmpty() || scenes.isEmpty()) return

        videoUiState.value = HubUiState.Loading
        viewModelScope.launch {
            try {
                val jsonConfig = JSONObject().apply {
                    put("theme", videoTheme.value.name)
                    put("frequency_music", videoTheme.value.musicGenre)
                    
                    val scenesArray = JSONArray()
                    scenes.forEach { scene ->
                        scenesArray.put(JSONObject().apply {
                            put("number", scene.sceneNumber)
                            put("prompt", scene.scenePrompt)
                            put("subtitle", scene.subtitle)
                            put("url", urls[scene.sceneNumber] ?: "")
                        })
                    }
                    put("scenes", scenesArray)
                }

                // Download the first keyframe to represent the cover of this video project
                val uniqueId = UUID.randomUUID().toString().substring(0, 8)
                val coverFileName = "video_cover_$uniqueId.jpg"
                val coverLocalPath = ImageUtils.downloadAndSaveImage(context, urls[1] ?: "", coverFileName)

                val displayTitle = if (promptText.length > 25) promptText.take(22) + "..." else promptText
                val creation = Creation(
                    title = "Video: " + displayTitle.trim(),
                    prompt = promptText.trim(),
                    type = CreationType.VIDEO,
                    fileUri = if (coverLocalPath.isNotEmpty()) coverLocalPath else urls[1] ?: "",
                    configurationJson = jsonConfig.toString()
                )

                repository.insert(creation)
                videoUiState.value = HubUiState.Success("Video Project saved to History!")
                generalNotification.value = "Video project saved to gallery library!"
            } catch (e: Exception) {
                Log.e(TAG, "Error saving video", e)
                videoUiState.value = HubUiState.Error("Failed saving movie project: ${e.message}")
            }
        }
    }

    // ----------------------------------------------------
    // FACE SWAP TOLL CONTROLLERS
    // ----------------------------------------------------
    fun selectPreset(preset: PresetTemplate, context: Context) {
        selectedPreset.value = preset
        customTargetUri.value = null
        loadTargetTemplateBitmap(preset, context)
    }

    fun selectCustomTarget(uri: Uri, context: Context) {
        selectedPreset.value = null
        customTargetUri.value = uri
        faceSwapUiState.value = HubUiState.Loading
        viewModelScope.launch {
            try {
                val loaded = ImageUtils.loadUriToBitmap(context, uri)
                if (loaded != null) {
                    targetBaseBitmap.value = loaded
                    // Reset positions for custom background
                    faceXOffset.value = 0.5f
                    faceYOffset.value = 0.5f
                    faceScale.value = 0.8f
                    faceRotation.value = 0.0f
                    
                    // Re-render
                    executeFaceSwapRenderer()
                } else {
                    faceSwapUiState.value = HubUiState.Error("Could not load selected background image.")
                }
            } catch (e: Exception) {
                faceSwapUiState.value = HubUiState.Error("Error loading base photo: ${e.message}")
            }
        }
    }

    fun setSourceFace(uri: Uri, context: Context) {
        sourceFaceUri.value = uri
        faceSwapUiState.value = HubUiState.Loading
        viewModelScope.launch {
            try {
                val loaded = ImageUtils.loadUriToBitmap(context, uri)
                if (loaded != null) {
                    sourceFaceBitmap.value = loaded
                    
                    // Automatically load dynamic preset if target background isn't pre-loaded yet
                    if (targetBaseBitmap.value == null && selectedPreset.value != null) {
                        loadTargetTemplateBitmap(selectedPreset.value!!, context)
                    } else {
                        executeFaceSwapRenderer()
                    }
                } else {
                    faceSwapUiState.value = HubUiState.Error("Could not load face photo.")
                }
            } catch (e: Exception) {
                faceSwapUiState.value = HubUiState.Error("Error decoding face photo: ${e.message}")
            }
        }
    }

    private fun loadTargetTemplateBitmap(preset: PresetTemplate, context: Context) {
        faceSwapUiState.value = HubUiState.Loading
        viewModelScope.launch {
            try {
                // Download template image if cached, or let Coil load it and convert.
                // To be robust offline as well, we can cache. But let's fetch using HTTP as it represents actual integration!
                val cacheFile = File(context.cacheDir, "preset_${preset.id}.jpg")
                val path = if (cacheFile.exists()) {
                    cacheFile.absolutePath
                } else {
                    ImageUtils.downloadAndSaveImage(context, preset.imageUrl, "preset_${preset.id}.jpg")
                }

                if (path.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap != null) {
                        targetBaseBitmap.value = bitmap
                        
                        // Align with presets coordinates
                        faceXOffset.value = preset.defaultX
                        faceYOffset.value = preset.defaultY
                        faceScale.value = 1.0f // Normalized relative to preset coordinates
                        
                        executeFaceSwapRenderer()
                        return@launch
                    }
                }
                
                // Fallback: draw beautiful template vector/color placeholders offline
                drawOfflinePlaceholderTemplate(preset)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading target preset bitmap", e)
                drawOfflinePlaceholderTemplate(preset)
            }
        }
    }

    private fun drawOfflinePlaceholderTemplate(preset: PresetTemplate) {
        val placeholder = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(placeholder)
        
        // Draw elegant gradient background
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, 800f, 1000f,
            android.graphics.Color.DKGRAY, android.graphics.Color.BLACK,
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 800f, 1000f, paint)
        
        // Draw stylized shield or portrait circle for knight/astronaut
        val circlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.TRANSPARENT
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(400f, 500f, 300f, circlePaint)
        
        // Text tag
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 42f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText("[Template: ${preset.name}]", 400f, 500f, textPaint)
        canvas.drawText("Waiting for network or photo...", 400f, 560f, textPaint)

        targetBaseBitmap.value = placeholder
        faceXOffset.value = 0.5f
        faceYOffset.value = 0.5f
        faceScale.value = 1.0f
        executeFaceSwapRenderer()
    }

    // Real-time canvas rendering of face swap composites on background thread
    fun executeFaceSwapRenderer() {
        val src = sourceFaceBitmap.value ?: return
        val target = targetBaseBitmap.value ?: return

        faceSwapUiState.value = HubUiState.Loading
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                try {
                    // Normalize preset scale if selected preset is active
                    val actualScale = faceScale.value * (selectedPreset.value?.defaultScale ?: 0.35f)

                    val composite = ImageUtils.blendFaceSwap(
                        sourceFace = src,
                        targetBackground = target,
                        xOffsetPct = faceXOffset.value,
                        yOffsetPct = faceYOffset.value,
                        scale = actualScale,
                        rotation = faceRotation.value,
                        brightness = faceBrightness.value,
                        contrast = faceContrast.value,
                        feather = faceFeather.value,
                        skinColorRed = faceColorR.value,
                        skinColorGreen = faceColorG.value,
                        skinColorBlue = faceColorB.value
                    )
                    
                    swappedBitmapResult.value = composite
                    faceSwapUiState.value = HubUiState.Success("Face swapped successfully!")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed blending composite face", e)
                    faceSwapUiState.value = HubUiState.Error("Blending error: ${e.message}")
                }
            }
        }
    }

    fun saveSwappedFaceToHistory(context: Context) {
        val composite = swappedBitmapResult.value ?: return
        
        faceSwapUiState.value = HubUiState.Loading
        viewModelScope.launch {
            try {
                val uniqueId = UUID.randomUUID().toString().substring(0, 8)
                val fileName = "face_swap_$uniqueId.jpg"
                
                val savedLocalPath = ImageUtils.saveBitmapToInternal(context, composite, fileName)
                
                if (savedLocalPath.isNotEmpty()) {
                    val label = selectedPreset.value?.name ?: "Custom Scene"
                    val creation = Creation(
                        title = "Swap: $label",
                        prompt = "Face Swap blend on $label canvas.",
                        type = CreationType.FACESWAP,
                        fileUri = savedLocalPath,
                        configurationJson = JSONObject().apply {
                            put("x_pct", faceXOffset.value)
                            put("y_pct", faceYOffset.value)
                            put("scale", faceScale.value)
                            put("brightness", faceBrightness.value)
                        }.toString()
                    )
                    
                    repository.insert(creation)
                    faceSwapUiState.value = HubUiState.Success("Composite saved inside history library!")
                    generalNotification.value = "Composite saved inside history library!"
                } else {
                    faceSwapUiState.value = HubUiState.Error("Could not save composited file to local device storage.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving swapped face", e)
                faceSwapUiState.value = HubUiState.Error("Error: ${e.message}")
            }
        }
    }

    // ----------------------------------------------------
    // GENERAL GALLERY ACTIONS
    // ----------------------------------------------------
    fun deleteCreation(creation: Creation) {
        viewModelScope.launch {
            try {
                // Delete physical local file first to clean up device storage!
                if (creation.fileUri.startsWith("/")) {
                    val file = File(creation.fileUri)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                repository.delete(creation)
                generalNotification.value = "Saved creation deleted successfully."
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting creation", e)
                generalNotification.value = "Failed deleting creation: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        AudioSynth.stop() // Clear ambient audio synthesizers
    }
}

// ViewModelFactory definition for clean initialization
class ViewModelFactory(private val repository: CreationRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreativeHubViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CreativeHubViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
