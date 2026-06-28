package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_channels")
data class MonitoredChannel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val handle: String,
    val status: String = "MONITORING", // "MONITORING", "IDLE", "PAUSED"
    val lastChecked: Long = 0,
    val lastStreamDetected: Long = 0,
    val avatarUrl: String = ""
)

@Entity(tableName = "recording_items")
data class RecordingItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val channelId: Int = -1, // -1 if a direct URL download
    val channelName: String,
    val streamTitle: String,
    val streamUrl: String,
    val status: String, // "RECORDING", "COMPLETED", "CANCELLED", "PAUSED", "MISSED"
    val fileSize: String = "0 KB",
    val durationSeconds: Long = 0,
    val progress: Float = 0.0f,
    val filePath: String = "",
    val playerClient: String = "android_vr", // fallback order: android_vr, tv, web, mweb, ios, tv_embedded, ffmpeg
    val timestamp: Long = System.currentTimeMillis(),
    val urlType: String = "LIVE" // "LIVE" or "COMPLETED"
)

@Entity(tableName = "system_logs")
data class SystemLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // "INFO", "WARN", "ERROR"
    val message: String
)
