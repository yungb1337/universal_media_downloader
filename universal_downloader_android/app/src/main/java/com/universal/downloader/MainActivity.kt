package com.universal.downloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            Toast.makeText(this, "Permissions required to save downloads.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFE94560),
                    secondary = Color(0xFF533483),
                    background = Color(0xFF1A1A2E),
                    surface = Color(0xFF16213E),
                    onBackground = Color(0xFFEAEAEA),
                    onSurface = Color(0xFFEAEAEA)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: DownloadViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Queue", "Completed", "Settings & Logs")

    Column(modifier = Modifier.fillMaxSize()) {
        // App Bar
        SmallTopAppBar(
            title = {
                Text(
                    "🌐 Universal Downloader",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = Color(0xFF16213E)
            )
        )

        // Navigation Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF16213E),
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 14.sp) }
                )
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> QueueTab(viewModel)
                1 -> CompletedTab(viewModel)
                2 -> SettingsLogsTab(viewModel)
            }
        }
    }
}

@Composable
fun QueueTab(viewModel: DownloadViewModel) {
    var urlText by remember { mutableStateOf("") }
    var customNameText by remember { mutableStateOf("") }
    var isAddingLink by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Link Input Section
        if (isAddingLink) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text("Video URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customNameText,
                        onValueChange = { customNameText = it },
                        label = { Text("Custom Name (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { isAddingLink = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (urlText.isNotEmpty()) {
                                viewModel.addUrlToQueue(urlText, customNameText.ifEmpty { null })
                                urlText = ""
                                customNameText = ""
                                isAddingLink = false
                            }
                        }) {
                            Text("Add URL")
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { isAddingLink = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Link")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { viewModel.startQueueDownloads() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF57CC99)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run Queue")
                }
            }
        }

        // Active Queue List
        val activeQueue = viewModel.downloadQueue.filter { it.status != DownloadStatus.COMPLETED }
        
        if (activeQueue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No pending downloads in the queue.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(activeQueue) { item ->
                    QueueCard(item, viewModel)
                }
            }
        }
    }
}

@Composable
fun QueueCard(item: DownloadItem, viewModel: DownloadViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // URL / Title
            Text(
                text = item.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.url,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar (when downloading or paused)
            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PAUSED) {
                LinearProgressIndicator(
                    progress = item.progress / 100f,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${item.progress}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val downloadedFmt = formatBytes(item.downloadedBytes)
                    val totalFmt = formatBytes(item.totalBytes)
                    if (item.totalBytes > 0) {
                        Text("$downloadedFmt / $totalFmt", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Card Footer containing Status Label & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Label
                val (statusText, statusColor) = when (item.status) {
                    DownloadStatus.QUEUED -> "Queued" to Color.Gray
                    DownloadStatus.DOWNLOADING -> "Downloading..." to MaterialTheme.colorScheme.primary
                    DownloadStatus.PAUSED -> "Paused" to Color(0xFFF4A261)
                    DownloadStatus.FAILED -> "Failed" to Color(0xFFE63946)
                    else -> "" to Color.White
                }
                
                Column {
                    Text(statusText, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (item.status == DownloadStatus.FAILED && !item.error.isNullOrEmpty()) {
                        Text(item.error, color = Color(0xFFE63946), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Action Buttons
                Row {
                    if (item.status == DownloadStatus.DOWNLOADING) {
                        IconButton(onClick = { viewModel.pauseDownload(item.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Pause", tint = Color.White)
                        }
                    } else if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED) {
                        IconButton(onClick = { viewModel.startQueueDownloads() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry/Resume", tint = Color.Green)
                        }
                    }
                    IconButton(onClick = { viewModel.removeItem(item.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE63946))
                    }
                }
            }
        }
    }
}

@Composable
fun CompletedTab(viewModel: DownloadViewModel) {
    val completedList = viewModel.downloadQueue.filter { it.status == DownloadStatus.COMPLETED }

    if (completedList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No completed downloads yet.", color = Color.Gray)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(completedList) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111E3D)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF57CC99),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(formatBytes(item.totalBytes), fontSize = 12.sp, color = Color.Gray)
                        }
                        IconButton(onClick = { viewModel.removeItem(item.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsLogsTab(viewModel: DownloadViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Config fields
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = viewModel.downloadDir.value,
                    onValueChange = { viewModel.downloadDir.value = it },
                    label = { Text("Download Directory") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Highest Resolution", fontSize = 14.sp)
                    Switch(
                        checked = viewModel.highestRes.value,
                        onCheckedChange = { viewModel.highestRes.value = it }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Extract Audio Only (MP3)", fontSize = 14.sp)
                    Switch(
                        checked = viewModel.audioOnly.value,
                        onCheckedChange = { viewModel.audioOnly.value = it }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Logs terminal panel
        Text("Live Output Logs", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                items(viewModel.logs) { log ->
                    Text(
                        text = log,
                        color = Color(0xFFC0C0C0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return String.format("%.1f %s", size, units[unitIndex])
}
