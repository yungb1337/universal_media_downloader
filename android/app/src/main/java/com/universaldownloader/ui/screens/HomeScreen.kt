package com.universaldownloader.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.universaldownloader.data.database.DownloadHistoryEntity
import com.universaldownloader.data.repository.DownloadSettings
import com.universaldownloader.ui.components.DownloadItemCard
import com.universaldownloader.ui.components.LogPanel
import com.universaldownloader.ui.components.PlaylistSelectionDialog
import com.universaldownloader.ui.theme.*
import com.universaldownloader.ui.viewmodel.DownloadViewModel
import com.universaldownloader.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DownloadViewModel,
    settings: DownloadSettings,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.sessionState.collectAsState()
    val history by viewModel.downloadHistory.collectAsState()
    val linksText by viewModel.linksText.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val showPlaylistDialog by viewModel.showPlaylistDialog.collectAsState()
    val playlistEntries by viewModel.playlistEntries.collectAsState()
    val selectedFormats by viewModel.selectedFormats.collectAsState()
    val availableSpace by viewModel.availableSpace.collectAsState()
    val clipboardText by viewModel.clipboardText.collectAsState()

    if (showPlaylistDialog) {
        PlaylistSelectionDialog(
            entries = playlistEntries,
            selectedFormats = selectedFormats,
            onToggle = { viewModel.togglePlaylistEntry(it) },
            onFormatSelected = { url, formatId -> viewModel.setFormatForUrl(url, formatId) },
            onSelectAll = { viewModel.setAllPlaylistSelection(true) },
            onDeselectAll = { viewModel.setAllPlaylistSelection(false) },
            onConfirm = { viewModel.startDownload(settings) },
            onDismiss = { viewModel.dismissPlaylistDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Universal Downloader", color = TextPrimary) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background
                )
            )
        },
        containerColor = Background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Clipboard Suggestion
            if (clipboardText != null && !linksText.contains(clipboardText!!)) {
                item {
                    ClipboardSuggestion(
                        text = clipboardText!!,
                        onPaste = { viewModel.onLinksTextChange(linksText + (if (linksText.isNotEmpty()) "\n" else "") + clipboardText) }
                    )
                }
            }

            // URL Input card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Enter URLs (one per line)",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            IconButton(onClick = { viewModel.refreshLinksFromDisk() }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh from disk",
                                    tint = TextSecondary
                                )
                            }
                        }

                        OutlinedTextField(
                            value = linksText,
                            onValueChange = { viewModel.onLinksTextChange(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                            placeholder = {
                                Text(
                                    "Paste links here...",
                                    color = TextSecondary.copy(alpha = 0.5f)
                                )
                            },
                            trailingIcon = {
                                if (linksText.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onLinksTextChange("") }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear all",
                                            tint = Accent.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Border,
                                focusedContainerColor = Background,
                                unfocusedContainerColor = Background,
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )

                        // Storage Warning
                        Text(
                            text = "Free Space: ${Formatters.formatFileSize(availableSpace)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (availableSpace < 500 * 1024 * 1024) Error else TextSecondary
                        )

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startDownload(settings) },
                                enabled = !state.isRunning && linksText.isNotBlank() && !isAnalyzing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Accent,
                                    contentColor = Background,
                                    disabledContainerColor = Panel
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Download",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.analyzePlaylist(settings) },
                                enabled = !state.isRunning && !isAnalyzing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Surface,
                                    disabledContainerColor = Panel
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier
                                    .weight(0.8f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isAnalyzing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Accent,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = Accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Analyze",
                                            color = TextPrimary,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.startDownload(settings, forceAudio = true) },
                                enabled = !state.isRunning && linksText.isNotBlank() && !isAnalyzing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Surface,
                                    disabledContainerColor = Panel
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier
                                    .weight(0.8f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Audio",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.stopDownload() },
                                enabled = state.isRunning,
                                modifier = Modifier
                                    .height(48.dp)
                                    .padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = if (state.isRunning) Error else Panel
                                )
                            }
                        }
                    }
                }
            }

            // Real-time downloads list
            if (state.currentProgress.isNotEmpty()) {
                item {
                    Text(
                        text = "Active Downloads",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                items(state.currentProgress.values.toList()) { progress ->
                    DownloadItemCard(progress = progress)
                }
            }

            // Download History
            if (history.isNotEmpty()) {
                item {
                    Text(
                        text = "Download History",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                items(history) { entry ->
                    HistoryItemCard(
                        entry = entry,
                        onPlay = { viewModel.playFile(it) },
                        onDelete = { viewModel.deleteHistoryEntry(it) }
                    )
                }
            }

            // Logs / Console panel
            item {
                LogPanel(
                    logEntries = state.logEntries,
                    onClear = { viewModel.clearLogs() }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun ClipboardSuggestion(text: String, onPaste: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Accent)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Link detected in clipboard", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(
                onClick = onPaste,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Paste", color = Background, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    entry: DownloadHistoryEntity,
    onPlay: (String) -> Unit,
    onDelete: (DownloadHistoryEntity) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (entry.thumbnailUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(entry.thumbnailUri),
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp, 56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp, 56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Panel),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = TextSecondary)
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entry.quality} • ${Formatters.formatFileSize(entry.fileSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            
            Row {
                if (entry.filePath != null) {
                    IconButton(onClick = { onPlay(entry.filePath) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Accent)
                    }
                }
                IconButton(onClick = { onDelete(entry) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Error.copy(alpha = 0.7f))
                }
            }
        }
    }
}
