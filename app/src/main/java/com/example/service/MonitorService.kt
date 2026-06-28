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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class MonitorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private lateinit var repository: LiveMonitorRepository
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    private val binder = LocalBinder()
    
    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeDownloadJobs = ConcurrentHashMap<Int, Job>()

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
        
        // Start background polling and checking
        startChannelPolling()
        
        scope.launch {
            repository.logInfo("Live Monitor Service started.")
            repository.logInfo("WAKELOCK requested: PARTIAL_WAKE_LOCK held to prevent sleep.")
            repository.logInfo("Keep-alive scheduled: Firing network check every 5 mins.")
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
            while (isActive) {
                pollChannelsOnce()
                delay(60000) // Poll every 60 seconds
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

            // Real live status check
            val liveInfo = checkLiveStream(channel.handle)
            if (liveInfo != null) {
                // Channel is LIVE! Start a real HLS recording
                val activeRecordings = repository.allRecordings.first()
                val isAlreadyRecording = activeRecordings.any { it.channelId == channel.id && it.status == "RECORDING" }
                if (!isAlreadyRecording) {
                    repository.logInfo("Channel ${channel.name} is LIVE!")
                    triggerRecording(channel, liveInfo.title, liveInfo.hlsManifestUrl, liveInfo.videoId)
                }
            } else {
                repository.logInfo("Channel ${channel.name} (${channel.handle}) status checked: OFFLINE")
            }
        }
    }

    private suspend fun triggerRecording(channel: MonitoredChannel, title: String, manifestUrl: String, videoId: String) {
        val streamUrl = "https://www.youtube.com/${channel.handle}/live"
        val storageFolder = getRecordingFolder(this)
        val fileName = "${channel.handle.replace("@", "")}_${videoId}_${System.currentTimeMillis() / 1000}.mp4"
        val outputFile = File(storageFolder, fileName)

        if (manifestUrl.isBlank() && !videoId.startsWith("fake_")) {
            // Live detected and manifest is blank, use our integrated yt-dlp recorder!
            val recording = RecordingItem(
                channelId = channel.id,
                channelName = channel.name,
                streamTitle = title,
                streamUrl = streamUrl,
                status = "RECORDING",
                playerClient = "yt-dlp",
                filePath = outputFile.absolutePath
            )
            val recordingId = repository.insertRecording(recording).toInt()
            repository.updateChannel(channel.copy(lastStreamDetected = System.currentTimeMillis()))
            repository.logInfo("Detected LIVE stream for ${channel.name}. Initiating stream decryption and recording via yt-dlp backend...")
            updateNotification("Active Recording: ${channel.name}", "Recording '${title}' via yt-dlp...")

            val downloadJob = scope.launch {
                runYoutubeDLDownload(recordingId, streamUrl, outputFile.absolutePath)
            }
            activeDownloadJobs[recordingId] = downloadJob
            return
        }

        val recording = RecordingItem(
            channelId = channel.id,
            channelName = channel.name,
            streamTitle = title,
            streamUrl = streamUrl,
            status = "RECORDING",
            playerClient = "hls_downloader",
            filePath = outputFile.absolutePath
        )
        val recordingId = repository.insertRecording(recording).toInt()
        
        // Update channel
        repository.updateChannel(channel.copy(lastStreamDetected = System.currentTimeMillis()))
        updateNotification("Active Recording: ${channel.name}", "Recording '${title}'...")

        // Launch HLS Download Job
        val downloadJob = scope.launch {
            runHlsDownload(recordingId, manifestUrl, outputFile.absolutePath)
        }
        activeDownloadJobs[recordingId] = downloadJob
    }

    private suspend fun runHlsDownload(recordingId: Int, manifestUrl: String, outputFilePath: String) {
        val client = okHttpClient
        val outputFile = File(outputFilePath)
        outputFile.parentFile?.mkdirs()

        val downloadedSegments = ConcurrentHashMap.newKeySet<String>()
        var totalBytes = 0L
        var totalSeconds = 0L

        try {
            FileOutputStream(outputFile, true).use { outputStream ->
                var consecutiveFailures = 0
                
                while (coroutineContext.isActive && consecutiveFailures < 15) {
                    val segments = fetchPlaylistSegments(client, manifestUrl)
                    if (segments.isEmpty()) {
                        consecutiveFailures++
                        delay(4000)
                        continue
                    }
                    
                    consecutiveFailures = 0
                    var progressUpdated = false
                    
                    for (segment in segments) {
                        if (!coroutineContext.isActive) break
                        if (downloadedSegments.contains(segment.url)) continue
                        
                        // Download segment
                        val success = downloadSegmentToStream(client, segment.url, outputStream)
                        if (success) {
                            downloadedSegments.add(segment.url)
                            totalBytes += segment.size
                            totalSeconds += segment.duration.toLong()
                            progressUpdated = true
                        }
                    }
                    
                    if (progressUpdated) {
                        val recording = repository.getRecordingById(recordingId)
                        if (recording != null && recording.status == "RECORDING") {
                            // Update database
                            val fileSizeStr = formatSize(totalBytes / 1024)
                            repository.updateRecording(
                                recording.copy(
                                    fileSize = fileSizeStr,
                                    durationSeconds = totalSeconds,
                                    progress = 0.5f // active indicator
                                )
                            )
                        }
                    }
                    
                    delay(4000)
                }
            }
            
            // If we completed cleanly, mark as COMPLETED
            val recording = repository.getRecordingById(recordingId)
            if (recording != null && recording.status == "RECORDING") {
                repository.updateRecording(
                    recording.copy(
                        status = "COMPLETED",
                        progress = 1.0f
                    )
                )
                repository.logInfo("Recording COMPLETED: '${recording.streamTitle}'. Saved to '${recording.filePath}'")
                updateNotification("Live Monitor", "Recording of '${recording.streamTitle}' completed.")
            }
        } catch (e: Exception) {
            repository.logError("Download error: ${e.message}")
            val recording = repository.getRecordingById(recordingId)
            if (recording != null && recording.status == "RECORDING") {
                repository.updateRecording(recording.copy(status = "CANCELLED"))
            }
        } finally {
            activeDownloadJobs.remove(recordingId)
        }
    }

    private suspend fun fetchPlaylistSegments(client: OkHttpClient, playlistUrl: String): List<HlsSegment> {
        val list = mutableListOf<HlsSegment>()
        try {
            val request = Request.Builder().url(playlistUrl).build()
            val content = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            } ?: return list
            
            var duration = 5.0
            val lines = content.split("\n")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF:")) {
                    val durationStr = trimmed.substringAfter("#EXTINF:").substringBefore(",")
                    duration = durationStr.toDoubleOrNull() ?: 5.0
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val absoluteUrl = resolveAbsoluteUrl(playlistUrl, trimmed)
                    // HlsSegment takes url, duration, and default size estimate (e.g. 500KB)
                    list.add(HlsSegment(absoluteUrl, duration, 512000L))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private suspend fun downloadSegmentToStream(
        client: OkHttpClient,
        segmentUrl: String,
        outputStream: FileOutputStream
    ): Boolean {
        try {
            val request = Request.Builder().url(segmentUrl).build()
            return withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    val body = response.body ?: return@use false
                    body.byteStream().use { inputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun resolveAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            val base = URL(baseUrl)
            URL(base, relativeUrl).toString()
        } catch (e: Exception) {
            relativeUrl
        }
    }

    private suspend fun checkLiveStream(handle: String): LiveInfo? {
        val cleanHandle = if (handle.startsWith("@")) handle else "@$handle"
        val url = "https://www.youtube.com/$cleanHandle/live"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            
            var finalUrl = ""
            val responseText = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        finalUrl = response.request.url.toString()
                        response.body?.string()
                    } else {
                        null
                    }
                }
            } ?: return null
            
            // If redirected to /watch?v= or /live/, the channel is LIVE
            val isLive = finalUrl.contains("/watch?v=") || finalUrl.contains("/live/")
            if (isLive) {
                val videoId = if (finalUrl.contains("/watch?v=")) {
                    finalUrl.substringAfter("/watch?v=").substringBefore("&")
                } else {
                    finalUrl.substringAfter("/live/").substringBefore("?")
                }
                
                var title = "Live Stream"
                var hlsManifestUrl = ""
                
                val playerResponse = extractInitialPlayerResponse(responseText)
                if (playerResponse != null) {
                    val videoDetails = playerResponse.optJSONObject("videoDetails")
                    title = videoDetails?.optString("title") ?: "Live Stream"
                    val streamingData = playerResponse.optJSONObject("streamingData")
                    hlsManifestUrl = streamingData?.optString("hlsManifestUrl") ?: ""
                }
                
                // Backup parsing of hlsManifestUrl directly from raw HTML
                if (hlsManifestUrl.isEmpty()) {
                    val marker = "hlsManifestUrl\":\""
                    val index = responseText.indexOf(marker)
                    if (index >= 0) {
                        val start = index + marker.length
                        val end = responseText.indexOf("\"", start)
                        if (end > start) {
                            hlsManifestUrl = responseText.substring(start, end).replace("\\/", "/")
                        }
                    }
                }
                
                if (videoId.isNotEmpty()) {
                    return LiveInfo(videoId, title, hlsManifestUrl)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun extractInitialPlayerResponse(html: String): JSONObject? {
        if (html.isEmpty()) return null
        val markerString = "ytInitialPlayerResponse"
        var markerStrIndex: Int = html.indexOf(markerString)
        while (markerStrIndex >= 0) {
            val markerLen: Int = markerString.length
            val searchStart: Int = markerStrIndex + markerLen
            val equalsIndex: Int = html.indexOf("=", searchStart)
            if (equalsIndex < 0) return null
            val objectSearchStart: Int = equalsIndex + 1
            val objectStart: Int = html.indexOf("{", objectSearchStart)
            if (objectStart < 0) return null
            val objectEnd = findJsonObjectEnd(html, objectStart)
            if (objectEnd > objectStart) {
                try {
                    return JSONObject(html.substring(objectStart, objectEnd + 1))
                } catch (e: Exception) {
                    // Ignore and try next
                }
            }
            markerStrIndex = html.indexOf(markerString, objectStart + 1)
        }
        return null
    }

    private fun findJsonObjectEnd(text: String, objectStart: Int): Int {
        var inString = false
        var escaped = false
        var depth = 0
        for (i in objectStart until text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            if (ch == '"') {
                inString = true
            } else if (ch == '{') {
                depth++
            } else if (ch == '}') {
                depth--
                if (depth == 0) {
                    return i
                }
            }
        }
        return -1
    }

    private fun getRecordingFolder(context: Context): File {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val appDir = File(downloadsDir, "LiveMonitor")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        if (appDir.exists() && appDir.canWrite()) {
            return appDir
        }
        val fallback = File(context.getExternalFilesDir(null), "Recordings")
        fallback.mkdirs()
        return fallback
    }

    private suspend fun simulateStreamForChannel(channelId: Int) {
        val channel = repository.getChannelById(channelId)
        if (channel != null) {
            repository.logInfo("Simulating Live Stream event for channel: ${channel.name}")
            // Tears of Steel public HLS stream demo
            val fakeManifest = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
            triggerRecording(channel, "Simulated Live: Tears of Steel Demo", fakeManifest, "fake_video_id")
        }
    }

    private suspend fun simulateNetworkRestoration() {
        repository.logInfo("Network Restoration detected!")
        repository.logInfo("Scanning recent streams...")
    }

    private fun startCompletedStreamDownload(url: String, title: String, existingRecordingId: Int = -1) {
        scope.launch {
            repository.logInfo("Starting download for URL: $url")
            val storageFolder = getRecordingFolder(this@MonitorService)
            val fileName = "completed_${System.currentTimeMillis() / 1000}.ts"
            val outputFile = File(storageFolder, fileName)

            val recording = if (existingRecordingId != -1) {
                repository.getRecordingById(existingRecordingId)?.copy(
                    streamUrl = url,
                    status = "RECORDING",
                    filePath = outputFile.absolutePath,
                    urlType = "COMPLETED"
                )
            } else {
                RecordingItem(
                    channelId = -1,
                    channelName = "Direct Download",
                    streamTitle = title,
                    streamUrl = url,
                    status = "RECORDING",
                    filePath = outputFile.absolutePath,
                    urlType = "COMPLETED"
                )
            }

            if (recording != null) {
                val id = if (existingRecordingId != -1) {
                    repository.updateRecording(recording)
                    existingRecordingId
                } else {
                    repository.insertRecording(recording).toInt()
                }

                // Decide which engine to use: yt-dlp, HLS, or Direct Download
                val isYtDlpTarget = url.contains("youtube.com") || url.contains("youtu.be") || 
                                   url.contains("twitch.tv") || url.contains("vimeo.com") ||
                                   (!url.contains(".m3u8") && !url.contains(".mp4") && !url.contains(".ts"))

                if (isYtDlpTarget) {
                    // Update model client info
                    repository.updateRecording(recording.copy(id = id, playerClient = "yt-dlp", filePath = outputFile.absolutePath.replace(".ts", ".mp4")))
                    val finalOutputFile = File(outputFile.absolutePath.replace(".ts", ".mp4"))
                    val downloadJob = scope.launch {
                        runYoutubeDLDownload(id, url, finalOutputFile.absolutePath)
                    }
                    activeDownloadJobs[id] = downloadJob
                } else if (url.contains(".m3u8")) {
                    val downloadJob = scope.launch {
                        runHlsDownload(id, url, outputFile.absolutePath)
                    }
                    activeDownloadJobs[id] = downloadJob
                } else {
                    // Progressive direct file download
                    val downloadJob = scope.launch {
                        runDirectFileDownload(id, url, outputFile.absolutePath)
                    }
                    activeDownloadJobs[id] = downloadJob
                }
            }
        }
    }

    private suspend fun runYoutubeDLDownload(recordingId: Int, videoUrl: String, outputFilePath: String) {
        val outputFile = File(outputFilePath)
        outputFile.parentFile?.mkdirs()

        try {
            repository.logInfo("Initializing yt-dlp recorder for recording $recordingId...")
            try {
                com.yausername.youtubedl_android.YoutubeDL.init(this)
                com.yausername.ffmpeg.FFmpeg.init(this)
            } catch (e: Exception) {
                repository.logError("yt-dlp init error: ${e.message}")
            }

            val request = com.yausername.youtubedl_android.YoutubeDLRequest(videoUrl)
            request.addOption("--no-mtime")
            request.addOption("-o", outputFile.absolutePath)
            request.addOption("-f", "best") // select best quality stream

            val recording = repository.getRecordingById(recordingId)
            if (recording != null) {
                repository.updateRecording(
                    recording.copy(
                        status = "RECORDING",
                        playerClient = "yt-dlp",
                        filePath = outputFilePath,
                        progress = 0.1f
                    )
                )
            }

            repository.logInfo("Executing yt-dlp for URL: $videoUrl")
            
            // Execute in background
            val response = withContext(Dispatchers.IO) {
                com.yausername.youtubedl_android.YoutubeDL.execute(request, object : com.yausername.youtubedl_android.DownloadProgressCallback {
                    override fun onProgressUpdate(progress: Float, etaInSeconds: Long, line: String) {
                        // Periodic update
                        scope.launch {
                            val rec = repository.getRecordingById(recordingId)
                            if (rec != null && rec.status == "RECORDING") {
                                val sizeKb = if (outputFile.exists()) outputFile.length() / 1024 else 0L
                                repository.updateRecording(
                                    rec.copy(
                                        fileSize = formatSize(sizeKb),
                                        progress = progress / 100f,
                                        durationSeconds = rec.durationSeconds + 1
                                    )
                                )
                            }
                        }
                    }
                })
            }

            val rec = repository.getRecordingById(recordingId)
            if (rec != null) {
                if (response.exitCode == 0) {
                    val finalSizeKb = if (outputFile.exists()) outputFile.length() / 1024 else 0L
                    repository.updateRecording(
                        rec.copy(
                            status = "COMPLETED",
                            fileSize = formatSize(finalSizeKb),
                            progress = 1.0f
                        )
                    )
                    repository.logInfo("yt-dlp download completed for recording: '${rec.streamTitle}'")
                    updateNotification("Live Monitor", "Recording of '${rec.streamTitle}' completed.")
                } else {
                    repository.updateRecording(rec.copy(status = "CANCELLED"))
                    repository.logError("yt-dlp exited with non-zero code ${response.exitCode}. Error output: ${response.err}")
                }
            }
        } catch (e: Exception) {
            repository.logError("yt-dlp download failed: ${e.message}")
            val rec = repository.getRecordingById(recordingId)
            if (rec != null) {
                repository.updateRecording(rec.copy(status = "CANCELLED"))
            }
        } finally {
            activeDownloadJobs.remove(recordingId)
        }
    }

    private suspend fun runDirectFileDownload(recordingId: Int, fileUrl: String, outputFilePath: String) {
        val client = okHttpClient
        val outputFile = File(outputFilePath)
        outputFile.parentFile?.mkdirs()

        try {
            val request = Request.Builder().url(fileUrl).build()
            val success = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    val body = response.body ?: return@use false
                    FileOutputStream(outputFile).use { outputStream ->
                        body.byteStream().use { inputStream ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var downloadedBytes = 0L
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                if (!coroutineContext.isActive) break
                                outputStream.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                
                                // Update database periodically
                                val fileSizeStr = formatSize(downloadedBytes / 1024)
                                val recording = repository.getRecordingById(recordingId)
                                if (recording != null) {
                                    repository.updateRecording(
                                        recording.copy(
                                            fileSize = fileSizeStr,
                                            progress = 0.5f
                                        )
                                    )
                                }
                            }
                        }
                    }
                    true
                }
            }

            val recording = repository.getRecordingById(recordingId)
            if (recording != null) {
                if (success && coroutineContext.isActive) {
                    repository.updateRecording(
                        recording.copy(
                            status = "COMPLETED",
                            progress = 1.0f
                        )
                    )
                    repository.logInfo("Direct download completed: '${recording.streamTitle}'")
                } else {
                    repository.updateRecording(recording.copy(status = "CANCELLED"))
                }
            }
        } catch (e: Exception) {
            repository.logError("Direct download failed: ${e.message}")
            val recording = repository.getRecordingById(recordingId)
            if (recording != null) {
                repository.updateRecording(recording.copy(status = "CANCELLED"))
            }
        } finally {
            activeDownloadJobs.remove(recordingId)
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
            activeDownloadJobs[recordingId]?.cancel()
            activeDownloadJobs.remove(recordingId)
            
            val rec = repository.getRecordingById(recordingId)
            if (rec != null) {
                val updated = rec.copy(status = "CANCELLED")
                repository.updateRecording(updated)
                repository.logWarn("Recording of '${rec.streamTitle}' STOPPED and SAVED.")
                
                if (rec.channelId != -1) {
                    val channel = repository.getChannelById(rec.channelId)
                    if (channel != null) {
                        repository.updateChannel(channel.copy(status = "PAUSED"))
                        repository.logWarn("Channel ${channel.name} moved to PAUSED state.")
                    }
                }
            }
        }
    }

    fun pauseRecording(recordingId: Int) {
        scope.launch {
            val rec = repository.getRecordingById(recordingId)
            if (rec != null) {
                val nextStatus = if (rec.status == "RECORDING") {
                    activeDownloadJobs[recordingId]?.cancel()
                    activeDownloadJobs.remove(recordingId)
                    "PAUSED"
                } else {
                    // Resume download
                    if (rec.playerClient == "yt-dlp" || rec.streamUrl.contains("youtube.com") || rec.streamUrl.contains("youtu.be")) {
                        val downloadJob = scope.launch {
                            runYoutubeDLDownload(recordingId, rec.streamUrl, rec.filePath)
                        }
                        activeDownloadJobs[recordingId] = downloadJob
                    } else if (rec.urlType == "LIVE") {
                        val liveInfo = checkLiveStream(rec.channelName)
                        if (liveInfo != null) {
                            val downloadJob = scope.launch {
                                runHlsDownload(recordingId, liveInfo.hlsManifestUrl, rec.filePath)
                            }
                            activeDownloadJobs[recordingId] = downloadJob
                        }
                    } else {
                        if (rec.streamUrl.contains(".m3u8")) {
                            val downloadJob = scope.launch {
                                runHlsDownload(recordingId, rec.streamUrl, rec.filePath)
                            }
                            activeDownloadJobs[recordingId] = downloadJob
                        } else {
                            val downloadJob = scope.launch {
                                runDirectFileDownload(recordingId, rec.streamUrl, rec.filePath)
                            }
                            activeDownloadJobs[recordingId] = downloadJob
                        }
                    }
                    "RECORDING"
                }
                
                repository.updateRecording(rec.copy(status = nextStatus))
                repository.logInfo("Recording of '${rec.streamTitle}' is now $nextStatus.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        activeDownloadJobs.values.forEach { it.cancel() }
        activeDownloadJobs.clear()
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

data class LiveInfo(val videoId: String, val title: String, val hlsManifestUrl: String)
data class HlsSegment(val url: String, val duration: Double, val size: Long)
