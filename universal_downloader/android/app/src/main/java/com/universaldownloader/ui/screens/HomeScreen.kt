package com.universaldownloader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.universaldownloader.data.repository.DownloadSettings
import com.universaldownloader.ui.components.DownloadItemCard
import com.universaldownloader.ui.components.DownloadResultCard
import com.universaldownloader.ui.components.LogPanel
import com.universaldownloader.ui.components.PlaylistSelectionDialog
import com.universaldownloader.ui.theme.Accent
import com.universaldownloader.ui.theme.Background
import com.universaldownloader.ui.theme.Border
import com.universaldownloader.ui.theme.Error
import com.universaldownloader.ui.theme.Panel
import com.universaldownloader.ui.theme.Surface
import com.universaldownloader.ui.theme.TextPrimary
import com.universaldownloader.ui.theme.TextSecondary
import com.universaldownloader.ui.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DownloadViewModel,
    settings: DownloadSettings,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.sessionState.collectAsState()
    val linksText by viewModel.linksText.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val showPlaylistDialog by viewModel.showPlaylistDialog.collectAsState()
    val playlistEntries by viewModel.playlistEntries.collectAsState()

    if (showPlaylistDialog) {
        PlaylistSelectionDialog(
            entries = playlistEntries,
            onToggle = { viewModel.togglePlaylistEntry(it) },
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
            // URL Input card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(16.dp),
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
                                    "https://example.com/video1\nhttps://example.com/video2 MyCustomName",
                                    color = TextSecondary.copy(alpha = 0.5f)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Border,
                                focusedContainerColor = Background,
                                unfocusedContainerColor = Background,
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Action Buttons (Start / Analyze / Stop)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startDownload(settings) },
                                enabled = !state.isRunning && linksText.isNotBlank() && !isAnalyzing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Accent,
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
                                enabled = !state.isRunning && linksText.isNotBlank() && !isAnalyzing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Surface,
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

            // Real-time downloads list (if running)
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

            // Logs / Console panel
            item {
                LogPanel(
                    logEntries = state.logEntries,
                    onClear = { viewModel.clearLogs() }
                )
            }

            // Completed / Succeeded / Failed results list
            if (state.results.isNotEmpty()) {
                item {
                    Text(
                        text = "Download History",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                items(state.results) { result ->
                    DownloadResultCard(result = result)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
