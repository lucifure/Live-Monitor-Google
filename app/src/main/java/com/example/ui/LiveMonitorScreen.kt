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
    val channels by viewModel.channels.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isServiceBound by viewModel.isServiceBound.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var showAddChannelDialog by remember { mutableStateOf(false) }
    var stopRecordingIdDialog by remember { mutableStateOf<Int?>(null) }
    var activePlaybackRecording by remember { mutableStateOf<RecordingItem?>(null) }

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
                    onToggleMonitoring = { viewModel.toggleChannelStatus(it) },
                    onDeleteChannel = { viewModel.deleteChannel(it) },
                    onTriggerSimulateLive = { viewModel.simulateStream(it) },
                    onForceCheck = { viewModel.manuallyPoll() },
                    onAddChannelClicked = { showAddChannelDialog = true }
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
                    onRestoreNetworkSimulation = { viewModel.simulateNetworkRestore() }
                )
            }
        }
    }

    // Add Channel Dialog
    if (showAddChannelDialog) {
        var nameInput by remember { mutableStateOf("") }
        var handleInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddChannelDialog = false },
            title = {
                Text(
                    text = "Add YouTube Channel",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column {
                    Text(
                        text = "Paste a full YouTube channel URL (e.g. https://youtube.com/@lofigirl) or type a handle. The app will automatically extract the handle and attempt to resolve the channel name from YouTube.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = handleInput,
                        onValueChange = { handleInput = it },
                        label = { Text("YouTube URL or @Handle (Required)", color = CyberBlue) },
                        placeholder = { Text("e.g. @lofigirl or channel link", color = Color.White.copy(alpha = 0.3f)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = CyberBlue
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("add_channel_handle_input")
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Custom Channel Name (Optional)", color = Color.White.copy(alpha = 0.6f)) },
                        placeholder = { Text("Left blank to auto-detect", color = Color.White.copy(alpha = 0.3f)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = CyberBlue
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_channel_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (handleInput.isNotBlank()) {
                            viewModel.addChannel(nameInput, handleInput)
                            showAddChannelDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue, contentColor = CosmicBlack)
                ) {
                    Text("ADD", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddChannelDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.6f))
                ) {
                    Text("CANCEL")
                }
            },
            containerColor = CosmicSlate,
            shape = RoundedCornerShape(16.dp)
        )
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
    onToggleMonitoring: (MonitoredChannel) -> Unit,
    onDeleteChannel: (Int) -> Unit,
    onTriggerSimulateLive: (Int) -> Unit,
    onForceCheck: () -> Unit,
    onAddChannelClicked: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (channels.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "No channels",
                                tint = CyberBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "NO ACTIVE MONITORS",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Add YouTube channels using the floating action button below to start tracking.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "ACTIVE MONITORS",
                    color = CyberBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(channels, key = { it.id }) { channel ->
                        ChannelCard(
                            channel = channel,
                            onToggleMonitoring = { onToggleMonitoring(channel) },
                            onDelete = { onDeleteChannel(channel.id) },
                            onSimulateLive = { onTriggerSimulateLive(channel.id) },
                            onForceCheck = onForceCheck
                        )
                    }
                    
                    // Extra spacing for floating action button
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // Floating Action Button to Add Channels
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Button(
                onClick = onAddChannelClicked,
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue, contentColor = CosmicBlack),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .height(56.dp)
                    .testTag("add_channel_fab")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Icon", modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ADD CHANNEL", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun ChannelCard(
    channel: MonitoredChannel,
    onToggleMonitoring: () -> Unit,
    onDelete: () -> Unit,
    onSimulateLive: () -> Unit,
    onForceCheck: () -> Unit
) {
    val badgeColor = when (channel.status) {
        "MONITORING" -> CyberGreen
        "PAUSED" -> SunsetOrange
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
                            text = channel.status,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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

                    Row {
                        // Real Check Now Button
                        if (channel.status == "MONITORING") {
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
                        if (channel.status == "MONITORING") {
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

    Column(modifier = Modifier.fillMaxSize()) {
        // paste url bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSlate)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DOWNLOAD COMPLETED STREAM",
                    color = CyberBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("Paste YouTube video URL here...", color = Color.White.copy(alpha = 0.4f)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        cursorColor = CyberBlue
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("youtube_url_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = streamTitleInput,
                    onValueChange = { streamTitleInput = it },
                    placeholder = { Text("Title / Filename (optional)...", color = Color.White.copy(alpha = 0.4f)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        cursorColor = CyberBlue
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_title_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

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
                        .height(44.dp)
                        .testTag("download_get_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Download Icon", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GET DOWNLOAD", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }

        Text(
            text = "RECORDINGS & ARCHIVES",
            color = CyberBlue,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        if (recordings.isEmpty()) {
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
                                contentDescription = "No downloads",
                                tint = CyberBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "NO STREAM ARCHIVES",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "No active or completed stream recordings found. Active YouTube streams will automatically appear here once detected.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
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
                items(recordings, key = { it.id }) { item ->
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
                    // Open partial stream
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
    onRestoreNetworkSimulation: () -> Unit
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

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Click 'MIUI NETWORK RESTORE' to trigger detection of missed streams inside the overnight battery doze outage window. This verifies the custom search scan and logs 'MISSED STREAM DETECTED' with instant auto-download recovery.",
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
