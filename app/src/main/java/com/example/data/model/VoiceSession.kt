package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "voice_sessions")
data class VoiceSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val filePath: String?,
    val bpm: Int,
    val vocalFx: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L
) : Serializable
