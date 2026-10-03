package com.universal.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universal.downloader.ui.theme.*
import com.universal.downloader.ui.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineSettingsScreen(
    viewModel: DownloadViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCookieLogin: () -> Unit
) {
    val engineStatus by viewModel.engineStatus.collectAsState()
    var hasCookies by remember { mutableStateOf(viewModel.hasCookies()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Engine & Core Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Engine Info Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorderDark, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (engineStatus.isCustomUpdated) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (engineStatus.isCustomUpdated) SuccessGreen else PrimaryCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "yt-dlp Extraction Core",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Installed Version: ${engineStatus.version}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Engine Type: ${if (engineStatus.isCustomUpdated) "Dynamic Wheel (OTA Injected)" else "Bundled Baseline"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Location: ${engineStatus.location}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { viewModel.checkForUpdates() },
                                modifier = Modifier.weight(1f),
                                enabled = !engineStatus.isUpdating,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                            ) {
                                if (engineStatus.isUpdating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black)
                                } else {
                                    Text("Check for Updates", color = Color.Black)
                                }
                            }

                            if (engineStatus.isCustomUpdated) {
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.resetEngine() },
                                    enabled = !engineStatus.isUpdating
                                ) {
                                    Text("Reset", color = WarningOrange)
                                }
                            }
                        }

                        if (engineStatus.updateAvailable && engineStatus.latestVersionAvailable != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = Color(0xFF1E3A2F),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "New Engine Available: v${engineStatus.latestVersionAvailable}",
                                        color = SuccessGreen,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Updates extractors for YouTube, Instagram, and fixes 403 Forbidden errors instantly without updating the APK.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            engineStatus.latestVersionAvailable?.let {
                                                viewModel.applyUpdate(it)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                    ) {
                                        Text("Download & Apply In-Memory Update", color = Color.Black)
                                    }
                                }
                            }
                        }

                        if (engineStatus.updateLog.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = Color(0xFF0F0F0F),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = engineStatus.updateLog,
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Authentication & Cookie Management
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorderDark, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Authentication & Cookies",
                            style = MaterialTheme.typography.titleMedium,
                            color = PrimaryCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Export cookies to access age-restricted videos, private playlists, and avoid YouTube rate limits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cookie Status: ",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (hasCookies) "Configured & Active" else "None",
                                color = if (hasCookies) SuccessGreen else Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onNavigateToCookieLogin,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
                            ) {
                                Text("Web Login / Refresh Cookies", color = PrimaryCyan)
                            }

                            if (hasCookies) {
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        viewModel.clearCookies()
                                        hasCookies = viewModel.hasCookies()
                                    }
                                ) {
                                    Text("Clear", color = ErrorRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
