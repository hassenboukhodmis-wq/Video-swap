package com.example.utils

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.example.data.AiEngine.VideoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AudioSynth {
    private const val TAG = "AudioSynth"
    private const val SAMPLE_RATE = 22050
    
    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Synchronized
    fun start(theme: VideoTheme) {
        stop() // Ensure previous synthesis is cleared

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBufferSize, 4096),
                AudioTrack.MODE_STREAM
            )
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}", e)
            return
        }

        synthJob = scope.launch {
            val freq1: Double
            val freq2: Double
            val modulatorSpeed: Double
            
            when (theme) {
                VideoTheme.SCIFI_SPACE -> {
                    freq1 = 80.0    // Deep rumble
                    freq2 = 120.0   // High ambient shimmer
                    modulatorSpeed = 0.5 // Slow breathing
                }
                VideoTheme.CYBER_PULSE -> {
                    freq1 = 90.0    // Fast rhythm sub-bass
                    freq2 = 180.0   // Pulse drive
                    modulatorSpeed = 3.0 // Fast
                }
                VideoTheme.FANTASY_REALM -> {
                    freq1 = 150.0   // Gentle hum
                    freq2 = 300.0   // High ethereal ring
                    modulatorSpeed = 0.8 // Whispering
                }
                VideoTheme.DRAMATIC_MONUMENTS -> {
                    freq1 = 110.0   // Orchestral pad
                    freq2 = 220.0   // Bold melody
                    modulatorSpeed = 1.2 // Dynamic
                }
            }

            val bufferSize = 2048
            val buffer = ShortArray(bufferSize)
            var phase = 0.0
            var modPhase = 0.0

            while (isActive) {
                for (i in 0 until bufferSize) {
                    // Slowly modulate frequency over time
                    val modulation = Math.sin(modPhase) * 15.0
                    val currentFreq = freq1 + modulation
                    
                    // Modulate volume slightly to create a breathing/swelling effect
                    val lfoVol = 0.6 + 0.4 * Math.sin(modPhase * 0.7)
                    
                    // Generate fundamental sine wave
                    var sampleVal = Math.sin(phase)
                    
                    // Add secondary harmonics for richer texture
                    sampleVal += 0.4 * Math.sin(phase * 2.0)
                    if (theme == VideoTheme.CYBER_PULSE) {
                        // Cyber pulse gets a gritty sawtooth blend
                        sampleVal += 0.25 * ((phase % (2.0 * Math.PI)) / Math.PI - 1.0)
                    } else {
                        sampleVal += 0.2 * Math.sin(phase * 1.5)
                    }

                    // Normalize and scale to Short.MAX_VALUE with safety volume
                    val volScale = 0.25 * lfoVol
                    val sampleShort = (sampleVal * volScale * Short.MAX_VALUE).toInt().coerceIn(
                        Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()
                    )
                    buffer[i] = sampleShort.toShort()

                    // Advance phases
                    val angleIncrement = (2.0 * Math.PI * currentFreq) / SAMPLE_RATE
                    phase += angleIncrement
                    if (phase > 2.0 * Math.PI * 1000.0) { // Safety reset to avoid overflow
                        phase -= 2.0 * Math.PI * 1000.0
                    }

                    val modIncrement = (2.0 * Math.PI * modulatorSpeed) / SAMPLE_RATE
                    modPhase += modIncrement
                    if (modPhase > 2.0 * Math.PI * 1000.0) {
                        modPhase -= 2.0 * Math.PI * 1000.0
                    }
                }

                // Write short chunk streams to track
                val track = audioTrack
                if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.write(buffer, 0, bufferSize)
                } else {
                    break
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        try {
            synthJob?.cancel()
            synthJob = null
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up AudioSynth resources: ${e.message}", e)
        }
    }
}
