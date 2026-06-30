package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.LiveMonitorRepository
import com.example.data.MonitoredChannel
import com.example.data.RecordingItem
import com.example.data.SystemLog
import com.example.service.MonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class LiveMonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LiveMonitorRepository
    val channels: StateFlow<List<MonitoredChannel>>
    val recordings: StateFlow<List<RecordingItem>>
    val logs: StateFlow<List<SystemLog>>

    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    private var monitorService: MonitorService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MonitorService.LocalBinder
            monitorService = binder.getService()
            _isServiceBound.value = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            monitorService = null
            _isServiceBound.value = false
        }
    }

    init {
        val db = AppDatabase.getDatabase(application)
        repository = LiveMonitorRepository(db)

        channels = repository.allChannels.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        recordings = repository.allRecordings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        logs = repository.allLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Bind foreground service
        bindMonitorService()

        // Insert default channels only on the first launch
        viewModelScope.launch(Dispatchers.IO) {
            val sharedPrefs = application.getSharedPreferences("live_monitor_prefs", Context.MODE_PRIVATE)
            val seeded = sharedPrefs.getBoolean("channels_seeded", false)
            if (!seeded) {
                val list = repository.allChannels.first()
                if (list.isEmpty()) {
                    // Seed standard channels
                    repository.insertChannel(
                        MonitoredChannel(
                            name = "Ungraduate Gamer Live",
                            handle = "@ungraduategamer",
                            status = "MONITORING"
                        )
                    )
                    repository.insertChannel(
                        MonitoredChannel(
                            name = "Lofi Girl Live",
                            handle = "@lofigirl",
                            status = "MONITORING"
                        )
                    )
                }
                sharedPrefs.edit().putBoolean("channels_seeded", true).apply()
            } else {
                // Ensure @ungraduategamer is present and active even on secondary launches
                val list = repository.allChannels.first()
                if (list.none { it.handle.equals("@ungraduategamer", ignoreCase = true) }) {
                    repository.insertChannel(
                        MonitoredChannel(
                            name = "Ungraduate Gamer Live",
                            handle = "@ungraduategamer",
                            status = "MONITORING"
                        )
                    )
                    repository.logInfo("Proactively added Ungraduate Gamer Live (@ungraduategamer) stream.")
                    // Trigger poll check
                    manuallyPoll()
                }
            }
        }
    }

    private fun bindMonitorService() {
        val intent = Intent(getApplication(), MonitorService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun startService() {
        val intent = Intent(getApplication(), MonitorService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
        bindMonitorService()
    }

    fun stopService() {
        if (_isServiceBound.value) {
            getApplication<Application>().unbindService(serviceConnection)
            _isServiceBound.value = false
            monitorService = null
        }
        val intent = Intent(getApplication(), MonitorService::class.java)
        getApplication<Application>().stopService(intent)
    }

    // Channels Actions
    fun addChannel(name: String, handle: String) {
        viewModelScope.launch {
            val cleanHandle = extractHandleFromInput(handle)
            val displayName = name.trim().ifBlank {
                val base = cleanHandle.substringAfterLast("/")
                base.replace("@", "").replace("_", " ").capitalizeWords()
            }
            
            repository.logInfo("Adding channel. Input: '$handle' -> Cleaned: '$cleanHandle', Name: '$displayName'")
            val channel = MonitoredChannel(name = displayName, handle = cleanHandle, status = "MONITORING")
            val id = repository.insertChannel(channel)
            
            // Trigger manual check immediately so the user doesn't have to wait 60 seconds
            manuallyPoll()
            
            // Asynchronously fetch display name from YouTube to resolve channel's official name
            launch(Dispatchers.IO) {
                try {
                    val actualName = fetchChannelDisplayNameFromYouTube(cleanHandle)
                    if (!actualName.isNullOrBlank()) {
                        val currentChannel = repository.getChannelById(id.toInt())
                        if (currentChannel != null && currentChannel.name == displayName) {
                            repository.updateChannel(currentChannel.copy(name = actualName))
                            repository.logInfo("Resolved channel identity: '$cleanHandle' is named '$actualName'")
                        }
                    }
                } catch (e: Exception) {
                    repository.logWarn("Could not fetch channel display name for '$cleanHandle': ${e.message}")
                }
            }
        }
    }

    fun deleteChannel(id: Int) {
        viewModelScope.launch {
            repository.deleteChannelById(id)
        }
    }

    fun toggleChannelStatus(channel: MonitoredChannel) {
        viewModelScope.launch {
            val nextStatus = when (channel.status) {
                "MONITORING" -> "PAUSED"
                "PAUSED" -> "MONITORING"
                else -> "MONITORING"
            }
            repository.updateChannel(channel.copy(status = nextStatus))
            repository.logInfo("Channel ${channel.name} status updated to $nextStatus.")
        }
    }

    // Recording Actions
    fun pauseRecording(recId: Int) {
        monitorService?.pauseRecording(recId)
    }

    fun stopRecording(recId: Int) {
        monitorService?.stopRecording(recId)
    }

    fun startUrlDownload(url: String, title: String) {
        val intent = Intent(getApplication(), MonitorService::class.java).apply {
            action = MonitorService.ACTION_DOWNLOAD_URL
            putExtra("url", url)
            putExtra("title", title)
        }
        getApplication<Application>().startService(intent)
    }

    // Diagnostics / Simulation Actions
    fun simulateStream(channelId: Int) {
        val intent = Intent(getApplication(), MonitorService::class.java).apply {
            action = MonitorService.ACTION_SIMULATE_STREAM
            putExtra("channel_id", channelId)
        }
        getApplication<Application>().startService(intent)
    }

    fun simulateNetworkRestore() {
        val intent = Intent(getApplication(), MonitorService::class.java).apply {
            action = MonitorService.ACTION_RESTORE_NETWORK
        }
        getApplication<Application>().startService(intent)
    }

    fun manuallyPoll() {
        val intent = Intent(getApplication(), MonitorService::class.java).apply {
            action = MonitorService.ACTION_TRIGGER_POLL
        }
        getApplication<Application>().startService(intent)
    }

    fun updateYtDlpEngine() {
        val intent = Intent(getApplication(), MonitorService::class.java).apply {
            action = MonitorService.ACTION_UPDATE_ENGINE
        }
        getApplication<Application>().startService(intent)
    }

    fun resetYtDlpEngine() {
        val intent = Intent(getApplication(), MonitorService::class.java).apply {
            action = MonitorService.ACTION_RESET_ENGINE
        }
        getApplication<Application>().startService(intent)
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            repository.logInfo("Logs cleared.")
        }
    }

    fun exportAndShareLogs(context: Context) {
        viewModelScope.launch {
            try {
                repository.logInfo("Initiating detailed diagnostics log export...")
                
                val logsContent = withContext(Dispatchers.IO) {
                    val stringBuilder = StringBuilder()
                    stringBuilder.append("==================================================\n")
                    stringBuilder.append("       LIVE MONITOR DIAGNOSTIC TELEMETRY REPORT   \n")
                    stringBuilder.append("==================================================\n")
                    stringBuilder.append("Export Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())}\n")
                    stringBuilder.append("Device Manufacturer: ${android.os.Build.MANUFACTURER}\n")
                    stringBuilder.append("Device Model       : ${android.os.Build.MODEL}\n")
                    stringBuilder.append("Android Version    : ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                    stringBuilder.append("Application ID     : ${context.packageName}\n")
                    stringBuilder.append("==================================================\n\n")

                    // 1. App State summary
                    stringBuilder.append("--- [1] APP DATABASE SUMMARY ---\n")
                    val allRecs = repository.allRecordings.first()
                    val allChs = repository.allChannels.first()
                    stringBuilder.append("Tracked Channels: ${allChs.size}\n")
                    allChs.forEach { ch ->
                        stringBuilder.append("  - [ID: ${ch.id}] Name: '${ch.name}', Handle: '${ch.handle}', Status: ${ch.status}, Last Stream Checked: ${ch.lastChecked}, Last Detected: ${ch.lastStreamDetected}\n")
                    }
                    stringBuilder.append("\nRecorded Items: ${allRecs.size}\n")
                    allRecs.forEach { rec ->
                        stringBuilder.append("  - [ID: ${rec.id}] ChannelID: ${rec.channelId}, Title: '${rec.streamTitle}', Client: '${rec.playerClient}', Status: ${rec.status}, Size: ${rec.fileSize}, Progress: ${rec.progress}, FilePath: '${rec.filePath}', Error: '${rec.errorMessage ?: "None"}'\n")
                    }
                    stringBuilder.append("\n==================================================\n\n")

                    // 2. Database Logs
                    stringBuilder.append("--- [2] DATABASE DIAGNOSTIC ENTRIES ---\n")
                    val dbLogs = repository.allLogs.first()
                    if (dbLogs.isEmpty()) {
                        stringBuilder.append("(No logs in database)\n")
                    } else {
                        dbLogs.forEach { log ->
                            stringBuilder.append("[${log.timestamp}] [${log.level}] ${log.message}\n")
                        }
                    }
                    stringBuilder.append("\n==================================================\n\n")

                    // 3. Programmatic Logcat dump
                    stringBuilder.append("--- [3] SYSTEM LOGCAT DUMP (KEYWORDS FILTERED) ---\n")
                    try {
                        val logcatProcess = Runtime.getRuntime().exec("logcat -d")
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(logcatProcess.inputStream))
                        var line: String?
                        var count = 0
                        // Keep up to 1500 matching lines to avoid memory blowup
                        while (reader.readLine().also { line = it } != null && count < 1500) {
                            val l = line ?: continue
                            if (l.contains("MonitorService", ignoreCase = true) ||
                                l.contains("YoutubeDL", ignoreCase = true) ||
                                l.contains("youtubedl", ignoreCase = true) ||
                                l.contains("FFmpeg", ignoreCase = true) ||
                                l.contains("com.example", ignoreCase = true) ||
                                l.contains("hls_downloader", ignoreCase = true) ||
                                l.contains("AndroidRuntime", ignoreCase = true) ||
                                l.contains("FATAL EXCEPTION", ignoreCase = true)
                            ) {
                                stringBuilder.append(l).append("\n")
                                count++
                            }
                        }
                        if (count == 0) {
                            stringBuilder.append("(No filtered logcat entries found. Ensure logcat read permission or app has logged activity.)\n")
                        } else {
                            stringBuilder.append("Total logcat matching lines retrieved: $count\n")
                        }
                    } catch (le: Exception) {
                        stringBuilder.append("Error trying to run program logcat command: ${le.message}\n")
                        android.util.Log.e("LiveMonitorViewModel", "Error running logcat -d", le)
                    }

                    stringBuilder.append("\n==================================================\n")
                    stringBuilder.append("                 END OF DIAGNOSTIC REPORT         \n")
                    stringBuilder.append("==================================================\n")
                    stringBuilder.toString()
                }

                // Write report to cache directory
                val cacheFile = withContext(Dispatchers.IO) {
                    val file = java.io.File(context.cacheDir, "live_monitor_diagnostic_logs.txt")
                    file.writeText(logsContent)
                    file
                }

                // Share file via standard Intent
                val authority = "${context.packageName}.fileprovider"
                val fileUri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(context, authority, cacheFile)

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Live Monitor Diagnostics Log")
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // Launch chooser
                val chooser = Intent.createChooser(shareIntent, "Save or Send Diagnostic Log .txt")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                
                repository.logInfo("Diagnostic logs exported successfully! Share sheet opened.")
            } catch (e: Exception) {
                repository.logError("Export failed: ${e.message}")
                android.util.Log.e("LiveMonitorViewModel", "Logs export failed", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_isServiceBound.value) {
            getApplication<Application>().unbindService(serviceConnection)
        }
    }

    // Helper to capitalize words for temporary names
    private fun String.capitalizeWords(): String {
        return this.split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() } }
    }

    // Helper to extract a clean handle/path from any YouTube link/handle input
    private fun extractHandleFromInput(input: String): String {
        val trimmed = input.trim()
        
        // 1. Full YouTube URLs
        if (trimmed.contains("youtube.com") || trimmed.contains("youtu.be")) {
            // Match handles: youtube.com/@handle
            val handleRegex = "youtube\\.com/@([^/?#\\s]+)".toRegex(RegexOption.IGNORE_CASE)
            val handleMatch = handleRegex.find(trimmed)
            if (handleMatch != null) {
                val handle = handleMatch.groupValues[1]
                return if (handle.startsWith("@")) handle else "@$handle"
            }
            
            // Match c/custom_name
            val cRegex = "youtube\\.com/c/([^/?#\\s]+)".toRegex(RegexOption.IGNORE_CASE)
            val cMatch = cRegex.find(trimmed)
            if (cMatch != null) {
                return "@" + cMatch.groupValues[1]
            }
            
            // Match channel/UC...
            val channelRegex = "youtube\\.com/channel/([^/?#\\s]+)".toRegex(RegexOption.IGNORE_CASE)
            val channelMatch = channelRegex.find(trimmed)
            if (channelMatch != null) {
                return "channel/" + channelMatch.groupValues[1]
            }
            
            // Match user/name
            val userRegex = "youtube\\.com/user/([^/?#\\s]+)".toRegex(RegexOption.IGNORE_CASE)
            val userMatch = userRegex.find(trimmed)
            if (userMatch != null) {
                return "user/" + userMatch.groupValues[1]
            }
        }
        
        // 2. Raw inputs
        var handle = trimmed.removeSuffix("/").substringAfterLast("/")
        if (handle.isEmpty()) return "@unknown"
        
        if (!handle.startsWith("@") && !handle.startsWith("channel/") && !handle.startsWith("user/")) {
            handle = "@$handle"
        }
        return handle
    }

    // Helper to fetch the actual display name from YouTube page
    private suspend fun fetchChannelDisplayNameFromYouTube(handle: String): String? {
        val client = OkHttpClient()
        val url = "https://www.youtube.com/$handle"
        
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                
                val html = client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                } ?: return@withContext null
                
                // Try og:title meta tag first
                val ogTitleMarker = "<meta property=\"og:title\" content=\""
                val ogTitleIndex = html.indexOf(ogTitleMarker)
                if (ogTitleIndex >= 0) {
                    val contentStart = ogTitleIndex + ogTitleMarker.length
                    val contentEnd = html.indexOf("\"", contentStart)
                    if (contentEnd > contentStart) {
                        val ogTitle = html.substring(contentStart, contentEnd)
                        if (ogTitle.isNotEmpty() && ogTitle != "YouTube") {
                            return@withContext ogTitle
                        }
                    }
                }
                
                // Try <title> tag fallback
                val titleStartMarker = "<title>"
                val titleEndMarker = "</title>"
                val titleStartIndex = html.indexOf(titleStartMarker)
                if (titleStartIndex >= 0) {
                    val contentStart = titleStartIndex + titleStartMarker.length
                    val titleEndIndex = html.indexOf(titleEndMarker, contentStart)
                    if (titleEndIndex > contentStart) {
                        var title = html.substring(contentStart, titleEndIndex)
                        title = title.replace(" - YouTube", "").trim()
                        if (title.isNotEmpty() && title != "YouTube") {
                            return@withContext title
                        }
                    }
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
