package com.universaldownloader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.universaldownloader.data.model.DownloadProgress
import com.universaldownloader.data.model.DownloadResult
import com.universaldownloader.ui.theme.Background
import com.universaldownloader.ui.theme.Download
import com.universaldownloader.ui.theme.Error
import com.universaldownloader.ui.theme.Success
import com.universaldownloader.ui.theme.Surface
import com.universaldownloader.ui.theme.TextPrimary
import com.universaldownloader.ui.theme.TextSecondary
import com.universaldownloader.util.Formatters

/**
 * Card showing real-time download progress for a single file.
 * Port of the per-item progress display from desktop gui/widgets.py.
 */
@Composable
fun DownloadItemCard(
    progress: DownloadProgress,
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
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Filename
            Text(
                text = progress.filename.ifBlank { "Downloading..." },
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Download,
                trackColor = Background,
            )

            // Stats row: size, speed, ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Downloaded / Total
                Text(
                    text = buildString {
                        append(Formatters.formatFileSize(progress.downloadedBytes))
                        if (progress.totalBytes > 0) {
                            append(" / ")
                            append(Formatters.formatFileSize(progress.totalBytes))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                // Speed
                progress.speed?.let { speed ->
                    Text(
                        text = "${Formatters.formatFileSize(speed.toLong())}/s",
                        style = MaterialTheme.typography.bodySmall,
                        color = Download
                    )
                }

                // ETA
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
        else -> "❌" to Error
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
                        color = Error,
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
