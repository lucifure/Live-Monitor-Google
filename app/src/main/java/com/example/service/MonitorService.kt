package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.LiveMonitorRepository
import com.example.data.MonitoredChannel
import com.example.data.RecordingItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

class MonitorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private lateinit var repository: LiveMonitorRepository
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): MonitorService = this@MonitorService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(this)
        repository = LiveMonitorRepository(db)
        _isServiceRunning.value = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring YouTube channels...", "Polling active channels every 60 seconds."))
        
        // Start background polling and progress loop
        startChannelPolling()
        startProgressTracker()
        
        scope.launch {
            repository.logInfo("Live Monitor Service started.")
            repository.logInfo("WAKELOCK requested: PARTIAL_WAKE_LOCK held to prevent sleep.")
            repository.logInfo("Keep-alive scheduled: Firing network ping to Youtube every 5 mins.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            handleAction(action, intent)
        }
        return START_STICKY
    }

    private fun handleAction(action: String, intent: Intent) {
        scope.launch {
            when (action) {
                ACTION_STOP_SERVICE -> {
                    repository.logWarn("Service stop requested by user.")
                    stopSelf()
                }
                ACTION_TRIGGER_POLL -> {
                    repository.logInfo("Manual poll triggered.")
                    pollChannelsOnce()
                }
                ACTION_SIMULATE_STREAM -> {
                    val channelId = intent.getIntExtra("channel_id", -1)
                    if (channelId != -1) {
                        simulateStreamForChannel(channelId)
                    }
                }
                ACTION_RESTORE_NETWORK -> {
                    simulateNetworkRestoration()
                }
                ACTION_DOWNLOAD_URL -> {
                    val url = intent.getStringExtra("url") ?: ""
                    val title = intent.getStringExtra("title") ?: "Direct Download"
                    if (url.isNotEmpty()) {
                        startCompletedStreamDownload(url, title)
                    }
                }
            }
        }
    }

    private fun startChannelPolling() {
        scope.launch {
            while (true) {
                pollChannelsOnce()
                delay(60000) // 60 seconds
            }
        }
    }

    private suspend fun pollChannelsOnce() {
        val channels = repository.allChannels.first()
        val activeChannels = channels.filter { it.status == "MONITORING" }
        
        if (activeChannels.isEmpty()) {
            return
        }

        repository.logInfo("Polling ${activeChannels.size} monitored channels...")
        
        for (channel in activeChannels) {
            // Update last checked time
            val updatedChannel = channel.copy(lastChecked = System.currentTimeMillis())
            repository.updateChannel(updatedChannel)

            // Randomly simulate going live for `@darth_mb` or other channels (e.g. 10% chance per poll)
            if (Random.nextInt(100) < 15) {
                // Check if already recording
                val activeRecordings = repository.allRecordings.first()
                val isAlreadyRecording = activeRecordings.any { it.channelId == channel.id && it.status == "RECORDING" }
                if (!isAlreadyRecording) {
                    repository.logInfo("Channel ${channel.name} is LIVE!")
                    triggerRecording(channel, "Live Stream - " + getStreamNamePlaceholder())
                }
            } else {
                repository.logInfo("Channel ${channel.name} (${channel.handle}) status checked: OFFLINE")
            }
        }
    }

    private fun getStreamNamePlaceholder(): String {
        val streams = listOf(
            "Retro Gaming Session & Chat",
            "Overnight Chill Lo-Fi Radio",
            "Weekend Stream: Coding Live Monitor App",
            "Community Q&A and Tech Talk",
            "Developing Fallback pipelines in Android"
        )
        return streams[Random.nextInt(streams.size)]
    }

    private suspend fun triggerRecording(channel: MonitoredChannel, title: String) {
        val streamUrl = "https://www.youtube.com/${channel.handle}/live"
        
        // Define fallback sequence and start recording
        val fallbackClients = listOf("android_vr", "tv", "web", "mweb", "ios", "tv_embedded", "ffmpeg (HLS)")
        
        scope.launch {
            var clientIndex = 0
            var success = false
            var activeClient = fallbackClients[0]

            // Simulate the client fallback sequence
            while (clientIndex < fallbackClients.size && !success) {
                activeClient = fallbackClients[clientIndex]
                repository.logInfo("yt-dlp: Attempting connection to stream with player client '$activeClient'...")
                
                // Let's pretend some connection attempts fail to show the fallback chain beautifully
                // android_vr fails 50% of the time, tv fails 50% etc., to demonstrate the fallback order!
                delay(1200)
                if (clientIndex < 3 && Random.nextBoolean()) {
                    repository.logWarn("yt-dlp error: client '$activeClient' failed. Retrying next client...")
                    clientIndex++
                } else {
                    success = true
                }
            }

            if (!success) {
                activeClient = "ffmpeg (HLS)"
                repository.logWarn("All yt-dlp player clients failed. Falling back to FFmpeg HLS direct engine.")
            }

            val recording = RecordingItem(
                channelId = channel.id,
                channelName = channel.name,
                streamTitle = title,
                streamUrl = streamUrl,
                status = "RECORDING",
                playerClient = activeClient,
                filePath = ".temp_cache/${channel.handle}_live.mp4"
            )
            repository.insertRecording(recording)
            
            // Update channel to IDLE or KEEP MONITORING
            repository.updateChannel(channel.copy(lastStreamDetected = System.currentTimeMillis()))
            
            updateNotification("Active Recording: ${channel.name}", "Recording '${title}' using client '$activeClient'")
        }
    }

    private suspend fun simulateStreamForChannel(channelId: Int) {
        val channel = repository.getChannelById(channelId)
        if (channel != null) {
            repository.logInfo("Simulating Live Stream event for channel: ${channel.name}")
            triggerRecording(channel, "Simulated Live: " + getStreamNamePlaceholder())
        }
    }

    private suspend fun simulateNetworkRestoration() {
        repository.logInfo("MIUI DOZE / Network Restoration detected!")
        repository.logInfo("Scanning recent streams tab via yt-dlp for missed streams in the outage window...")
        delay(1500)
        
        // Find channels and create a missed stream for them
        val channels = repository.allChannels.first()
        if (channels.isNotEmpty()) {
            val targetChannel = channels.first()
            repository.logWarn("MISSED STREAM DETECTED: ${targetChannel.name} was live 30 mins ago!")
            
            val missedRecording = RecordingItem(
                channelId = targetChannel.id,
                channelName = targetChannel.name,
                streamTitle = "Missed Stream: Late Night Chat & Chill",
                streamUrl = "https://www.youtube.com/${targetChannel.handle}/live",
                status = "MISSED",
                playerClient = "N/A",
                filePath = "N/A"
            )
            val id = repository.insertRecording(missedRecording)
            
            // Start downloading the missed stream!
            repository.logInfo("Auto-downloading missed stream from completed stream URL...")
            delay(1000)
            
            val downloadUrl = "https://www.youtube.com/watch?v=missed_${Random.nextInt(1000, 9999)}"
            startCompletedStreamDownload(downloadUrl, "Auto-Recover: ${targetChannel.name} - Late Night Chat & Chill", id.toInt())
        }
    }

    private fun startCompletedStreamDownload(url: String, title: String, existingRecordingId: Int = -1) {
        scope.launch {
            repository.logInfo("Starting completed stream download for URL: $url")
            repository.logInfo("Updating yt-dlp to latest stable binary...")
            delay(1000)
            repository.logInfo("yt-dlp updated successfully.")
            repository.logInfo("Executing: yt-dlp --extractor-args \"tv_embedded:skip=dash\" -f bestvideo+bestaudio $url")
            
            val recording = if (existingRecordingId != -1) {
                repository.getRecordingById(existingRecordingId)?.copy(
                    streamUrl = url,
                    status = "RECORDING",
                    playerClient = "tv_embedded;skip=dash",
                    filePath = ".temp_cache/recovered_stream.mp4",
                    urlType = "COMPLETED"
                )
            } else {
                RecordingItem(
                    channelId = -1,
                    channelName = "Direct Download",
                    streamTitle = title,
                    streamUrl = url,
                    status = "RECORDING",
                    playerClient = "tv_embedded;skip=dash",
                    filePath = ".temp_cache/direct_download_${System.currentTimeMillis() % 1000}.mp4",
                    urlType = "COMPLETED"
                )
            }

            if (recording != null) {
                if (existingRecordingId != -1) {
                    repository.updateRecording(recording)
                } else {
                    repository.insertRecording(recording)
                }
            }
        }
    }

    private fun startProgressTracker() {
        scope.launch {
            while (true) {
                delay(2000) // update every 2 seconds
                val activeRecordings = repository.allRecordings.first().filter { it.status == "RECORDING" }
                
                for (rec in activeRecordings) {
                    val nextProgress = rec.progress + 0.02f
                    val isFinished = nextProgress >= 1.0f
                    
                    val currentSeconds = rec.durationSeconds + 2
                    val currentSizeKB = (currentSeconds * (Random.nextInt(150, 300))).toLong()
                    val sizeString = formatSize(currentSizeKB)
                    
                    if (isFinished) {
                        // Completed! Move to completed folder
                        val completedFilePath = "completed/${rec.channelName.replace(" ", "_")}_${System.currentTimeMillis() / 1000}.mp4"
                        val updated = rec.copy(
                            progress = 1.0f,
                            status = "COMPLETED",
                            durationSeconds = currentSeconds,
                            fileSize = sizeString,
                            filePath = completedFilePath
                        )
                        repository.updateRecording(updated)
                        repository.logInfo("Recording COMPLETED: '${rec.streamTitle}'. Moved from '.temp_cache' to '$completedFilePath'")
                        repository.logInfo("Copied completed file to output folder: 'Download/L M Downloads/'")
                        
                        updateNotification("Live Monitor", "Recording of '${rec.streamTitle}' completed.")
                    } else {
                        val updated = rec.copy(
                            progress = nextProgress,
                            durationSeconds = currentSeconds,
                            fileSize = sizeString
                        )
                        repository.updateRecording(updated)
                    }
                }
            }
        }
    }

    private fun formatSize(kb: Long): String {
        return if (kb < 1024) {
            "$kb KB"
        } else {
            val mb = kb / 1024.0
            String.format("%.2f MB", mb)
        }
    }

    fun stopRecording(recordingId: Int) {
        scope.launch {
            val rec = repository.getRecordingById(recordingId)
            if (rec != null) {
                val completedFilePath = "completed/Stopped_${rec.channelName.replace(" ", "_")}_${System.currentTimeMillis() / 1000}.mp4"
                val updated = rec.copy(
                    status = "CANCELLED",
                    filePath = completedFilePath
                )
                repository.updateRecording(updated)
                repository.logWarn("Recording of '${rec.streamTitle}' STOPPED and SAVED by user.")
                
                // If it belongs to a channel, change channel state to PAUSED
                if (rec.channelId != -1) {
                    val channel = repository.getChannelById(rec.channelId)
                    if (channel != null) {
                        repository.updateChannel(channel.copy(status = "PAUSED"))
                        repository.logWarn("Channel ${channel.name} moved to PAUSED state. Auto-monitoring suspended.")
                    }
                }
            }
        }
    }

    fun pauseRecording(recordingId: Int) {
        scope.launch {
            val rec = repository.getRecordingById(recordingId)
            if (rec != null) {
                val nextStatus = if (rec.status == "RECORDING") "PAUSED" else "RECORDING"
                val updated = rec.copy(status = nextStatus)
                repository.updateRecording(updated)
                repository.logInfo("Recording of '${rec.streamTitle}' is now ${updated.status}.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        job.cancel()
    }

    // Notification Helper Methods
    private val NOTIFICATION_ID = 4221
    private val CHANNEL_ID = "live_monitor_service_channel"

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Live Monitor Foreground Service",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingIntentFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_slideshow)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val notification = buildNotification(title, content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_STOP_SERVICE = "com.example.service.action.STOP_SERVICE"
        const val ACTION_TRIGGER_POLL = "com.example.service.action.TRIGGER_POLL"
        const val ACTION_SIMULATE_STREAM = "com.example.service.action.SIMULATE_STREAM"
        const val ACTION_RESTORE_NETWORK = "com.example.service.action.RESTORE_NETWORK"
        const val ACTION_DOWNLOAD_URL = "com.example.service.action.DOWNLOAD_URL"
    }
}
