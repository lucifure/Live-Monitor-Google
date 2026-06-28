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

        // Insert default channel if empty
        viewModelScope.launch {
            val list = repository.allChannels.first()
            if (list.isEmpty()) {
                // Seed standard channels
                repository.insertChannel(
                    MonitoredChannel(
                        name = "Darth MB (Retro Live)",
                        handle = "@darth_mb",
                        status = "MONITORING"
                    )
                )
                repository.insertChannel(
                    MonitoredChannel(
                        name = "Lofi Girl Live",
                        handle = "@lofigirl",
                        status = "PAUSED"
                    )
                )
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
            val formattedHandle = if (handle.startsWith("@")) handle else "@$handle"
            repository.insertChannel(MonitoredChannel(name = name, handle = formattedHandle))
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

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            repository.logInfo("Logs cleared.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_isServiceBound.value) {
            getApplication<Application>().unbindService(serviceConnection)
        }
    }
}
