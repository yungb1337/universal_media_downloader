package com.universal.downloader.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.universal.downloader.model.MediaFormatType
import com.universal.downloader.ui.theme.PrimaryCyan
import com.universal.downloader.ui.theme.SurfaceDark

@Composable
fun BatchLinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (urls: List<String>, format: MediaFormatType, resolution: String) -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf(MediaFormatType.VIDEO) }
    var selectedResolution by remember { mutableStateOf("1080p") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Batch Download Links",
                    style = MaterialTheme.typography.titleLarge,
                    color = PrimaryCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Paste multiple links (one per line). All valid links will be processed and queued.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    placeholder = { Text("https://youtube.com/watch?v=...\nhttps://youtu.be/...\nhttps://...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = Color.DarkGray
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedFormat == MediaFormatType.VIDEO,
                            onClick = { selectedFormat = MediaFormatType.VIDEO }
                        )
                        Text("Video", style = MaterialTheme.typography.bodyMedium)

                        Spacer(modifier = Modifier.width(8.dp))

                        RadioButton(
                            selected = selectedFormat == MediaFormatType.AUDIO,
                            onClick = { selectedFormat = MediaFormatType.AUDIO }
                        )
                        Text("Audio (MP3)", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val urls = rawText.lines()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("[DONE]") }
                            if (urls.isNotEmpty()) {
                                onConfirm(urls, selectedFormat, selectedResolution)
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                    ) {
                        Text("Queue All", color = Color.Black)
                    }
                }
            }
        }
    }
}
