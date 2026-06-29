package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Monitoring YouTube channels...", "Polling active channels every 60 seconds."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Monitoring YouTube channels...", "Polling active channels every 60 seconds.")
            )
        }
        
        // Start background polling and checking
        startChannelPolling()
        
        // Automatically verify yt-dlp engine integrity on startup and repair if corrupted
        ensureYtDlpEngineReady()
        
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
                ACTION_UPDATE_ENGINE -> {
                    repository.logInfo("Manual yt-dlp update triggered.")
                    updateYtDlpEngine()
                }
                ACTION_RESET_ENGINE -> {
                    repository.logInfo("Manual yt-dlp engine reset/rollback triggered.")
                    resetAndCleanYtDlp()
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
            val liveInfo = checkLiveStream(updatedChannel.handle)
            if (liveInfo != null) {
                // Channel is LIVE! Start a real HLS recording
                val activeRecordings = repository.allRecordings.first()
                val isAlreadyRecording = activeRecordings.any { it.channelId == updatedChannel.id && it.status == "RECORDING" }
                if (!isAlreadyRecording) {
                    repository.logInfo("Channel ${updatedChannel.name} is LIVE!")
                    triggerRecording(updatedChannel, liveInfo.title, liveInfo.hlsManifestUrl, liveInfo.videoId)
                }
            } else {
                repository.logInfo("Channel ${updatedChannel.name} (${updatedChannel.handle}) status checked: OFFLINE")
            }
        }
    }

    private suspend fun triggerRecording(channel: MonitoredChannel, title: String, manifestUrl: String, videoId: String) {
        val streamUrl = "https://www.youtube.com/${channel.handle}/live"
        val storageFolder = getRecordingFolder(this)
        val fileName = "${channel.handle.replace("@", "")}_${videoId}_${System.currentTimeMillis() / 1000}.mp4"
        val outputFile = File(storageFolder, fileName)

        if (!videoId.startsWith("fake_")) {
            // Live detected, use our integrated yt-dlp recorder!
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
            repository.logInfo("Checking live status for channel: $cleanHandle via $url")
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
                        repository.logWarn("HTTP request failed for $cleanHandle. Code: ${response.code}")
                        null
                    }
                }
            } ?: return null
            
            var isLive = false
            var videoId = ""
            var title = "Live Stream"
            var hlsManifestUrl = ""

            // 1. Check if the final URL itself got redirected to watch?v= or /live/ (with a trailing slash + ID)
            if (finalUrl.contains("/watch?v=") || (finalUrl.contains("/live/") && !finalUrl.endsWith("/live") && !finalUrl.endsWith("/live/"))) {
                isLive = true
                videoId = if (finalUrl.contains("/watch?v=")) {
                    finalUrl.substringAfter("/watch?v=").substringBefore("&").substringBefore("?")
                } else {
                    finalUrl.substringAfter("/live/").substringBefore("?")
                }
                repository.logInfo("Detected live redirection: finalUrl=$finalUrl, videoId=$videoId")
            }
            
            // 2. Check canonical or og:url in HTML
            if (!isLive) {
                val ogUrlRegex = """<meta\s+property="og:url"\s+content="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                val canonicalRegex = """<link\s+rel="canonical"\s+href="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                
                val ogUrlMatch = ogUrlRegex.find(responseText)
                val canonicalMatch = canonicalRegex.find(responseText)
                
                val targetUrl = ogUrlMatch?.groupValues?.get(1) ?: canonicalMatch?.groupValues?.get(1) ?: ""
                if (targetUrl.contains("/watch?v=") || (targetUrl.contains("/live/") && !targetUrl.endsWith("/live") && !targetUrl.endsWith("/live/"))) {
                    isLive = true
                    videoId = if (targetUrl.contains("/watch?v=")) {
                        targetUrl.substringAfter("/watch?v=").substringBefore("&").substringBefore("?")
                    } else {
                        targetUrl.substringAfter("/live/").substringBefore("?")
                    }
                    repository.logInfo("Detected live URL from meta og/canonical: targetUrl=$targetUrl, videoId=$videoId")
                }
            }
            
            // 3. Check for specific live indicators and fallback videoId on page source
            if (!isLive) {
                val hasLiveMarker = responseText.contains("hlsManifestUrl") || 
                        responseText.contains("\"isLive\":true") || 
                        responseText.contains("\"isLive\": true") || 
                        responseText.contains("\"isLiveStream\":true") || 
                        responseText.contains("\"isLiveStream\": true") ||
                        responseText.contains("LIVE_STREAM_OFFLINE") ||
                        responseText.contains("Started streaming") ||
                        responseText.contains("watching now")
                
                if (hasLiveMarker) {
                    val videoIdRegex = """"videoId"\s*:\s*"([^"]+)"""".toRegex()
                    val videoIdMatch = videoIdRegex.find(responseText)
                    if (videoIdMatch != null) {
                        isLive = true
                        videoId = videoIdMatch.groupValues[1]
                        repository.logInfo("Detected live via page markers. videoId=$videoId")
                    } else {
                        // Look inside og:image thumb URL if videoId field not found
                        val ogImageRegex = """<meta\s+property="og:image"\s+content="[^"]+/vi/([^/]+)/[^"]+"""".toRegex(RegexOption.IGNORE_CASE)
                        val ogImageMatch = ogImageRegex.find(responseText)
                        if (ogImageMatch != null) {
                            isLive = true
                            videoId = ogImageMatch.groupValues[1]
                            repository.logInfo("Detected live via og:image fallback. videoId=$videoId")
                        }
                    }
                }
            }
            
            // 4. Check ytInitialPlayerResponse JSON if present
            val playerResponse = extractInitialPlayerResponse(responseText)
            if (playerResponse != null) {
                val videoDetails = playerResponse.optJSONObject("videoDetails")
                val isLiveProp = videoDetails?.optBoolean("isLive") ?: false
                val isLiveStreamProp = videoDetails?.optBoolean("isLiveStream") ?: false
                val videoIdProp = videoDetails?.optString("videoId") ?: ""
                
                if ((isLiveProp || isLiveStreamProp) && videoIdProp.isNotEmpty()) {
                    isLive = true
                    videoId = videoIdProp
                    title = videoDetails.optString("title") ?: title
                }
                
                val streamingData = playerResponse.optJSONObject("streamingData")
                hlsManifestUrl = streamingData?.optString("hlsManifestUrl") ?: ""
            }
            
            if (isLive && videoId.isNotEmpty()) {
                // Parse title if we don't have a good one
                if (title == "Live Stream" || title.isBlank()) {
                    val ogTitleRegex = """<meta\s+property="og:title"\s+content="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                    val titleRegex = """<title>([^<]+)</title>""".toRegex(RegexOption.IGNORE_CASE)
                    
                    val ogTitleMatch = ogTitleRegex.find(responseText)
                    val titleMatch = titleRegex.find(responseText)
                    
                    val rawTitle = ogTitleMatch?.groupValues?.get(1) ?: titleMatch?.groupValues?.get(1) ?: "Live Stream"
                    title = rawTitle.replace(" - YouTube", "").trim()
                }
                
                // Parse hlsManifestUrl if still empty
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
                
                repository.logInfo("Channel $cleanHandle IS LIVE! Video ID: $videoId, Title: '$title'")
                return LiveInfo(videoId, title, hlsManifestUrl)
            } else {
                repository.logInfo("Channel $cleanHandle checked: OFFLINE")
            }
        } catch (e: Exception) {
            repository.logError("Error checking live status for $handle: ${e.message}")
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
                com.yausername.youtubedl_android.YoutubeDL.getInstance().init(applicationContext)
                com.yausername.ffmpeg.FFmpeg.getInstance().init(applicationContext)
            } catch (e: Exception) {
                val detailedError = "yt-dlp init error: ${e.message}. Cause: ${e.cause?.message}. Stack: ${android.util.Log.getStackTraceString(e)}"
                repository.logError(detailedError)
                android.util.Log.e("MonitorService", detailedError, e)
            }

            val request = com.yausername.youtubedl_android.YoutubeDLRequest(videoUrl)
            request.addOption("--no-mtime")
            request.addOption("-o", outputFile.absolutePath)
            
            // Replicate the highly successful Termux command-line options exactly
            request.addOption("--extractor-args", "youtube:player-client=android_vr")
            request.addOption("--js-runtime", "quickjs")
            request.addOption("--wait-for-video", "60")
            request.addOption("--live-from-start")
            request.addOption("--hls-use-mpegts")
            request.addOption("--no-part")
            request.addOption("--skip-unavailable-fragments")
            request.addOption("--retries", "10")
            request.addOption("--fragment-retries", "infinite")
            request.addOption("--extractor-retries", "infinite")
            request.addOption("--file-access-retries", "infinite")
            request.addOption("--retry-sleep", "5")
            request.addOption("--socket-timeout", "10")
            request.addOption("--force-ipv4")
            request.addOption("--no-check-certificates")
            request.addOption("-f", "bv*[height<=480]+ba/b/best")
            request.addOption("--merge-output-format", "mp4")

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
                com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request, object : com.yausername.youtubedl_android.DownloadProgressCallback {
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
                    val errMsg = "yt-dlp exited with code ${response.exitCode}. Error: ${response.err ?: "Unknown error"}"
                    repository.updateRecording(rec.copy(status = "CANCELLED", errorMessage = errMsg))
                    repository.logError("yt-dlp exited with non-zero code ${response.exitCode}. Error output: ${response.err}")
                }
            }
        } catch (e: Exception) {
            val errMsg = "yt-dlp failed: ${e.message}"
            repository.logError("yt-dlp download failed: ${e.message}")
            val rec = repository.getRecordingById(recordingId)
            if (rec != null) {
                repository.updateRecording(rec.copy(status = "CANCELLED", errorMessage = errMsg))
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

    private fun ensureYtDlpEngineReady() {
        scope.launch(Dispatchers.IO) {
            repository.logInfo("Verifying yt-dlp engine integrity...")
            try {
                com.yausername.youtubedl_android.YoutubeDL.getInstance().init(applicationContext)
                com.yausername.ffmpeg.FFmpeg.getInstance().init(applicationContext)
                val ver = com.yausername.youtubedl_android.YoutubeDL.getInstance().version(applicationContext)
                repository.logInfo("yt-dlp engine verified. Version: $ver")
            } catch (e: Exception) {
                repository.logError("yt-dlp engine integrity check failed: ${e.message}. Attempting automatic self-repair...")
                try {
                    val noBackupDir = File(applicationContext.noBackupFilesDir, "youtubedl-android")
                    if (noBackupDir.exists()) {
                        noBackupDir.deleteRecursively()
                    }
                    val filesDir = File(applicationContext.filesDir, "youtubedl-android")
                    if (filesDir.exists()) {
                        filesDir.deleteRecursively()
                    }
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().init(applicationContext)
                    com.yausername.ffmpeg.FFmpeg.getInstance().init(applicationContext)
                    val ver = com.yausername.youtubedl_android.YoutubeDL.getInstance().version(applicationContext)
                    repository.logInfo("yt-dlp engine self-repair successful! Restored to pre-bundled version: $ver")
                } catch (repairEx: Exception) {
                    val fatalErr = "yt-dlp engine self-repair failed: ${repairEx.message}"
                    repository.logError(fatalErr)
                    android.util.Log.e("MonitorService", fatalErr, repairEx)
                }
            }
        }
    }

    private fun updateYtDlpEngine() {
        scope.launch(Dispatchers.IO) {
            repository.logWarn("Starting manual update of yt-dlp engine to Python 3.8 compatible v2025.01.12...")
            try {
                // Ensure base directory/extraction is initialized
                com.yausername.youtubedl_android.YoutubeDL.getInstance().init(applicationContext)
                
                val url = "https://github.com/yt-dlp/yt-dlp/releases/download/2025.01.12/yt-dlp"
                val request = Request.Builder().url(url).build()
                
                val targetFileNoBackup = File(applicationContext.noBackupFilesDir, "youtubedl-android/youtube-dl")
                val targetFileFiles = File(applicationContext.filesDir, "youtubedl-android/youtube-dl")
                
                targetFileNoBackup.parentFile?.mkdirs()
                
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw java.io.IOException("Failed to download: ${response.code} ${response.message}")
                    }
                    val body = response.body ?: throw java.io.IOException("Null response body")
                    
                    targetFileNoBackup.outputStream().use { fos ->
                        body.byteStream().copyTo(fos)
                    }
                    targetFileNoBackup.setExecutable(true, false)
                    
                    if (targetFileFiles.parentFile?.exists() == true) {
                        targetFileNoBackup.copyTo(targetFileFiles, overwrite = true)
                        targetFileFiles.setExecutable(true, false)
                    }
                }
                
                val ver = com.yausername.youtubedl_android.YoutubeDL.getInstance().version(applicationContext)
                repository.logInfo("yt-dlp engine updated successfully to v2025.01.12! Current engine version: $ver")
            } catch (e: Exception) {
                val detailedError = "yt-dlp engine update failed: ${e.message}. Attempting rollback to pre-bundled version..."
                repository.logError(detailedError)
                android.util.Log.e("MonitorService", detailedError, e)
                
                try {
                    val noBackupDir = File(applicationContext.noBackupFilesDir, "youtubedl-android")
                    if (noBackupDir.exists()) noBackupDir.deleteRecursively()
                    val filesDir = File(applicationContext.filesDir, "youtubedl-android")
                    if (filesDir.exists()) filesDir.deleteRecursively()
                    
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().init(applicationContext)
                    com.yausername.ffmpeg.FFmpeg.getInstance().init(applicationContext)
                    val ver = com.yausername.youtubedl_android.YoutubeDL.getInstance().version(applicationContext)
                    repository.logInfo("Rollback successful. Pre-bundled version restored: $ver")
                } catch (rollbackEx: Exception) {
                    repository.logError("Rollback failed: ${rollbackEx.message}")
                }
            }
        }
    }

    private fun resetAndCleanYtDlp() {
        scope.launch(Dispatchers.IO) {
            repository.logWarn("Resetting and cleaning yt-dlp to pre-bundled version...")
            try {
                val noBackupDir = File(applicationContext.noBackupFilesDir, "youtubedl-android")
                if (noBackupDir.exists()) {
                    noBackupDir.deleteRecursively()
                }
                val filesDir = File(applicationContext.filesDir, "youtubedl-android")
                if (filesDir.exists()) {
                    filesDir.deleteRecursively()
                }
                
                com.yausername.youtubedl_android.YoutubeDL.getInstance().init(applicationContext)
                com.yausername.ffmpeg.FFmpeg.getInstance().init(applicationContext)
                
                val ver = com.yausername.youtubedl_android.YoutubeDL.getInstance().version(applicationContext)
                repository.logInfo("yt-dlp engine rollback completed successfully! Version: $ver")
            } catch (e: Exception) {
                repository.logError("Failed to reset yt-dlp engine: ${e.message}")
            }
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
        const val ACTION_UPDATE_ENGINE = "com.example.service.action.UPDATE_ENGINE"
        const val ACTION_RESET_ENGINE = "com.example.service.action.RESET_ENGINE"
    }
}

data class LiveInfo(val videoId: String, val title: String, val hlsManifestUrl: String)
data class HlsSegment(val url: String, val duration: Double, val size: Long)
