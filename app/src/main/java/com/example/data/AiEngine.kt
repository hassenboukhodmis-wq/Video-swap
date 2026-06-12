package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AiEngine {
    private const val TAG = "AiEngine"
    private const val MODEL_NAME = "gemini-3.5-flash"

    // Preset styles for the Image Generator
    enum class ArtStyle(val displayName: String, val promptSuffix: String) {
        NATURAL("Realistic Photo", "highly detailed, cinematic lighting, 8k resolution, professional photography, photorealistic, depth of field"),
        ANIME("Anime & Manga", "anime art style, vibrant colors, clean lines, beautiful illustration, studio ghibli feeling, highly detailed"),
        CYBERPUNK("Cyberpunk Neon", "neon lighting, sci-fi cyberpunk aesthetics, dark city, glowing signs, rainy night, hyper-detailed, futuristic"),
        RENAISSANCE("Classic Oil Painting", "renaissance oil painting style, fine art masterpiece, warm golden atmosphere, visible brush strokes, rich textures"),
        RENDER_3D("3D Pixar Render", "3d animation style, clay model render, cute proportions, smooth shapes, gorgeous cinematic volumetric lighting")
    }

    // Video generation styles
    enum class VideoTheme(val displayName: String, val musicGenre: String, val promptModifier: String) {
        SCIFI_SPACE("Cosmic Odyssey", "SCI_FI_DRONE", "futuristic space journey, stars flowing past, smooth camera motion, wide angle lens, slow cinematic speed"),
        CYBER_PULSE("Neon Overdrive", "CYBER_ELECTRONIC", "neon illuminated metropolis, speeding car trails, fast electric pace, high contrast cyber aesthetic"),
        FANTASY_REALM("Mystic Enchantment", "FANTASY_AMBIENT", "magical glowing forest, floating fairy dust, slow mystical transition, serene ancient fantasy environment"),
        DRAMATIC_MONUMENTS("Epic Heritage", "ORCHESTRAL_MELODY", "sweeping aerial drone shot of historical monument, dramatic clouds, gold hour volumetric lighting, cinematic scale")
    }

    // Direct image generator using Pollinations.ai (reliable, high-fidelity, and 100% free with no limit)
    fun buildImageUrl(prompt: String, style: ArtStyle, seed: Long = System.currentTimeMillis()): String {
        val styledPrompt = "$prompt, ${style.promptSuffix}"
        val encodedPrompt = URLEncoder.encode(styledPrompt, "UTF-8")
        return "https://image.pollinations.ai/p/$encodedPrompt?width=600&height=600&seed=$seed&nologo=true&private=true"
    }

    // Call the real Gemini API to expand a prompt using HttpURLConnection for 100% stability
    suspend fun expandPromptWithGemini(userPrompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured. Returning original prompt.")
            return@withContext userPrompt
        }

        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val systemInstruction = "You are an expert AI Art Director. Your task is to expand the given simple user prompt into a short, single-paragraph, highly descriptive, detailed prompt for an image generator. Add lighting, composition, mood, and style details but keep it unified and under 60 words. Avoid meta-commentary, just return the expanded prompt text directly."
            
            // Build JSON Request Body according to Google Direct REST spec
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "System: $systemInstruction\nUser Prompt: $userPrompt")
                            })
                        })
                    })
                })
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val responseJson = JSONObject(responseText)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val parts = candidates.getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                    if (parts.length() > 0) {
                        val textResult = parts.getJSONObject(0).getString("text").trim()
                        if (textResult.isNotEmpty()) {
                            return@withContext textResult
                        }
                    }
                }
            } else {
                Log.e(TAG, "Gemini API failed with response code: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini expandPrompt: ${e.message}", e)
        }
        return@withContext userPrompt
    }

    // Call Gemini to generate a 4-scene video storyboard script
    suspend fun generateVideoStoryboardWithGemini(videoConcept: String): List<StoryboardScene> = withContext(Dispatchers.IO) {
        val defaultList = listOf(
            StoryboardScene(1, "Deep space exploration ship starting engines", "Ignition sequence initiated..."),
            StoryboardScene(2, "Ship accelerating through spectacular glittering rings of a sapphire giant planet", "Navigating planetary rings..."),
            StoryboardScene(3, "Approaching an active golden stellar singularity", "Approaching stellar singularity event horizon..."),
            StoryboardScene(4, "Warp speed jump into a vibrant new galaxy of cyan and pink stars", "Jump complete. Unknown quadrant reached.")
        )

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured for video generator. Returning default storyboard.")
            return@withContext defaultList
        }

        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.doOutput = true

            val systemInstruction = "You are a professional movie director. The user wants to create a short cinematic video. Create exactly 4 visual scenes chronologically. Return a JSON array where each element contains 'sceneNumber' (int), 'scenePrompt' (string describing the frame visual detail), and 'subtitle' (string, voiceover/subtitle for that shot). Do not output markdown backticks, return only the naked valid JSON array."
            
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "System: $systemInstruction\nVideo Topic: $videoConcept")
                            })
                        })
                    })
                })
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                var responseText = conn.inputStream.bufferedReader().use { it.readText() }
                
                // Strip possible markdown ticks
                if (responseText.contains("```json")) {
                    responseText = responseText.substringAfter("```json").substringBefore("```")
                } else if (responseText.contains("```")) {
                    responseText = responseText.substringAfter("```").substringBefore("```")
                }
                responseText = responseText.trim()

                val jsonArray = JSONArray(responseText)
                val storyboard = mutableListOf<StoryboardScene>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    storyboard.add(
                        StoryboardScene(
                            sceneNumber = item.optInt("sceneNumber", i + 1),
                            scenePrompt = item.optString("scenePrompt", videoConcept),
                            subtitle = item.optString("subtitle", "")
                        )
                    )
                }
                if (storyboard.isNotEmpty()) {
                    return@withContext storyboard
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating video storyboard with Gemini: ${e.message}", e)
        }
        return@withContext defaultList
    }
}

// Data model representing a script storyboard frame/scene
data class StoryboardScene(
    val sceneNumber: Int,
    val scenePrompt: String,
    val subtitle: String
)
