package com.example.data.database

import androidx.room.*
import com.example.data.model.VoiceSession
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceSessionDao {
    @Query("SELECT * FROM voice_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<VoiceSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: VoiceSession)

    @Delete
    suspend fun deleteSession(session: VoiceSession)

    @Query("DELETE FROM voice_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Int)
}
