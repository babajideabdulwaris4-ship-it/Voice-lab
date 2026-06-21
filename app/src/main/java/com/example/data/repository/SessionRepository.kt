package com.example.data.repository

import com.example.data.database.VoiceSessionDao
import com.example.data.model.VoiceSession
import kotlinx.coroutines.flow.Flow

class SessionRepository(private val voiceSessionDao: VoiceSessionDao) {
    val allSessions: Flow<List<VoiceSession>> = voiceSessionDao.getAllSessions()

    suspend fun insert(session: VoiceSession) {
        voiceSessionDao.insertSession(session)
    }

    suspend fun delete(session: VoiceSession) {
        voiceSessionDao.deleteSession(session)
    }

    suspend fun deleteById(id: Int) {
        voiceSessionDao.deleteSessionById(id)
    }
}
