package com.universaldownloader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.universaldownloader.data.model.DownloadProgress
import com.universaldownloader.data.model.DownloadResult
import com.universaldownloader.data.model.DownloadStatus
import com.universaldownloader.ui.theme.*
import com.universaldownloader.util.Formatters

/**
 * Card showing real-time download progress for a single file with controls.
 */
@Composable
fun DownloadItemCard(
    progress: DownloadProgress,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onStop: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (progress.totalBytes > 0) {
            (progress.downloadedBytes.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
        } else 0f,
        label = "progress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (progress.status == DownloadStatus.PAUSED) 
                Surface.copy(alpha = 0.6f) else Surface
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Filename
                Text(
                    text = progress.filename.ifBlank { "Downloading..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (progress.status == DownloadStatus.PAUSED) 
                        TextPrimary.copy(alpha = 0.6f) else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Controls
                Row {
                    if (progress.status == DownloadStatus.PAUSED) {
                        IconButton(onClick = { onResume(progress.url) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = Accent)
                        }
                    } else {
                        IconButton(onClick = { onPause(progress.url) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Accent)
                        }
                    }
                    IconButton(onClick = { onStop(progress.url) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop & Delete", tint = com.universaldownloader.ui.theme.Error)
                    }
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (progress.status == DownloadStatus.PAUSED) 
                    Panel else Download,
                trackColor = Background,
            )

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = buildString {
                        append(Formatters.formatFileSize(progress.downloadedBytes))
                        if (progress.totalBytes > 0) {
                            append(" / ")
                            append(Formatters.formatFileSize(progress.totalBytes))
                        }
                        if (progress.status == DownloadStatus.PAUSED) {
                            append(" (Paused)")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                if (progress.status != DownloadStatus.PAUSED) {
                    progress.speed?.let { speed ->
                        Text(
                            text = "${Formatters.formatFileSize(speed.toLong())}/s",
                            style = MaterialTheme.typography.bodySmall,
                            color = Download
                        )
                    }
                    progress.eta?.let { eta ->
                        Text(
                            text = "ETA: ${Formatters.formatDuration(eta.toDouble())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card showing a completed download result (success, failure, or skipped).
 */
@Composable
fun DownloadResultCard(
    result: DownloadResult,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when {
        result.skipped -> "⏭️" to TextSecondary
        result.success -> "✅" to Success
        else -> "❌" to com.universaldownloader.ui.theme.Error
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = icon, style = MaterialTheme.typography.titleMedium)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title ?: result.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!result.success && result.error != null) {
                    Text(
                        text = result.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = com.universaldownloader.ui.theme.Error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (result.success && result.fileSizeBytes > 0) {
                    Text(
                        text = Formatters.formatFileSize(result.fileSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
