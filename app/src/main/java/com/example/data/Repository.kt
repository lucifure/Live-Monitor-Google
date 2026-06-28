package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class LiveMonitorRepository(private val db: AppDatabase) {
    val channelDao = db.channelDao()
    val recordingDao = db.recordingDao()
    val logDao = db.logDao()

    // Channels
    val allChannels: Flow<List<MonitoredChannel>> = channelDao.getAllChannels()

    suspend fun getChannelById(id: Int): MonitoredChannel? {
        return channelDao.getChannelById(id)
    }

    suspend fun insertChannel(channel: MonitoredChannel): Long {
        logInfo("Added monitored channel: ${channel.name} (${channel.handle})")
        return channelDao.insertChannel(channel)
    }

    suspend fun updateChannel(channel: MonitoredChannel) {
        channelDao.updateChannel(channel)
    }

    suspend fun deleteChannelById(id: Int) {
        val channel = getChannelById(id)
        if (channel != null) {
            logWarn("Removed monitored channel: ${channel.name}")
        }
        channelDao.deleteChannelById(id)
    }

    // Recordings
    val allRecordings: Flow<List<RecordingItem>> = recordingDao.getAllRecordings()
    val activeRecordings: Flow<List<RecordingItem>> = recordingDao.getActiveRecordingsFlow()

    suspend fun getRecordingById(id: Int): RecordingItem? {
        return recordingDao.getRecordingById(id)
    }

    suspend fun insertRecording(recording: RecordingItem): Long {
        val id = recordingDao.insertRecording(recording)
        logInfo("Started recording: '${recording.streamTitle}' [Client fallback: ${recording.playerClient}]")
        return id
    }

    suspend fun updateRecording(recording: RecordingItem) {
        recordingDao.updateRecording(recording)
    }

    suspend fun deleteRecordingById(id: Int) {
        recordingDao.deleteRecordingById(id)
    }

    // System Logs
    val allLogs: Flow<List<SystemLog>> = logDao.getAllLogs()

    suspend fun logInfo(message: String) {
        logDao.insertLog(SystemLog(level = "INFO", message = message))
    }

    suspend fun logWarn(message: String) {
        logDao.insertLog(SystemLog(level = "WARN", message = message))
    }

    suspend fun logError(message: String) {
        logDao.insertLog(SystemLog(level = "ERROR", message = message))
    }

    suspend fun clearLogs() {
        logDao.clearLogs()
    }
}
