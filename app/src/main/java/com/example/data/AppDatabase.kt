package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM monitored_channels ORDER BY name ASC")
    fun getAllChannels(): Flow<List<MonitoredChannel>>

    @Query("SELECT * FROM monitored_channels WHERE id = :id")
    suspend fun getChannelById(id: Int): MonitoredChannel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: MonitoredChannel): Long

    @Update
    suspend fun updateChannel(channel: MonitoredChannel)

    @Delete
    suspend fun deleteChannel(channel: MonitoredChannel)

    @Query("DELETE FROM monitored_channels WHERE id = :id")
    suspend fun deleteChannelById(id: Int)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recording_items ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<RecordingItem>>

    @Query("SELECT * FROM recording_items WHERE status = 'RECORDING' ORDER BY timestamp DESC")
    fun getActiveRecordingsFlow(): Flow<List<RecordingItem>>

    @Query("SELECT * FROM recording_items WHERE id = :id")
    suspend fun getRecordingById(id: Int): RecordingItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingItem): Long

    @Update
    suspend fun updateRecording(recording: RecordingItem)

    @Delete
    suspend fun deleteRecording(recording: RecordingItem)

    @Query("DELETE FROM recording_items WHERE id = :id")
    suspend fun deleteRecordingById(id: Int)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM system_logs ORDER BY timestamp DESC LIMIT 500")
    fun getAllLogs(): Flow<List<SystemLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SystemLog)

    @Query("DELETE FROM system_logs")
    suspend fun clearLogs()
}

@Database(
    entities = [MonitoredChannel::class, RecordingItem::class, SystemLog::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun recordingDao(): RecordingDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "live_monitor_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
