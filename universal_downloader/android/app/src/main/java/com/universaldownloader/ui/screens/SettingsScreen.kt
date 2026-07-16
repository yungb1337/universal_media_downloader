package com.universaldownloader.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.universaldownloader.ui.components.NumericField
import com.universaldownloader.ui.components.PathField
import com.universaldownloader.ui.components.SettingsToggle
import com.universaldownloader.ui.theme.Background
import com.universaldownloader.ui.theme.Surface
import com.universaldownloader.ui.theme.TextPrimary
import com.universaldownloader.ui.theme.TextSecondary
import com.universaldownloader.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsState()
    val context = LocalContext.current

    // Directory picker for SAF target download directory
    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable permission
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)

            // Convert Uri to path representation (or simply store the Uri string)
            viewModel.updateDownloadDir(it.path ?: it.toString(), it.toString())
        }
    }

    // Document picker for cookies file (txt)
    val cookiesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            viewModel.updateCookiesFile(it.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Go back",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsToggle(
                            label = "Highest Res",
                            checked = settings.highestRes,
                            onToggle = { viewModel.toggleHighestRes(it) },
                            modifier = Modifier.weight(1f)
                        )

                        SettingsToggle(
                            label = "Audio Only",
                            checked = settings.audioOnly,
                            onToggle = { viewModel.toggleAudioOnly(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Concurrency & Retries
                    NumericField(
                        label = "Max Parallel Workers",
                        value = settings.maxConcurrent.toString(),
                        onValueChange = {
                            val parsed = it.toIntOrNull() ?: 1
                            viewModel.updateMaxConcurrent(parsed)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    NumericField(
                        label = "Max Retry Attempts",
                        value = settings.maxRetries.toString(),
                        onValueChange = {
                            val parsed = it.toIntOrNull() ?: 3
                            viewModel.updateMaxRetries(parsed)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Download folder (SAF)
                    PathField(
                        label = "Download Folder",
                        value = settings.downloadDir,
                        onClick = { dirPickerLauncher.launch(null) },
                        placeholder = "App-specific external storage (Default)"
                    )

                    // Cookies File (SAF)
                    PathField(
                        label = "Cookies File",
                        value = settings.cookiesFile,
                        onClick = { cookiesPickerLauncher.launch(arrayOf("text/plain")) },
                        placeholder = "Import cookies.txt (optional)"
                    )
                }
            }
        }
    }
}
