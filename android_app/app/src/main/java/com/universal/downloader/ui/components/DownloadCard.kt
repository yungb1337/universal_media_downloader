package com.universal.downloader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universal.downloader.model.DownloadItem
import com.universal.downloader.model.DownloadStatus
import com.universal.downloader.model.MediaFormatType
import com.universal.downloader.ui.theme.CardBorderDark
import com.universal.downloader.ui.theme.ErrorRed
import com.universal.downloader.ui.theme.PrimaryCyan
import com.universal.downloader.ui.theme.SuccessGreen
import com.universal.downloader.ui.theme.SurfaceDark
import com.universal.downloader.ui.theme.WarningOrange

@Composable
fun DownloadCard(
    item: DownloadItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, CardBorderDark, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge for Format
                Surface(
                    color = if (item.formatType == MediaFormatType.AUDIO) Color(0xFF9C27B0) else PrimaryCyan,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (item.formatType == MediaFormatType.AUDIO) "AUDIO MP3" else "VIDEO ${item.resolution}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status chip
                val (statusColor, statusText) = when (item.status) {
                    DownloadStatus.QUEUED -> Pair(WarningOrange, "Queued")
                    DownloadStatus.PROBING -> Pair(PrimaryCyan, "Probing")
                    DownloadStatus.DOWNLOADING -> Pair(PrimaryCyan, "${item.progress.toInt()}%")
                    DownloadStatus.PROCESSING -> Pair(PrimaryCyan, "Finalizing")
                    DownloadStatus.COMPLETED -> Pair(SuccessGreen, "Done")
                    DownloadStatus.FAILED -> Pair(ErrorRed, "Failed")
                    DownloadStatus.CANCELLED -> Pair(Color.Gray, "Cancelled")
                }

                Text(
                    text = statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.weight(1f))

                // Actions
                when (item.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.PROBING -> {
                        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Gray)
                        }
                    }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = PrimaryCyan)
                        }
                        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Gray)
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        IconButton(onClick = onOpen, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Open", tint = SuccessGreen)
                        }
                        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Gray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (item.uploader.isNotEmpty()) {
                Text(
                    text = item.uploader,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = item.status == DownloadStatus.DOWNLOADING) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = PrimaryCyan,
                        trackColor = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val downloadedMb = item.downloadedBytes / (1024f * 1024f)
                        val totalMb = item.totalBytes / (1024f * 1024f)
                        val speedMb = item.downloadSpeedBytesPerSec / (1024f * 1024f)

                        Text(
                            text = if (totalMb > 0) String.format("%.1f MB / %.1f MB", downloadedMb, totalMb) else String.format("%.1f MB", downloadedMb),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray
                        )
                        Text(
                            text = String.format("%.1f MB/s", speedMb),
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryCyan
                        )
                    }
                }
            }

            if (item.errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = ErrorRed,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
