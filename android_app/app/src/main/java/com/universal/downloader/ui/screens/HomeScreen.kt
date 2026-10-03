package com.universal.downloader.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.universal.downloader.model.DownloadStatus
import com.universal.downloader.model.MediaFormatType
import com.universal.downloader.ui.components.BatchLinkDialog
import com.universal.downloader.ui.components.DownloadCard
import com.universal.downloader.ui.theme.*
import com.universal.downloader.ui.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DownloadViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val inputUrl by viewModel.inputUrl.collectAsState()
    val isProbing by viewModel.isProbing.collectAsState()
    val probedInfo by viewModel.probedInfo.collectAsState()
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val selectedResolution by viewModel.selectedResolution.collectAsState()
    val downloads by viewModel.downloads.collectAsState()

    var showBatchDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Universal Downloader", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = { showBatchDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Batch Download", tint = PrimaryCyan)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Engine Settings", tint = Color.LightGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // URL Input Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorderDark, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { viewModel.setInputUrl(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Paste video, playlist, or song URL...") },
                        singleLine = true,
                        trailingIcon = {
                            if (inputUrl.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setInputUrl("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            } else {
                                IconButton(onClick = {
                                    clipboardManager.getText()?.text?.let { text ->
                                        viewModel.setInputUrl(text)
                                    }
                                }) {
                                    Icon(Icons.Default.Search, contentDescription = "Paste", tint = PrimaryCyan)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = CardBorderDark
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Format toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = selectedFormat == MediaFormatType.VIDEO,
                                onClick = { viewModel.setSelectedFormat(MediaFormatType.VIDEO) },
                                label = { Text("Video") }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = selectedFormat == MediaFormatType.AUDIO,
                                onClick = { viewModel.setSelectedFormat(MediaFormatType.AUDIO) },
                                label = { Text("Audio (MP3)") }
                            )
                        }

                        Button(
                            onClick = { viewModel.probeCurrentUrl() },
                            enabled = inputUrl.isNotBlank() && !isProbing,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                        ) {
                            if (isProbing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black)
                            } else {
                                Text("Analyze", color = Color.Black)
                            }
                        }
                    }
                }
            }

            // Probe Result Card
            AnimatedVisibility(visible = probedInfo != null) {
                probedInfo?.let { probe ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .border(1.dp, PrimaryCyan, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = probe.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryCyan
                            )
                            if (probe.uploader.isNotEmpty()) {
                                Text(
                                    text = "Channel: ${probe.uploader}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.LightGray
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectedFormat == MediaFormatType.VIDEO && probe.availableResolutions.isNotEmpty()) {
                                    var expanded by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(onClick = { expanded = true }) {
                                            Text("Quality: $selectedResolution")
                                        }
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            probe.availableResolutions.forEach { res ->
                                                DropdownMenuItem(
                                                    text = { Text(res) },
                                                    onClick = {
                                                        viewModel.setSelectedResolution(res)
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text("High Quality MP3 (192 kbps)", style = MaterialTheme.typography.labelSmall)
                                }

                                Button(
                                    onClick = { viewModel.startDownloadWithCurrentProbe() },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Downloads List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Download Tasks (${downloads.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active or past downloads.\nPaste a link above or tap '+' for batch download.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(downloads, key = { it.id }) { item ->
                        DownloadCard(
                            item = item,
                            onCancel = { viewModel.cancelDownload(item.id) },
                            onRetry = { viewModel.retryDownload(item.id) },
                            onRemove = { viewModel.removeDownload(item.id) },
                            onOpen = {
                                item.outputPath?.let { uriStr ->
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(
                                                Uri.parse(uriStr),
                                                if (item.formatType == MediaFormatType.AUDIO) "audio/*" else "video/*"
                                            )
                                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Fallback open gallery
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showBatchDialog) {
        BatchLinkDialog(
            onDismiss = { showBatchDialog = false },
            onConfirm = { urls, format, resolution ->
                viewModel.addDirectUrls(urls, format, resolution)
            }
        )
    }
}
