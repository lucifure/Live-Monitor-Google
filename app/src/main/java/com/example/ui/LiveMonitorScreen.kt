package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MonitoredChannel
import com.example.data.RecordingItem
import com.example.data.SystemLog
import com.example.ui.theme.CosmicBlack
import com.example.ui.theme.CosmicSlate
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.GlowRed
import com.example.ui.theme.SunsetOrange
import com.example.ui.theme.SoftGrey
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.example.viewmodel.LiveMonitorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveMonitorAppScreen(viewModel: LiveMonitorViewModel) {
    val context = LocalContext.current
    val channels by viewModel.channels.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isServiceBound by viewModel.isServiceBound.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var stopRecordingIdDialog by remember { mutableStateOf<Int?>(null) }
    var activePlaybackRecording by remember { mutableStateOf<RecordingItem?>(null) }
    var activeLiveMonitorRecording by remember { mutableStateOf<RecordingItem?>(null) }

    val tabs = listOf("CHANNELS", "DOWNLOADS", "LOGS & DIAGS")

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                CosmicBlack,
                                CosmicSlate.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(CyberBlue, CyberGreen))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = "App Icon",
                                tint = CosmicBlack,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "LIVE MONITOR",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    // Service Running Indicator Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isServiceBound) CyberGreen.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f))
                            .border(
                                width = 1.dp,
                                color = if (isServiceBound) CyberGreen else GlowRed,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (isServiceBound) {
                                    viewModel.stopService()
                                } else {
                                    viewModel.startService()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val pulseAlpha by rememberInfiniteTransition().animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .alpha(if (isServiceBound) pulseAlpha else 1f)
                                    .background(if (isServiceBound) CyberGreen else GlowRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isServiceBound) "SERVICE ACTIVE" else "SERVICE OFF",
                                color = if (isServiceBound) CyberGreen else GlowRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = CyberBlue,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = CyberBlue,
                            height = 3.dp
                        )
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (selectedTab == index) CyberBlue else Color.White.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier.testTag("tab_$index")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    CyberBlue,
                                    CyberGreen,
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        },
        bottomBar = {
            // Safe drawing padding for Bottom Navigation Bar
            Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CosmicBlack,
                            CosmicSlate,
                            CosmicBlack
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ChannelsTab(
                    channels = channels,
                    recordings = recordings,
                    onToggleMonitoring = { viewModel.toggleChannelStatus(it) },
                    onDeleteChannel = { viewModel.deleteChannel(it) },
                    onTriggerSimulateLive = { viewModel.simulateStream(it) },
                    onForceCheck = { viewModel.manuallyPoll() },
                    onMonitorLiveFeed = { activeLiveMonitorRecording = it },
                    onAddChannel = { url -> viewModel.addChannel("", url) }
                )
                1 -> DownloadsTab(
                    recordings = recordings,
                    onGetClicked = { url, title -> viewModel.startUrlDownload(url, title) },
                    onPauseClicked = { viewModel.pauseRecording(it) },
                    onStopClicked = { stopRecordingIdDialog = it },
                    onOpenClicked = { activePlaybackRecording = it }
                )
                2 -> LogsTab(
                    logs = logs,
                    onClearLogs = { viewModel.clearLogs() },
                    onManualPoll = { viewModel.manuallyPoll() },
                    onRestoreNetworkSimulation = { viewModel.simulateNetworkRestore() },
                    onUpdateEngine = { viewModel.updateYtDlpEngine() },
                    onResetEngine = { viewModel.resetYtDlpEngine() },
                    onExportLogs = { viewModel.exportAndShareLogs(context) }
                )
            }
        }
    }

    // Stop and Save Dialog
    if (stopRecordingIdDialog != null) {
        val recId = stopRecordingIdDialog!!
        AlertDialog(
            onDismissRequest = { stopRecordingIdDialog = null },
            title = {
                Text(
                    text = "Stop and save recording?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "This will stop the stream recording immediately, compile the partial video file, and save it. The associated channel will transition to PAUSED state.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.stopRecording(recId)
                        stopRecordingIdDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlowRed, contentColor = Color.White)
                ) {
                    Text("STOP & SAVE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { stopRecordingIdDialog = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.6f))
                ) {
                    Text("KEEP RECORDING")
                }
            },
            containerColor = CosmicSlate,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Active Live Monitor Player Dialog
    if (activeLiveMonitorRecording != null) {
        LiveMonitorPlayerDialog(
            recording = activeLiveMonitorRecording!!,
            onDismissRequest = { activeLiveMonitorRecording = null }
        )
    }

    // Interactive Media Player Dialog
    if (activePlaybackRecording != null) {
        val recording = activePlaybackRecording!!
        AlertDialog(
            onDismissRequest = { activePlaybackRecording = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(CyberBlue)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE PLAYER (PREVIEW)",
                        color = CyberBlue,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title and source
                    Text(
                        text = recording.streamTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = recording.channelName,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    // Waveform simulation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CosmicBlack)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val waveHeight1 by rememberInfiniteTransition().animateFloat(
                            initialValue = 0.2f,
                            targetValue = 0.9f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(400),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        val waveHeight2 by rememberInfiniteTransition().animateFloat(
                            initialValue = 0.9f,
                            targetValue = 0.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(350),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        val waveHeight3 by rememberInfiniteTransition().animateFloat(
                            initialValue = 0.4f,
                            targetValue = 0.8f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500),
                                repeatMode = RepeatMode.Reverse
                            )
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(waveHeight1, waveHeight3, waveHeight2, waveHeight1, waveHeight3, waveHeight2, waveHeight1).forEach { height ->
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height((60 * height).dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Brush.verticalGradient(listOf(CyberBlue, CyberGreen)))
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "File: ${recording.filePath}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Status: ${recording.status} | Size: ${recording.fileSize} | Client: ${recording.playerClient}",
                        color = CyberGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { activePlaybackRecording = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue, contentColor = CosmicBlack)
                ) {
                    Text("CLOSE", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CosmicSlate,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ---------------------- TAB 1: CHANNELS ----------------------
@Composable
fun ChannelsTab(
    channels: List<MonitoredChannel>,
    recordings: List<RecordingItem>,
    onToggleMonitoring: (MonitoredChannel) -> Unit,
    onDeleteChannel: (Int) -> Unit,
    onTriggerSimulateLive: (Int) -> Unit,
    onForceCheck: () -> Unit,
    onMonitorLiveFeed: (RecordingItem) -> Unit,
    onAddChannel: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "MONITORING", "PAUSED", "LIVE"

    var channelUrlInput by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Inline Capsule-styled Add Channel Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = channelUrlInput,
                    onValueChange = { channelUrlInput = it },
                    placeholder = { Text("Paste YouTube channel URL...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        cursorColor = CyberBlue,
                        focusedContainerColor = CosmicSlate.copy(alpha = 0.3f),
                        unfocusedContainerColor = CosmicSlate.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("add_channel_inline_input"),
                    trailingIcon = {
                        Button(
                            onClick = {
                                if (channelUrlInput.isNotBlank()) {
                                    onAddChannel(channelUrlInput)
                                    channelUrlInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberBlue,
                                contentColor = CosmicBlack
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .height(40.dp)
                                .testTag("add_channel_inline_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Icon",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ADD",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                )
            }
            
            // 1. SYSTEM STATS BOARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSlate),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SYSTEM ENGINE METRICS",
                            color = CyberBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CyberGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ONLINE",
                                color = CyberGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Metric 1: Tracked Channels
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text("TRACKED", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            Text("${channels.size} channels", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                        
                        // Metric 2: Live Right Now
                        val liveCount = recordings.count { it.status == "RECORDING" }
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text("LIVE NOW", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (liveCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(GlowRed)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = "$liveCount active",
                                    color = if (liveCount > 0) GlowRed else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        
                        // Metric 3: Archives Completed
                        val archiveCount = recordings.count { it.status == "COMPLETED" }
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text("ARCHIVES", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            Text("$archiveCount files", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Storage Indicator Bar
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STORAGE (COMPLETED DOWNLOADS)",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "40.9 GB / 128 GB (32% Used)",
                                color = CyberBlue,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { 0.32f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = CyberBlue,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }
                }
            }

            // 2. SEARCH & FILTER SECTION
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSlate.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Search text box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter channels by name or handle...", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = 13.sp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search icon",
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            cursorColor = CyberBlue
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Filter row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val filters = listOf("ALL", "MONITORING", "PAUSED", "LIVE")
                        filters.forEach { filter ->
                            val isSelected = selectedFilter == filter
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) CyberBlue.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) CyberBlue else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedFilter = filter }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = filter,
                                    color = if (isSelected) CyberBlue else Color.White.copy(alpha = 0.6f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // 3. CHANNEL CARDS LIST
            // Filter and Search Logic
            val filteredChannels = channels.filter { channel ->
                val matchesSearch = channel.name.contains(searchQuery, ignoreCase = true) || 
                                    channel.handle.contains(searchQuery, ignoreCase = true)
                val matchesFilter = when (selectedFilter) {
                    "ALL" -> true
                    "MONITORING" -> channel.status == "MONITORING"
                    "PAUSED" -> channel.status == "PAUSED"
                    "LIVE" -> {
                        val activeRecording = recordings.any { it.channelId == channel.id && it.status == "RECORDING" }
                        activeRecording
                    }
                    else -> true
                }
                matchesSearch && matchesFilter
            }

            if (filteredChannels.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = CosmicSlate.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "No channels match",
                                tint = CyberBlue,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "NO CHANNELS FOUND",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Try adjusting your search query or filter settings.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    items(filteredChannels, key = { it.id }) { channel ->
                        val activeRecording = recordings.find { it.channelId == channel.id && it.status == "RECORDING" }
                        ChannelCard(
                            channel = channel,
                            activeRecording = activeRecording,
                            onToggleMonitoring = { onToggleMonitoring(channel) },
                            onDelete = { onDeleteChannel(channel.id) },
                            onSimulateLive = { onTriggerSimulateLive(channel.id) },
                            onForceCheck = onForceCheck,
                            onMonitorLiveFeed = onMonitorLiveFeed
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelCard(
    channel: MonitoredChannel,
    activeRecording: RecordingItem?,
    onToggleMonitoring: () -> Unit,
    onDelete: () -> Unit,
    onSimulateLive: () -> Unit,
    onForceCheck: () -> Unit,
    onMonitorLiveFeed: (RecordingItem) -> Unit
) {
    val badgeColor = when {
        activeRecording != null -> GlowRed
        channel.status == "MONITORING" -> CyberGreen
        channel.status == "PAUSED" -> SunsetOrange
        else -> SoftGrey
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CosmicSlate),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, if (activeRecording != null) GlowRed else Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // High-tech status indicator strip on the left edge
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(badgeColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar + Handle info
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CyberBlue.copy(alpha = 0.1f))
                                .border(1.dp, CyberBlue, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = channel.name.take(1).uppercase(),
                                color = CyberBlue,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = channel.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = channel.handle,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Monitoring State Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .border(1.dp, badgeColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (activeRecording != null) "🔴 LIVE REC" else channel.status,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // If active recording exists, show embedded stream metadata card
                if (activeRecording != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CosmicBlack.copy(alpha = 0.4f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "ACTIVE LIVE STREAM BEING CAPTURED",
                            color = CyberBlue,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeRecording.streamTitle,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Last checked and stream timings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "LAST CHECKED", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = if (channel.lastChecked > 0) formatTimestamp(channel.lastChecked) else "Never",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "LAST DETECTED STREAM", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = if (channel.lastStreamDetected > 0) formatTimestamp(channel.lastStreamDetected) else "None",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Toggle status
                    TextButton(
                        onClick = onToggleMonitoring,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (channel.status == "MONITORING") SunsetOrange else CyberGreen
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (channel.status == "MONITORING") Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Toggle Action",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (channel.status == "MONITORING") "PAUSE" else "RESUME",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Monitor Live Feed Button
                        if (activeRecording != null) {
                            Button(
                                onClick = { onMonitorLiveFeed(activeRecording) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue.copy(alpha = 0.15f), contentColor = CyberBlue),
                                border = BorderStroke(1.dp, CyberBlue.copy(alpha = 0.6f)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LiveTv,
                                        contentDescription = "Monitor Live",
                                        tint = CyberBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "MONITOR",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Real Check Now Button
                        if (channel.status == "MONITORING" && activeRecording == null) {
                            IconButton(
                                onClick = onForceCheck,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(CyberGreen.copy(alpha = 0.1f))
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Check Now",
                                    tint = CyberGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Quick Simulate Stream Button
                        if (channel.status == "MONITORING" && activeRecording == null) {
                            IconButton(
                                onClick = onSimulateLive,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(CyberBlue.copy(alpha = 0.1f))
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = "Simulate Stream Going Live",
                                    tint = CyberBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Delete button
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(GlowRed.copy(alpha = 0.1f))
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Channel",
                                tint = GlowRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


// ---------------------- TAB 2: DOWNLOADS ----------------------
@Composable
fun DownloadsTab(
    recordings: List<RecordingItem>,
    onGetClicked: (String, String) -> Unit,
    onPauseClicked: (Int) -> Unit,
    onStopClicked: (Int) -> Unit,
    onOpenClicked: (RecordingItem) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var streamTitleInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "RECORDING", "COMPLETED", "PAUSED"

    Column(modifier = Modifier.fillMaxSize()) {
        // paste url bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSlate),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "DOWNLOAD COMPLETED STREAM / DIRECT URL",
                    color = CyberBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("Paste YouTube video URL here...", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        cursorColor = CyberBlue
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("youtube_url_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = streamTitleInput,
                    onValueChange = { streamTitleInput = it },
                    placeholder = { Text("Title / Filename (optional)...", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        cursorColor = CyberBlue
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("stream_title_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (urlText.isNotBlank()) {
                            val title = streamTitleInput.ifBlank { "Direct URL Download" }
                            onGetClicked(urlText, title)
                            urlText = ""
                            streamTitleInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue, contentColor = CosmicBlack),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("download_get_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Download Icon", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GET DOWNLOAD", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }

        // Search & filter for recordings
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSlate.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search recorded archives by title...", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = 13.sp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        cursorColor = CyberBlue
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filters = listOf("ALL", "RECORDING", "COMPLETED", "PAUSED")
                    filters.forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) CyberBlue.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) CyberBlue else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = filter,
                                color = if (isSelected) CyberBlue else Color.White.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "RECORDINGS & ARCHIVES",
            color = CyberBlue,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        val filteredRecordings = recordings.filter { item ->
            val matchesSearch = item.streamTitle.contains(searchQuery, ignoreCase = true) || 
                                item.channelName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "ALL" -> true
                "RECORDING" -> item.status == "RECORDING"
                "COMPLETED" -> item.status == "COMPLETED"
                "PAUSED" -> item.status == "PAUSED"
                else -> true
            }
            matchesSearch && matchesFilter
        }

        if (filteredRecordings.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicSlate.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CyberBlue.copy(alpha = 0.1f))
                                .border(1.dp, CyberBlue.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "No recordings match",
                                tint = CyberBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "NO ARCHIVES DETECTED",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "No stream records match your criteria. Start monitoring channels or paste direct URLs to compile download archives.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRecordings, key = { it.id }) { item ->
                    RecordingCard(
                        item = item,
                        onPause = { onPauseClicked(item.id) },
                        onStop = { onStopClicked(item.id) },
                        onOpen = { onOpenClicked(item) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun RecordingCard(
    item: RecordingItem,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onOpen: () -> Unit
) {
    val stateColor = when (item.status) {
        "RECORDING" -> GlowRed
        "COMPLETED" -> CyberGreen
        "PAUSED" -> SunsetOrange
        "MISSED" -> GlowRed
        else -> SoftGrey
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CosmicSlate),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // High-tech status indicator strip on the left edge
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(stateColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // Header: Status Badge + Client fallback tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.status == "RECORDING") {
                            val pulseAlpha by rememberInfiniteTransition().animateFloat(
                                initialValue = 0.2f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .alpha(pulseAlpha)
                                    .background(GlowRed)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "REC",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(stateColor.copy(alpha = 0.15f))
                                    .border(1.dp, stateColor, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = item.status,
                                    color = stateColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Mode Tag: e.g. completed URL vs active live
                        val modeLabel = if (item.urlType == "COMPLETED") "ARCHIVE" else "LIVE"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = modeLabel,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Client fallback tag
                    Text(
                        text = "Engine: ${item.playerClient}",
                        color = CyberBlue,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title + Channel Info
                Text(
                    text = item.streamTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "Source: " + item.channelName,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Progress bar and info
                if (item.status == "RECORDING" || item.status == "PAUSED") {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (item.status == "RECORDING") CyberBlue else SunsetOrange,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Stats: size, duration, completed time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        Column {
                            Text(text = "SIZE", color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            Text(text = item.fileSize, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column {
                            Text(text = "DURATION", color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            Text(text = formatDuration(item.durationSeconds), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "TIMESTAMP", color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        Text(text = formatTimestamp(item.timestamp), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }

                // Playback and Control Actions
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Open partial stream or show error log
                    if (item.status == "CANCELLED" && !item.errorMessage.isNullOrEmpty()) {
                        var showErrorDialog by remember { mutableStateOf(false) }
                        if (showErrorDialog) {
                            AlertDialog(
                                onDismissRequest = { showErrorDialog = false },
                                title = { Text("YT-DLP ERROR DETAILS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = GlowRed, fontSize = 14.sp) },
                                text = {
                                    Column {
                                        Text(
                                            text = item.errorMessage,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = { showErrorDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberBlue)
                                    ) {
                                        Text("CLOSE", fontWeight = FontWeight.Bold, color = CosmicBlack)
                                    }
                                },
                                containerColor = CosmicSlate
                            )
                        }

                        Button(
                            onClick = { showErrorDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GlowRed.copy(alpha = 0.15f),
                                contentColor = GlowRed
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = "Error Info", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SHOW ERROR LOG", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    } else {
                        Button(
                            onClick = onOpen,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberBlue.copy(alpha = 0.15f),
                                contentColor = CyberBlue
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("OPEN PREVIEW", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    if (item.status == "RECORDING" || item.status == "PAUSED") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Pause/Resume button
                            IconButton(
                                onClick = onPause,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SunsetOrange.copy(alpha = 0.15f))
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.status == "RECORDING") Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Pause stream",
                                    tint = SunsetOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Stop/Cancel button
                            IconButton(
                                onClick = onStop,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GlowRed.copy(alpha = 0.15f))
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Cancel and save stream",
                                    tint = GlowRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// ---------------------- TAB 3: LOGS & DIAGS ----------------------
@Composable
fun LogsTab(
    logs: List<SystemLog>,
    onClearLogs: () -> Unit,
    onManualPoll: () -> Unit,
    onRestoreNetworkSimulation: () -> Unit,
    onUpdateEngine: () -> Unit,
    onResetEngine: () -> Unit,
    onExportLogs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        
        // Diagnostic actions card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSlate),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SIMULATION CONTROL BOARD",
                    color = CyberBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onManualPoll,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberBlue.copy(alpha = 0.15f), contentColor = CyberBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Poll", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("POLL CHANNELS", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Button(
                        onClick = onRestoreNetworkSimulation,
                        colors = ButtonDefaults.buttonColors(containerColor = SunsetOrange.copy(alpha = 0.15f), contentColor = SunsetOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Outlined.CompassCalibration, contentDescription = "Doze Simulation", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("MIUI NETWORK RESTORE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onUpdateEngine,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGreen.copy(alpha = 0.15f), contentColor = CyberGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Update yt-dlp", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("UPDATE YT-DLP ENGINE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onResetEngine,
                    colors = ButtonDefaults.buttonColors(containerColor = GlowRed.copy(alpha = 0.15f), contentColor = GlowRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Reset yt-dlp", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ROLLBACK / RESET ENGINE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Click 'MIUI NETWORK RESTORE' to trigger detection of missed streams inside the overnight battery doze outage window. Click 'UPDATE YT-DLP ENGINE' to update to Python 3.8-compatible yt-dlp (v2025.01.12) to bypass player client blocks. Click 'ROLLBACK / RESET ENGINE' to restore the highly stable, pre-bundled engine if anything breaks.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        // Logs terminal header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DIAGNOSTIC TELEMETRY LOGS",
                color = CyberBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SAVE/EXPORT (.TXT)",
                    color = CyberGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onExportLogs() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )

                Text(
                    text = "CLEAR ALL",
                    color = GlowRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onClearLogs() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        // Terminal style list
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF07090E))
                .border(1.dp, CyberBlue.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .drawBehind {
                    val scanlineSpacing = 16f
                    val lineCount = (size.height / scanlineSpacing).toInt()
                    for (i in 0..lineCount) {
                        val y = i * scanlineSpacing
                        drawLine(
                            color = CyberBlue.copy(alpha = 0.04f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2f
                        )
                    }
                }
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "System is idle. No diagnostics recorded yet.",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logs) { log ->
                        LogLineItem(log)
                    }
                }
            }
        }
    }
}

@Composable
fun LogLineItem(log: SystemLog) {
    val levelColor = when (log.level) {
        "ERROR" -> GlowRed
        "WARN" -> SunsetOrange
        else -> CyberGreen
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            // Time prefix
            Text(
                text = formatLogTime(log.timestamp),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(64.dp)
            )

            // Log Level badge
            Text(
                text = "[${log.level}]",
                color = levelColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(56.dp)
            )

            // Message text
            Text(
                text = log.message,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
        }
    }
}


// ---------------------- UTILITIES ----------------------
fun formatTimestamp(millis: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy - HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(millis))
}

fun formatLogTime(millis: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(millis))
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

// ---------------------- LIVE STREAM PLAYER MONITOR SIMULATION ----------------------

data class ChatMessage(
    val username: String,
    val message: String,
    val color: Color
)

@Composable
fun LiveMonitorPlayerDialog(
    recording: RecordingItem,
    onDismissRequest: () -> Unit
) {
    // Keep a list of active chat messages and append new ones periodically
    var chatMessages by remember {
        mutableStateOf(
            listOf(
                ChatMessage("@swatisharma5222", "Hello everyone!", Color(0xFFE91E63)),
                ChatMessage("@SyedAyyyan-k3j", "Let's go kG Empire! 🔥", Color(0xFF2196F3)),
                ChatMessage("@Vibha-y2u", "Nice gameplay bro", Color(0xFF009688))
            )
        )
    }

    val sampleChats = listOf(
        ChatMessage("@gamer_pro_99", "Wow, amazing skills! 🎮", Color(0xFF4CAF50)),
        ChatMessage("@live_chatter_hq", "Can you show the recording settings?", Color(0xFFFF9800)),
        ChatMessage("@lofi_miner", "Diamonds found! 💎", Color(0xFF03A9F4)),
        ChatMessage("@stream_fanatic", "What server is this?", Color(0xFFE91E63)),
        ChatMessage("@recorder_status", "HLS stream packet decryption complete.", CyberGreen),
        ChatMessage("@ytdlp_bot", "Chunk downloaded successfully.", CyberBlue),
        ChatMessage("@swatisharma5222", "This quality is awesome! 🌟", Color(0xFFE91E63)),
        ChatMessage("@SyedAyyyan-k3j", "Saved directly to local MP4! 💾", Color(0xFF2196F3)),
        ChatMessage("@Vibha-y2u", "No frame drops detected! 🚀", Color(0xFF009688))
    )

    // Append new chats every 2.5 seconds
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var index = 0
        while (true) {
            kotlinx.coroutines.delay(2500)
            val nextChat = sampleChats[index % sampleChats.size]
            chatMessages = chatMessages + nextChat
            if (chatMessages.size > 20) {
                chatMessages = chatMessages.drop(1)
            }
            index++
        }
    }

    // Interactive likes count
    var likesCount by remember { mutableStateOf(510) }
    var isLiked by remember { mutableStateOf(false) }

    // Stream duration counter
    var elapsedSeconds by remember { mutableStateOf(45) } // starting at 45 seconds for active feel
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds++
        }
    }

    // Layout representation of Youtube live interface
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue, contentColor = CosmicBlack)
            ) {
                Text("CLOSE MONITOR", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(GlowRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LIVE FEED ACTIVE MONITOR",
                    color = GlowRed,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
            ) {
                // 1. GAME SIMULATION CANVAS / PLAYER WINDOW
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Compose Canvas with beautiful Minecraft / Retro Gaming simulation!
                    val infiniteTransition = rememberInfiniteTransition()
                    
                    // Moving sun/clouds x offset
                    val sunXOffset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 200f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(10000, easing = androidx.compose.animation.core.LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    )

                    // Sword swinging animation
                    val swordRotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = -45f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )

                    // Blinking facecam mic icon
                    val micAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.2f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse
                        )
                    )

                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val w = size.width
                        val h = size.height

                        // Draw Sky
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF0D1B2A), Color(0xFF1B263B))
                            ),
                            size = size
                        )

                        // Draw Moving Sun (Retro Cyber style block)
                        val sunX = (40f + sunXOffset) % w
                        drawRect(
                            color = Color(0xFFFFCC00),
                            topLeft = Offset(sunX, h * 0.15f),
                            size = androidx.compose.ui.geometry.Size(20f, 20f)
                        )

                        // Draw Blocky Green Mountains/Terrain (Minecraft-like)
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, h)
                            lineTo(0f, h * 0.75f)
                            lineTo(w * 0.15f, h * 0.65f)
                            lineTo(w * 0.3f, h * 0.75f)
                            lineTo(w * 0.45f, h * 0.60f)
                            lineTo(w * 0.65f, h * 0.80f)
                            lineTo(w * 0.8f, h * 0.65f)
                            lineTo(w, h * 0.75f)
                            lineTo(w, h)
                            close()
                        }
                        drawPath(path = path, color = Color(0xFF386641))

                        // Draw a few blocky trees
                        drawRect(Color(0xFF6A4F3B), topLeft = Offset(w * 0.25f, h * 0.70f), size = androidx.compose.ui.geometry.Size(8f, 20f))
                        drawRect(Color(0xFF38b000), topLeft = Offset(w * 0.22f, h * 0.58f), size = androidx.compose.ui.geometry.Size(20f, 20f))

                        drawRect(Color(0xFF6A4F3B), topLeft = Offset(w * 0.75f, h * 0.62f), size = androidx.compose.ui.geometry.Size(8f, 25f))
                        drawRect(Color(0xFF38b000), topLeft = Offset(w * 0.72f, h * 0.48f), size = androidx.compose.ui.geometry.Size(22f, 22f))

                        // Draw Blocky Player hand & sword swinging (bottom right)
                        // Sword trunk
                        rotate(degrees = swordRotation, pivot = Offset(w - 20f, h - 20f)) {
                            // Draw blocky iron sword
                            drawRect(Color(0xFFE5E5E5), topLeft = Offset(w - 45f, h - 90f), size = androidx.compose.ui.geometry.Size(12f, 70f))
                            // crossguard
                            drawRect(Color(0xFF8D99AE), topLeft = Offset(w - 55f, h - 35f), size = androidx.compose.ui.geometry.Size(32f, 10f))
                            // hilt
                            drawRect(Color(0xFF4E5D6C), topLeft = Offset(w - 41f, h - 25f), size = androidx.compose.ui.geometry.Size(8f, 20f))
                        }
                    }

                    // BLINKING LIVE / REC OVERLAYS
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(650),
                                repeatMode = RepeatMode.Reverse
                            )
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(GlowRed)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "LIVE",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .alpha(pulseAlpha)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(1.dp, GlowRed, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(GlowRed)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "REC ${formatDuration(elapsedSeconds.toLong())}",
                                    color = GlowRed,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // RESOLUTION & ENCRYPTOR OVERLAY
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "1080p60 • yt-dlp",
                            color = CyberBlue,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // GAMER FACECAM OVERLAY (Bottom-Left)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .size(50.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E1E2E))
                            .border(1.dp, CyberBlue, RoundedCornerShape(6.dp))
                    ) {
                        // Drawing facecam avatar
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val cw = size.width
                            val ch = size.height
                            
                            // Draw face background circle
                            drawCircle(color = Color(0xFFC3A5EC), radius = cw * 0.35f, center = Offset(cw * 0.5f, ch * 0.52f))
                            
                            // Draw headphones
                            drawRect(CyberGreen, topLeft = Offset(cw * 0.1f, ch * 0.35f), size = androidx.compose.ui.geometry.Size(8f, 20f))
                            drawRect(CyberGreen, topLeft = Offset(cw * 0.75f, ch * 0.35f), size = androidx.compose.ui.geometry.Size(8f, 20f))
                            
                            // Headphones band
                            drawArc(
                                color = CyberGreen,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(cw * 0.1f, ch * 0.15f),
                                size = androidx.compose.ui.geometry.Size(cw * 0.75f, ch * 0.45f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                            )
                            
                            // Mic boom
                            drawLine(
                                color = CyberGreen,
                                start = Offset(cw * 0.2f, ch * 0.6f),
                                end = Offset(cw * 0.45f, ch * 0.75f),
                                strokeWidth = 3f
                            )
                        }

                        // Pulsing tiny mic badge inside facecam
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .alpha(micAlpha)
                                .background(CyberGreen)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. STREAM TITLE & CHANNEL METADATA
                Text(
                    text = recording.streamTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = recording.channelName,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "File size: ${recording.fileSize}",
                        color = CyberGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. STATS BAR (VIEWERS & LIKES)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CosmicSlate)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Viewers (eyeball count slightly animating)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Viewers",
                            tint = GlowRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // slightly fluctuating viewer count
                        val viewerFluctuation = (likesCount * 5.75 + (elapsedSeconds % 10) * 3).toInt()
                        Text(
                            text = "${String.format("%,d", viewerFluctuation)} watching",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Interactive Likes button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                if (isLiked) {
                                    likesCount--
                                    isLiked = false
                                } else {
                                    likesCount++
                                    isLiked = true
                                }
                            }
                            .background(if (isLiked) CyberBlue.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Like",
                            tint = if (isLiked) CyberBlue else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$likesCount likes",
                            color = if (isLiked) CyberBlue else Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. LIVE CHAT PANEL (Scrolling stream chat from screenshot)
                Text(
                    text = "LIVE CHAT FEED",
                    color = CyberBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CosmicBlack)
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(6.dp)
                ) {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    
                    // Auto scroll to bottom when new messages arrive
                    androidx.compose.runtime.LaunchedEffect(chatMessages.size) {
                        if (chatMessages.isNotEmpty()) {
                            listState.animateScrollToItem(chatMessages.size - 1)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(chatMessages) { chat ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = chat.username,
                                    color = chat.color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(110.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = chat.message,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 5. TECHNICAL ENCRYPTOR DECODER LOGS
                Text(
                    text = "DECODER STATUS: ACTIVE CAPTURE WITH SECURE PARSING",
                    color = CyberGreen.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        containerColor = CosmicSlate,
        shape = RoundedCornerShape(16.dp)
    )
}
