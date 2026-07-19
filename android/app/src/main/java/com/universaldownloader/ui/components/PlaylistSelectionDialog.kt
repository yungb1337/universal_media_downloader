package com.universaldownloader.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.universaldownloader.data.model.PlaylistEntry
import com.universaldownloader.util.Formatters
import com.universaldownloader.ui.theme.Accent
import com.universaldownloader.ui.theme.Panel
import com.universaldownloader.ui.theme.Surface
import com.universaldownloader.ui.theme.TextPrimary
import com.universaldownloader.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelectionDialog(
    entries: List<PlaylistEntry>,
    selectedFormats: Map<String, String>,
    onToggle: (String) -> Unit,
    onFormatSelected: (String, String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Download (${entries.count { it.isSelected }})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        title = {
            Text("Analysis Results", color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (entries.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = onSelectAll, modifier = Modifier.weight(1f)) {
                            Text("Select All", color = Accent)
                        }
                        TextButton(onClick = onDeselectAll, modifier = Modifier.weight(1f)) {
                            Text("Deselect All", color = Accent)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(entries) { entry ->
                        PlaylistEntryItem(
                            entry = entry,
                            selectedFormat = selectedFormats[entry.url] ?: "auto",
                            onToggle = { onToggle(entry.url) },
                            onFormatSelected = { onFormatSelected(entry.url, it) }
                        )
                    }
                }
            }
        },
        containerColor = Surface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun PlaylistEntryItem(
    entry: PlaylistEntry,
    selectedFormat: String,
    onToggle: () -> Unit,
    onFormatSelected: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = entry.isSelected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = Accent)
                )
                
                if (entry.thumbnail != null) {
                    Image(
                        painter = rememberAsyncImagePainter(entry.thumbnail),
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp, 45.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Surface),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Text(
                    text = entry.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            
            if (entry.isSelected && entry.formats.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FormatSelector(
                    formats = entry.formats,
                    selectedFormat = selectedFormat,
                    onFormatSelected = onFormatSelected
                )
            }
        }
    }
}

@Composable
fun FormatSelector(
    formats: List<com.universaldownloader.data.model.VideoFormat>,
    selectedFormat: String,
    onFormatSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentFormat = formats.find { it.formatId == selectedFormat }
    
    val videoFormats = formats.filter { it.type == "video" }
    val audioFormats = formats.filter { it.type == "audio" }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayText = when {
                    selectedFormat == "auto" -> "⭐ Auto (Best Video)"
                    selectedFormat == "bestaudio" -> "🎵 Audio Only (Highest)"
                    currentFormat == null -> "Auto Quality"
                    currentFormat.type == "video" -> "🎬 ${currentFormat.resolution} (${currentFormat.ext})"
                    else -> "🎵 Audio (${currentFormat.ext})"
                }
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Accent)
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Surface).heightIn(max = 300.dp)
        ) {
            DropdownMenuItem(
                text = { Text("⭐ Auto (Best Video)", color = TextPrimary) },
                onClick = {
                    onFormatSelected("auto")
                    expanded = false
                }
            )

            DropdownMenuItem(
                text = { Text("🎵 Audio Only (Highest)", color = Accent, fontWeight = FontWeight.Bold) },
                onClick = {
                    onFormatSelected("bestaudio")
                    expanded = false
                }
            )
            
            if (videoFormats.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("--- 🎬 VIDEO ---", style = MaterialTheme.typography.labelSmall, color = Accent) },
                    onClick = {},
                    enabled = false
                )
                videoFormats.forEach { format ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(format.resolution, color = TextPrimary, modifier = Modifier.weight(1f))
                                if (format.fileSize > 0) {
                                    Text(
                                        Formatters.formatFileSize(format.fileSize),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        },
                        onClick = {
                            onFormatSelected(format.formatId)
                            expanded = false
                        }
                    )
                }
            }

            if (audioFormats.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("--- 🎵 AUDIO ONLY ---", style = MaterialTheme.typography.labelSmall, color = Accent) },
                    onClick = {},
                    enabled = false
                )
                audioFormats.forEach { format ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Audio (${format.ext})", color = TextPrimary, modifier = Modifier.weight(1f))
                                if (format.fileSize > 0) {
                                    Text(
                                        Formatters.formatFileSize(format.fileSize),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        },
                        onClick = {
                            onFormatSelected(format.formatId)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
