package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class VoiceLabEngine(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var loopAudioTrack: AudioTrack? = null
    
    var isRecording = false
        private set
    var isPlaying = false
        private set
    var isLoopingBeatPlaying = false
        private set

    var currentRecordingFile: File? = null
        private set

    // Recording amplitude tracking
    fun getMaxAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }

    // Start recording user's vocals
    fun startRecording(): File? {
        if (isRecording) return null

        val outputFile = File(context.filesDir, "voicelab_rec_${System.currentTimeMillis()}.mp4")
        currentRecordingFile = outputFile

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFile.absolutePath)
            try {
                prepare()
                start()
                isRecording = true
            } catch (e: Exception) {
                Log.e("VoiceLabEngine", "Failed to start recording", e)
                isRecording = false
                return null
            }
        }
        return outputFile
    }

    // Stop recording vocals
    fun stopRecording(): File? {
        if (!isRecording) return null
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceLabEngine", "Error stopping recording", e)
        } finally {
            mediaRecorder = null
            isRecording = false
        }
        return currentRecordingFile
    }

    // Synthesize a drum loop pattern over PCM dynamically in-memory based on selected BPM and sequencer pattern
    fun generateDrumLoop(bpm: Int, patternType: Int, useMetronome: Boolean): ShortArray {
        val sampleRate = 22050 // Lower rate for space efficiency and instantaneous DSP synthesis
        val secondsPerBeat = 60.0 / bpm
        val samplesPerBeat = (sampleRate * secondsPerBeat).toInt()
        val totalSamples = samplesPerBeat * 4 // A standard 4-beat looping measure
        val buffer = ShortArray(totalSamples)

        // Kick drum synthesizer: Sliding sine wave
        fun addKick(startOffset: Int) {
            val duration = (sampleRate * 0.16).toInt() // 160ms long
            for (i in 0 until duration) {
                val tIndex = startOffset + i
                if (tIndex >= totalSamples) break
                val t = i.toDouble() / sampleRate
                val freq = 130.0 - (90.0 * (i.toDouble() / duration))
                val env = 15000.0 * Math.exp(-6.5 * i.toDouble() / duration)
                val sampleVal = (Math.sin(2.0 * Math.PI * freq * t) * env).toInt()
                buffer[tIndex] = (buffer[tIndex] + sampleVal).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        // Snare drum synthesizer: High-density exponential decaying white noise
        fun addSnare(startOffset: Int) {
            val duration = (sampleRate * 0.14).toInt() // 140ms
            for (i in 0 until duration) {
                val tIndex = startOffset + i
                if (tIndex >= totalSamples) break
                val rand = Math.random() * 2.0 - 1.0
                val env = 9000.0 * Math.exp(-9.0 * i.toDouble() / duration)
                val sampleVal = (rand * env).toInt()
                buffer[tIndex] = (buffer[tIndex] + sampleVal).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        // Hi-Hat synthesizer: Extremely decaying noise blast
        fun addHiHat(startOffset: Int) {
            val duration = (sampleRate * 0.04).toInt() // 40ms click
            for (i in 0 until duration) {
                val tIndex = startOffset + i
                if (tIndex >= totalSamples) break
                val rand = Math.random() * 2.0 - 1.0
                val env = 6000.0 * Math.exp(-18.0 * i.toDouble() / duration)
                val sampleVal = (rand * env).toInt()
                buffer[tIndex] = (buffer[tIndex] + sampleVal).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        // Metronome Click synthesizer (High wooden tone)
        fun addMetronomeClick(startOffset: Int) {
            val duration = (sampleRate * 0.05).toInt()
            for (i in 0 until duration) {
                val tIndex = startOffset + i
                if (tIndex >= totalSamples) break
                val t = i.toDouble() / sampleRate
                val env = 12000.0 * Math.exp(-15.0 * i.toDouble() / duration)
                val sampleVal = (Math.sin(2.0 * Math.PI * 1100.0 * t) * env).toInt()
                buffer[tIndex] = (buffer[tIndex] + sampleVal).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        // Synthesizer Lead Melody note (Square/Triangle wave)
        fun addSynthNote(startOffset: Int, freq: Double, durationSec: Double = 0.22) {
            val duration = (sampleRate * durationSec).toInt()
            for (i in 0 until duration) {
                val tIndex = startOffset + i
                if (tIndex >= totalSamples) break
                val t = i.toDouble() / sampleRate
                
                // Pure synth square wave generator
                val squareValue = if (Math.sin(2.0 * Math.PI * freq * t) >= 0.0) 1.0 else -1.0
                val env = 4500.0 * Math.exp(-4.5 * i.toDouble() / duration)
                val sampleVal = (squareValue * env).toInt()
                buffer[tIndex] = (buffer[tIndex] + sampleVal).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        // Inject Metronome clicks on the 4 beats if enabled
        if (useMetronome) {
            addMetronomeClick(0)
            addMetronomeClick(samplesPerBeat)
            addMetronomeClick(samplesPerBeat * 2)
            addMetronomeClick(samplesPerBeat * 3)
        }

        // Build drum pattern tracks
        when (patternType) {
            0 -> {
                // Style 1: Trap/Hip-hop Beat
                // Beat 1: Kick & HiHat
                addKick(0)
                addHiHat(0)
                addHiHat((samplesPerBeat * 0.5).toInt())

                // Beat 2: Snare & HiHat
                addSnare(samplesPerBeat)
                addHiHat(samplesPerBeat)
                addHiHat((samplesPerBeat * 1.5).toInt())

                // Beat 3: Double Kick & HiHat
                addKick(samplesPerBeat * 2)
                addKick((samplesPerBeat * 2.3).toInt())
                addHiHat(samplesPerBeat * 2)
                addHiHat((samplesPerBeat * 2.5).toInt())

                // Beat 4: Snare & Double HiHat
                addSnare(samplesPerBeat * 3)
                addHiHat(samplesPerBeat * 3)
                addHiHat((samplesPerBeat * 3.3).toInt())
                addHiHat((samplesPerBeat * 3.65).toInt())
            }
            1 -> {
                // Style 2: Cyber Techno Beat (4-on-the-Floor with synth lead!)
                // Beat 1: Heavy Kick
                addKick(0)
                addHiHat((samplesPerBeat * 0.5).toInt())
                addSynthNote(0, 196.00) // G3 Note

                // Beat 2: Kick & Snare
                addKick(samplesPerBeat)
                addSnare(samplesPerBeat)
                addHiHat((samplesPerBeat * 1.5).toInt())
                addSynthNote(samplesPerBeat, 233.08) // Bb3 Note

                // Beat 3: Kick
                addKick(samplesPerBeat * 2)
                addHiHat((samplesPerBeat * 2.5).toInt())
                addSynthNote(samplesPerBeat * 2, 293.66) // D4 Note

                // Beat 4: Kick & Snare
                addKick(samplesPerBeat * 3)
                addSnare(samplesPerBeat * 3)
                addHiHat((samplesPerBeat * 3.5).toInt())
                addSynthNote(samplesPerBeat * 3, 349.23) // F4 Note
            }
            2 -> {
                // Style 3: Retro Wave Synth Pulse
                addKick(0)
                addHiHat(0)
                addSynthNote(0, 110.00, 0.4) // A2 root
                addSynthNote((samplesPerBeat * 0.5).toInt(), 220.00, 0.2) // A3 oct
                
                addKick(samplesPerBeat)
                addSnare(samplesPerBeat)
                addSynthNote(samplesPerBeat, 130.81, 0.4) // C3 root
                addSynthNote((samplesPerBeat * 1.5).toInt(), 261.63, 0.2) // C4 oct

                addKick(samplesPerBeat * 2)
                addHiHat(samplesPerBeat * 2)
                addSynthNote(samplesPerBeat * 2, 146.83, 0.4) // D3 root
                addSynthNote((samplesPerBeat * 2.5).toInt(), 293.66, 0.2) // D4 oct

                addKick(samplesPerBeat * 3)
                addSnare(samplesPerBeat * 3)
                addSynthNote(samplesPerBeat * 3, 164.81, 0.4) // E3 root
                addSynthNote((samplesPerBeat * 3.5).toInt(), 329.63, 0.2) // E4 oct
            }
        }

        return buffer
    }

    // Play loop track in synchronized infinite hardware state
    fun startLoopingBeat(bpm: Int, patternType: Int, useMetronome: Boolean, volumeMultiplier: Float = 0.8f) {
        stopLoopingBeat()

        val drumTrack = generateDrumLoop(bpm, patternType, useMetronome)
        
        try {
            loopAudioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                22050,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                drumTrack.size * 2, // 2 bytes per 16-bit PCM short
                AudioTrack.MODE_STATIC
            ).apply {
                write(drumTrack, 0, drumTrack.size)
                setLoopPoints(0, drumTrack.size, -1) // -1 signifies infinite seamless loops
                
                // Adjust static loop volume
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setVolume(volumeMultiplier)
                } else {
                    @Suppress("DEPRECATION")
                    setStereoVolume(volumeMultiplier, volumeMultiplier)
                }
                
                play()
            }
            isLoopingBeatPlaying = true
        } catch (e: Exception) {
            Log.e("VoiceLabEngine", "Failed to start AudioTrack static loop", e)
            isLoopingBeatPlaying = false
        }
    }

    // Stop loops immediately
    fun stopLoopingBeat() {
        try {
            loopAudioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceLabEngine", "Error stopping loop audio track", e)
        } finally {
            loopAudioTrack = null
            isLoopingBeatPlaying = false
        }
    }

    // Adjust playback volume of the loop beat in real time
    fun setLoopVolume(volume: Float) {
        try {
            loopAudioTrack?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setVolume(volume)
                } else {
                    @Suppress("DEPRECATION")
                    setStereoVolume(volume, volume)
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceLabEngine", "Failed to set loop volume", e)
        }
    }

    // Vocal Tracks Playback Controls with hardware-level digital pitching and time stretching!
    fun startPlayback(file: File, pitch: Float = 1.0f, speed: Float = 1.0f, vocalFx: String = "Raw", voiceVolume: Float = 1.0f) {
        stopPlayback()

        if (!file.exists()) return

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                setVolume(voiceVolume, voiceVolume)
                prepare()
                
                // Configure real-time sound pitching & playback stretching parameters!
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val params = playbackParams
                    
                    // Assign pitch modifiers based on Vocal FX preset selection
                    val adjustedPitch = when (vocalFx) {
                        "Deep Vocals" -> 0.70f
                        "Heavy Robot" -> 0.60f
                        "High Pitch" -> 1.35f
                        "Hyper Tune" -> 1.25f
                        else -> pitch // "Raw" or general default
                    }
                    val adjustedSpeed = when (vocalFx) {
                        "Deep Vocals" -> 0.90f
                        "Heavy Robot" -> 0.85f
                        else -> speed
                    }

                    params.pitch = adjustedPitch
                    params.speed = adjustedSpeed
                    playbackParams = params
                }

                setOnCompletionListener {
                    this@VoiceLabEngine.isPlaying = false
                    stopPlayback()
                }
                
                start()
                this@VoiceLabEngine.isPlaying = true
            } catch (e: IOException) {
                Log.e("VoiceLabEngine", "Failed to play back recording", e)
            }
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceLabEngine", "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
            isPlaying = false
        }
    }

    fun setVocalsVolume(volume: Float) {
        try {
            mediaPlayer?.setVolume(volume, volume)
        } catch (e: Exception) {
            Log.e("VoiceLabEngine", "Failed to update vocal volume", e)
        }
    }

    fun releaseAll() {
        stopRecording()
        stopPlayback()
        stopLoopingBeat()
    }
}
