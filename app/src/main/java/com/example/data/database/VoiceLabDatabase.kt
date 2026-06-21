package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.VoiceSession

@Database(entities = [VoiceSession::class], version = 1, exportSchema = false)
abstract class VoiceLabDatabase : RoomDatabase() {
    abstract fun voiceSessionDao(): VoiceSessionDao

    companion object {
        @Volatile
        private var INSTANCE: VoiceLabDatabase? = null

        fun getDatabase(context: Context): VoiceLabDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceLabDatabase::class.java,
                    "voicelab_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
