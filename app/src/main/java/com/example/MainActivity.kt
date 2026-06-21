package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.VoiceLabDatabase
import com.example.data.model.VoiceSession
import com.example.data.repository.SessionRepository
import com.example.ui.theme.*
import com.example.ui.viewmodel.VoiceLabViewModel
import com.example.ui.viewmodel.VoiceLabViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Room local database and Repository layers
        val database = VoiceLabDatabase.getDatabase(this)
        val repository = SessionRepository(database.voiceSessionDao())
        
        // Build ViewModel using our custom Standalone construction factory
        val viewModel: VoiceLabViewModel = ViewModelProvider(
            this,
            VoiceLabViewModelFactory(repository, applicationContext)
        )[VoiceLabViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("voicelab_scaffold"),
                    containerColor = ObsidianDark
                ) { innerPadding ->
                    VoiceLabStudioScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceLabStudioScreen(
    viewModel: VoiceLabViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Collect parameters reactively from our View State Flow holding central DAW engine settings
    val bpm by viewModel.bpm.collectAsStateWithLifecycle()
    val vocalFx by viewModel.vocalFx.collectAsStateWithLifecycle()
    val backingBeat by viewModel.backingBeat.collectAsStateWithLifecycle()
    val metronomeEnabled by viewModel.metronomeEnabled.collectAsStateWithLifecycle()
    val vocalVolume by viewModel.vocalVolume.collectAsStateWithLifecycle()
    val beatsVolume by viewModel.beatsVolume.collectAsStateWithLifecycle()

    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isPlayingVocal by viewModel.isPlayingVocal.collectAsStateWithLifecycle()
    val isBeatPlaying by viewModel.isBeatPlaying.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val amplitudes by viewModel.amplitudes.collectAsStateWithLifecycle()
    val savedSessions by viewModel.savedSessions.collectAsStateWithLifecycle()

    // Dialog & UI flows control
    var showSaveDialog by remember { mutableStateOf(false) }
    var inputSessionTitle by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) } // 0: Multitrack Console, 1: Preset Beats, 2: Session List

    // Setup permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        } else {
            Toast.makeText(context, "VoiceLab requires Microphone permission to record studio vocals!", Toast.LENGTH_LONG).show()
        }
    }

    // Save prompt dialog popup
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSaveDialog = false
                viewModel.discardRecording()
            },
            title = {
                Text(
                    text = "Name your VoiceLab creation!",
                    style = TextStyle(fontWeight = FontWeight.Bold, color = OnBackgroundWhite, fontSize = 20.sp)
                )
            },
            text = {
                OutlinedTextField(
                    value = inputSessionTitle,
                    onValueChange = { inputSessionTitle = it },
                    label = { Text("Track Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OnBackgroundWhite,
                        unfocusedTextColor = OnBackgroundWhite,
                        focusedBorderColor = AcousticTeal,
                        unfocusedBorderColor = SoftGrey
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_session_input"),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalTitle = inputSessionTitle.trim().ifEmpty { "Voicelab Mix #${savedSessions.size + 1}" }
                        viewModel.stopRecording(finalTitle)
                        inputSessionTitle = ""
                        showSaveDialog = false
                        Toast.makeText(context, "Track Saved Successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRedOrange),
                    modifier = Modifier.testTag("confirm_save_button")
                ) {
                    Text("Save & Export", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.discardRecording()
                        inputSessionTitle = ""
                        showSaveDialog = false
                    },
                    modifier = Modifier.testTag("discard_save_button")
                ) {
                    Text("Discard Track", color = SoftGrey)
                }
            },
            containerColor = SurfaceCarbon
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header Status Deck
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Custom Glowing DAW Beat Node
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) BrandRedOrange else if (isBeatPlaying) MetricGreen else SoftGrey)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VOICELAB",
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = OnBackgroundWhite,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                    )
                }
                Text(
                    text = "Professional Multitrack Studio",
                    style = TextStyle(color = SoftGrey, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )
            }

            // Real-Time DAW Engine Status Flag
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isRecording) BrandRedOrange.copy(alpha = 0.15f)
                        else if (isPlayingVocal) AcousticTeal.copy(alpha = 0.15f)
                        else SurfaceCarbon
                    )
                    .border(
                        width = 1.dp,
                        color = if (isRecording) BrandRedOrange else if (isPlayingVocal) AcousticTeal else TranslucentOutline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isRecording) "● REC ON" else if (isPlayingVocal) "▶ MIX PLAYing" else "STANDBY",
                    style = TextStyle(
                        color = if (isRecording) BrandRedOrange else if (isPlayingVocal) AcousticTeal else SoftGrey,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }

        // Horizontal Studio Tab bar selection
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color.Transparent,
            contentColor = AcousticTeal,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = AcousticTeal
                )
            },
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("CONSOLE", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.GraphicEq, contentDescription = "Console", modifier = Modifier.size(20.dp)) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("BEATS", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.MusicNote, contentDescription = "Preset Loops", modifier = Modifier.size(20.dp)) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("LIBRARY", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.History, contentDescription = "Record History", modifier = Modifier.size(20.dp)) }
            )
        }

        // Active View Render content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                0 -> {
                    // MAIN DAW MULTITRACK CONSOLE TAB
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Dynamic Wave Visualizer Layer
                        WaveformVisualizer(
                            amplitudes = amplitudes,
                            isRecording = isRecording,
                            isPlaying = isPlayingVocal,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Beat tempo / metronome card
                        DAWControlCard(
                            title = "Tempo & Rhythm Sync",
                            icon = Icons.Default.Settings
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "STRETCH TEMPO: $bpm BPM",
                                        style = TextStyle(
                                            color = OnBackgroundWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                    
                                    // Bouncing Metronome Beat Indicator Lamp
                                    MetronomeLamp(
                                        bpm = bpm,
                                        isActive = isBeatPlaying
                                    )
                                }

                                Slider(
                                    value = bpm.toFloat(),
                                    onValueChange = { viewModel.updateBpm(it.toInt()) },
                                    valueRange = 70f..180f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MetricGreen,
                                        activeTrackColor = MetricGreen,
                                        inactiveTrackColor = DarkGreyOutline
                                    ),
                                    modifier = Modifier.testTag("tempo_bpm_slider")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "INTEGRATE METRONOME CLICK",
                                        style = TextStyle(color = SoftGrey, fontSize = 11.sp)
                                    )
                                    Switch(
                                        checked = metronomeEnabled,
                                        onCheckedChange = { viewModel.toggleMetronome() },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = AcousticTeal,
                                            checkedTrackColor = AcousticTeal.copy(alpha = 0.4f),
                                            uncheckedBorderColor = SoftGrey
                                        ),
                                        modifier = Modifier.testTag("metronome_switch")
                                    )
                                }
                            }
                        }

                        // Studio FX preset vocal rack
                        DAWControlCard(
                            title = "Vocal Vocal FX Presets",
                            icon = Icons.Default.GraphicEq
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "AUTO TUNING & SPACE DELAY PRESETS",
                                    style = TextStyle(color = SoftGrey, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val fxOptions = listOf("Raw Vocals", "Deep Vocals", "Heavy Robot", "High Pitch")
                                    fxOptions.forEach { option ->
                                        val isSelected = vocalFx == option
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) BrandRedOrange else SurfaceCarbon)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Color.Transparent else TranslucentOutline,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { viewModel.updateVocalFx(option) }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = option.replace(" Vocals", ""),
                                                style = TextStyle(
                                                    color = if (isSelected) Color.White else OnBackgroundWhite,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Mixing Console Rails
                        DAWControlCard(
                            title = "Studio Mixing Console",
                            icon = Icons.Default.VolumeUp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                // Vocals fader
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Voice Track", color = OnBackgroundWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text("${(vocalVolume * 100).toInt()}%", color = AcousticTeal, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Slider(
                                        value = vocalVolume,
                                        onValueChange = { viewModel.setVocalVolume(it) },
                                        colors = SliderDefaults.colors(
                                            thumbColor = AcousticTeal,
                                            activeTrackColor = AcousticTeal,
                                            inactiveTrackColor = DarkGreyOutline
                                        ),
                                        modifier = Modifier.testTag("vocal_volume_slider")
                                    )
                                }

                                // Backing loops fader
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Synth Loop", color = OnBackgroundWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text("${(beatsVolume * 100).toInt()}%", color = MetricGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Slider(
                                        value = beatsVolume,
                                        onValueChange = { viewModel.setBeatsVolume(it) },
                                        colors = SliderDefaults.colors(
                                            thumbColor = MetricGreen,
                                            activeTrackColor = MetricGreen,
                                            inactiveTrackColor = DarkGreyOutline
                                        ),
                                        modifier = Modifier.testTag("beats_volume_slider")
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // PRESET BEATS LOOP LIBRARY SELECTION TAB
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "SELECT BACKING BEAT ENGINE",
                            style = TextStyle(
                                color = OnBackgroundWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                        )

                        val loopPresets = listOf(
                            LoopPresetItem("Trap Hip-Hop Beat", "Standard punchy sub-kicks, fast trap hi-hat rolls, and clean clap layers.", 0, NeonViolet),
                            LoopPresetItem("Cyber Synth Techno", "High-intensity four-on-the-floor synth-wave beat with pulsating melodic tones.", 1, BrandRedOrange),
                            LoopPresetItem("Retro Wave Space", "Deep electronic resonant backing basslines for vintage chill vibes.", 2, AcousticTeal)
                        )

                        loopPresets.forEach { preset ->
                            val isSelected = backingBeat == preset.id
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) preset.color.copy(alpha = 0.08f) else SurfaceCarbon)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isSelected) preset.color else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { viewModel.updateBackingBeat(preset.id) }
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(preset.color.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = preset.color,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.name,
                                            style = TextStyle(
                                                color = OnBackgroundWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = preset.desc,
                                            style = TextStyle(
                                                color = SoftGrey,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = preset.color,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Playback engine launcher for background beat
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceCarbon)
                                .border(1.dp, TranslucentOutline, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Preview backing track", color = OnBackgroundWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Select BPM on console tab to speed up loops", color = SoftGrey, fontSize = 10.sp)
                                }

                                Button(
                                    onClick = { viewModel.toggleBackingBeat() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isBeatPlaying) SoftGrey else NeonViolet
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("preview_beat_button")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (isBeatPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(if (isBeatPlaying) "Stop" else "Play Beat")
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // RECORDINGS DATABASE HISTORY LIST TAB
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "VOICELAB EXPORT RELEASES",
                            style = TextStyle(
                                color = OnBackgroundWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (savedSessions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = SoftGrey.copy(alpha = 0.3f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("NO STUDIO CREATIONS SAVED YET", color = SoftGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Enter CONSOLE, press record and drop vocal track!", color = SoftGrey, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .testTag("saved_sessions_list"),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(savedSessions) { session ->
                                    val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(session.timestamp))
                                    val durationStr = String.format("%02d:%02d", (session.durationMs / 60000) % 60, (session.durationMs / 1000) % 60)
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(SurfaceCarbon)
                                            .border(1.dp, TranslucentOutline, RoundedCornerShape(14.dp))
                                            .clickable { viewModel.selectHistorySession(session) }
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(BrandRedOrange.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = "Replay Creation",
                                                    tint = BrandRedOrange,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = session.title,
                                                    style = TextStyle(
                                                        color = OnBackgroundWhite,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        text = "$bpm BPM  |  $vocalFx",
                                                        style = TextStyle(color = SoftGrey, fontSize = 10.sp)
                                                    )
                                                    Text(
                                                        text = "•  $dateStr",
                                                        style = TextStyle(color = SoftGrey, fontSize = 10.sp)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = durationStr,
                                                style = TextStyle(
                                                    color = AcousticTeal,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )

                                            IconButton(
                                                onClick = { viewModel.deleteSession(session) },
                                                modifier = Modifier.testTag("delete_session_button")
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = SoftGrey
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // PERSISTENT MASTER RECORDING HUD (Large glowing console control dock)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live clock displaying seconds/minutes
            val mm = (durationMs / 60000) % 60
            val ss = (durationMs / 1000) % 60
            val ms = (durationMs / 10) % 100
            val clockString = String.format("%02d:%02d:%02d", mm, ss, ms)
            
            Text(
                text = clockString,
                style = TextStyle(
                    color = if (isRecording) BrandRedOrange else OnBackgroundWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary Play backing beat switcher
                IconButton(
                    onClick = { viewModel.toggleBackingBeat() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isBeatPlaying) MetricGreen.copy(alpha = 0.2f) else SurfaceCarbon)
                        .border(1.dp, if (isBeatPlaying) MetricGreen else TranslucentOutline, CircleShape)
                        .testTag("control_beat_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Toggle backing synth beat",
                        tint = if (isBeatPlaying) MetricGreen else OnBackgroundWhite
                    )
                }

                // PRIMARY RECORD HUD ACTION NODE (Fires permissions & save dialog states)
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) BrandRedOrange.copy(alpha = 0.2f) else BrandRedOrange.copy(alpha = 0.1f))
                        .border(
                            width = 2.dp,
                            color = if (isRecording) BrandRedOrange else BrandRedOrange.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .clickable {
                            val recordAudioPermission = android.Manifest.permission.RECORD_AUDIO
                            val hasPermission = ContextCompat.checkSelfPermission(context, recordAudioPermission) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                if (isRecording) {
                                    showSaveDialog = true
                                } else {
                                    viewModel.startRecording()
                                }
                            } else {
                                permissionLauncher.launch(recordAudioPermission)
                            }
                        }
                        .testTag("record_button_hub"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isRecording) 24.dp else 52.dp)
                            .clip(if (isRecording) RoundedCornerShape(4.dp) else CircleShape)
                            .background(BrandRedOrange)
                    )
                }

                // Vocal Pitch Player Toggle
                IconButton(
                    onClick = { viewModel.playVocalTrack() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isPlayingVocal) AcousticTeal.copy(alpha = 0.2f) else SurfaceCarbon)
                        .border(1.dp, if (isPlayingVocal) AcousticTeal else TranslucentOutline, CircleShape)
                        .testTag("control_vocal_toggle")
                ) {
                    Icon(
                        imageVector = if (isPlayingVocal) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "Play vocal tape",
                        tint = if (isPlayingVocal) AcousticTeal else OnBackgroundWhite
                    )
                }
            }
        }
    }
}

// Custom reusable card for spacing & styling M3 design guidelines
@Composable
fun DAWControlCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCarbon),
        border = BorderStroke(1.dp, DarkGreyOutline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = AcousticTeal,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title.uppercase(Locale.getDefault()),
                    style = TextStyle(
                        color = OnBackgroundWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp
                    )
                )
            }
            content()
        }
    }
}

// Bounding Metronome Bouncer
@Composable
fun MetronomeLamp(
    bpm: Int,
    isActive: Boolean
) {
    val duration = (60000 / bpm).toInt()
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by if (isActive) {
        infiniteTransition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration / 2, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    Box(
        modifier = Modifier
            .size(18.dp)
            .border(1.dp, if (isActive) MetricGreen else SoftGrey, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp * scale)
                .clip(CircleShape)
                .background(if (isActive) MetricGreen else SoftGrey.copy(alpha = 0.5f))
        )
    }
}

// Waveform visualizer representation object helper
data class LoopPresetItem(
    val name: String,
    val desc: String,
    val id: Int,
    val color: Color
)

@Composable
fun WaveformVisualizer(
    amplitudes: List<Float>,
    isRecording: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceCarbon)
            .border(1.dp, DarkGreyOutline, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (amplitudes.isEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val idleBars = listOf(0.2f, 0.3f, 0.5f, 0.8f, 0.4f, 0.6f, 0.7f, 0.3f, 0.2f, 0.4f, 0.6f, 0.9f, 0.5f, 0.3f, 0.4f, 0.2f, 0.3f, 0.2f)
                idleBars.forEach { heightFactor ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp * heightFactor)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        BrandRedOrange.copy(alpha = 0.4f),
                                        NeonViolet.copy(alpha = 0.4f)
                                    )
                                )
                            )
                    )
                }
            }
            Text(
                text = "TAP RECORD TO LAY VOCALS",
                style = TextStyle(
                    color = SoftGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                amplitudes.forEach { amp ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp * amp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        if (isRecording) BrandRedOrange else AcousticTeal,
                                        if (isRecording) NeonViolet else MetricGreen
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

