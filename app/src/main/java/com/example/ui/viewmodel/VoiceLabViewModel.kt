package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.VoiceLabEngine
import com.example.data.database.VoiceLabDatabase
import com.example.data.model.VoiceSession
import com.example.data.repository.SessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class VoiceLabViewModel(
    private val repository: SessionRepository,
    private val context: Context
) : ViewModel() {

    private val audioEngine = VoiceLabEngine(context)

    // Reactive StateFlow list of saved VoiceLab pieces from Room database
    val savedSessions: StateFlow<List<VoiceSession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current DAW Engine parameters
    private val _bpm = MutableStateFlow(110)
    val bpm: StateFlow<Int> = _bpm.asStateFlow()

    private val _vocalFx = MutableStateFlow("Raw Vocals") // Raw, Super Reverb, Electro Delay, Cyber Robot, Deep Vocals, Heavy Robot, High Pitch
    val vocalFx: StateFlow<String> = _vocalFx.asStateFlow()

    private val _backingBeat = MutableStateFlow(0) // 0: Hip-hop, 1: Techno, 2: Retro Wave
    val backingBeat: StateFlow<Int> = _backingBeat.asStateFlow()

    private val _metronomeEnabled = MutableStateFlow(false)
    val metronomeEnabled: StateFlow<Boolean> = _metronomeEnabled.asStateFlow()

    private val _vocalVolume = MutableStateFlow(1.0f)
    val vocalVolume: StateFlow<Float> = _vocalVolume.asStateFlow()

    private val _beatsVolume = MutableStateFlow(0.8f)
    val beatsVolume: StateFlow<Float> = _beatsVolume.asStateFlow()

    // Recording states
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlayingVocal = MutableStateFlow(false)
    val isPlayingVocal: StateFlow<Boolean> = _isPlayingVocal.asStateFlow()

    private val _isBeatPlaying = MutableStateFlow(false)
    val isBeatPlaying: StateFlow<Boolean> = _isBeatPlaying.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    // Amplitude sampler for waveform rendering
    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes: StateFlow<List<Float>> = _amplitudes.asStateFlow()

    private var recordTimerJob: Job? = null
    private var amplitudeSamplerJob: Job? = null
    private var playbackTimerJob: Job? = null
    private var currentRecordingFile: File? = null

    // Start a new recording session
    fun startRecording() {
        if (_isRecording.value) return
        
        // Clear past waveforms
        _amplitudes.value = emptyList()
        _durationMs.value = 0L

        // Stop any active playbacks
        if (_isPlayingVocal.value) {
            stopVocalPlayback()
        }

        // Start underlying background tape record
        val recordFile = audioEngine.startRecording()
        if (recordFile != null) {
            currentRecordingFile = recordFile
            _isRecording.value = true

            // Trigger backing loops if enabled simultaneously
            if (_isBeatPlaying.value) {
                audioEngine.startLoopingBeat(_bpm.value, _backingBeat.value, _metronomeEnabled.value, _beatsVolume.value)
            }

            // Launch recording clock and hardware decibel amplitude sampler
            startRecordTimer()
            startAmplitudeSampler()
        }
    }

    // Stop and save recording session
    fun stopRecording(sessionTitle: String? = null) {
        if (!_isRecording.value) return

        _isRecording.value = false
        stopTimers()

        val savedFile = audioEngine.stopRecording()
        if (savedFile != null && savedFile.exists()) {
            val title = sessionTitle ?: "Voicelab Mix #${savedSessions.value.size + 1}"
            saveSessionToDatabase(title, savedFile, _durationMs.value)
        }
    }

    // Discard current recording in progress
    fun discardRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false
        stopTimers()
        val file = audioEngine.stopRecording()
        if (file?.exists() == true) {
            file.delete()
        }
        _amplitudes.value = emptyList()
        _durationMs.value = 0L
    }

    // Toggle Backing Beat track loop
    fun toggleBackingBeat() {
        if (_isBeatPlaying.value) {
            audioEngine.stopLoopingBeat()
            _isBeatPlaying.value = false
        } else {
            // Apply current parameters
            audioEngine.startLoopingBeat(_bpm.value, _backingBeat.value, _metronomeEnabled.value, _beatsVolume.value)
            _isBeatPlaying.value = true
        }
    }

    // Playback saved session vocal track
    fun playVocalTrack() {
        val file = currentRecordingFile
        if (file == null || !file.exists() || _isRecording.value) return

        if (_isPlayingVocal.value) {
            stopVocalPlayback()
        } else {
            _isPlayingVocal.value = true
            
            // Align loop playback volume
            if (_isBeatPlaying.value) {
                audioEngine.startLoopingBeat(_bpm.value, _backingBeat.value, _metronomeEnabled.value, _beatsVolume.value)
            }

            audioEngine.startPlayback(
                file = file,
                vocalFx = _vocalFx.value,
                voiceVolume = _vocalVolume.value
            )
            
            // Re-trigger visual playback timer slider
            startPlaybackTimer()
        }
    }

    fun stopVocalPlayback() {
        audioEngine.stopPlayback()
        _isPlayingVocal.value = false
        playbackTimerJob?.cancel()
    }

    // Track Volume Mixing Controllers
    fun setVocalVolume(vol: Float) {
        _vocalVolume.value = vol
        audioEngine.setVocalsVolume(vol)
    }

    fun setBeatsVolume(vol: Float) {
        _beatsVolume.value = vol
        audioEngine.setLoopVolume(vol)
    }

    // Session variables edits
    fun updateBpm(newBpm: Int) {
        _bpm.value = newBpm
        // Automatically sync & transition live loop
        if (_isBeatPlaying.value) {
            audioEngine.startLoopingBeat(_bpm.value, _backingBeat.value, _metronomeEnabled.value, _beatsVolume.value)
        }
    }

    fun updateVocalFx(fx: String) {
        _vocalFx.value = fx
        // If playing back, reload track params instantly to hear the awesome pitch shifting preset!
        if (_isPlayingVocal.value && currentRecordingFile != null) {
            playVocalTrack() // Toggle and restart with new pitch-bend FX params automatically!
            playVocalTrack()
        }
    }

    fun updateBackingBeat(pattern: Int) {
        _backingBeat.value = pattern
        if (_isBeatPlaying.value) {
            audioEngine.startLoopingBeat(_bpm.value, _backingBeat.value, _metronomeEnabled.value, _beatsVolume.value)
        }
    }

    fun toggleMetronome() {
        _metronomeEnabled.value = !_metronomeEnabled.value
        if (_isBeatPlaying.value) {
            audioEngine.startLoopingBeat(_bpm.value, _backingBeat.value, _metronomeEnabled.value, _beatsVolume.value)
        }
    }

    fun selectHistorySession(session: VoiceSession) {
        stopVocalPlayback()
        if (session.filePath != null) {
            val file = File(session.filePath)
            if (file.exists()) {
                currentRecordingFile = file
                _bpm.value = session.bpm
                _vocalFx.value = session.vocalFx
                _durationMs.value = session.durationMs
                
                // Regenerate appropriate raw waveform visualization values
                val staticWaveform = generateMockStaticWaveform(session.id)
                _amplitudes.value = staticWaveform
                
                // Immediately start playing
                playVocalTrack()
            }
        }
    }

    fun deleteSession(session: VoiceSession) {
        viewModelScope.launch {
            // Delete actual file
            if (session.filePath != null) {
                val file = File(session.filePath)
                if (file.exists()) {
                    file.delete()
                }
            }
            repository.delete(session)
            // If current active is the deleted one, clear active track
            if (currentRecordingFile?.absolutePath == session.filePath) {
                stopVocalPlayback()
                currentRecordingFile = null
                _amplitudes.value = emptyList()
                _durationMs.value = 0L
            }
        }
    }

    private fun generateMockStaticWaveform(seed: Int): List<Float> {
        val random = java.util.Random(seed.toLong())
        return List(40) {
            0.1f + random.nextFloat() * 0.85f
        }
    }

    private fun saveSessionToDatabase(title: String, file: File, duration: Long) {
        viewModelScope.launch {
            val newSession = VoiceSession(
                title = title,
                filePath = file.absolutePath,
                bpm = _bpm.value,
                vocalFx = _vocalFx.value,
                durationMs = duration
            )
            repository.insert(newSession)
        }
    }

    // Timers & samplers management
    private fun startRecordTimer() {
        recordTimerJob?.cancel()
        _durationMs.value = 0L
        recordTimerJob = viewModelScope.launch {
            var elapsed = 0L
            while (_isRecording.value) {
                delay(100)
                elapsed += 100
                _durationMs.value = elapsed
            }
        }
    }

    private fun startPlaybackTimer() {
        playbackTimerJob?.cancel()
        playbackTimerJob = viewModelScope.launch {
            val totalDuration = if (_durationMs.value > 0L) _durationMs.value else 10000L
            var currentPos = 0L
            while (_isPlayingVocal.value && currentPos < totalDuration) {
                delay(100)
                currentPos += 100
                // Just display slider progress if desired — handled gracefully
            }
            if (currentPos >= totalDuration) {
                _isPlayingVocal.value = false
            }
        }
    }

    private fun startAmplitudeSampler() {
        amplitudeSamplerJob?.cancel()
        amplitudeSamplerJob = viewModelScope.launch {
            while (_isRecording.value) {
                val amp = audioEngine.getMaxAmplitude()
                // Convert to a scaled, beautifully-sampled amplitude float
                val normalized = (amp.toFloat() / 32767f).coerceIn(0.04f, 1.0f)
                val currentList = _amplitudes.value.toMutableList()
                if (currentList.size > 50) {
                    currentList.removeAt(0) // Rolling window to keep the screen ultra-clean
                }
                currentList.add(normalized)
                _amplitudes.value = currentList
                delay(75) // Capture sampling interval
            }
        }
    }

    private fun stopTimers() {
        recordTimerJob?.cancel()
        amplitudeSamplerJob?.cancel()
        playbackTimerJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.releaseAll()
    }
}

// VM Factory to maintain strict DI construction constraints
class VoiceLabViewModelFactory(
    private val repository: SessionRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VoiceLabViewModel::class.java)) {
            return VoiceLabViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
