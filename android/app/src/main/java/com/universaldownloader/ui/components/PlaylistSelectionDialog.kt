package com.universaldownloader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.universaldownloader.data.model.PlaylistEntry
import com.universaldownloader.ui.theme.Accent
import com.universaldownloader.ui.theme.Surface
import com.universaldownloader.ui.theme.TextPrimary
import com.universaldownloader.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelectionDialog(
    entries: List<PlaylistEntry>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = Accent)) {
                Text("Download Selected (${entries.count { it.isSelected }})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                Text("Cancel")
            }
        },
        title = {
            Text("Select Videos to Download", color = TextPrimary)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(entries) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(entry.url) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = entry.isSelected,
                                onCheckedChange = { onToggle(entry.url) },
                                colors = CheckboxDefaults.colors(checkedColor = Accent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry.title,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        containerColor = Surface
    )
}
