package com.universaldownloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.universaldownloader.ui.navigation.AppNavigation
import com.universaldownloader.ui.theme.UniversalDownloaderTheme
import com.universaldownloader.ui.viewmodel.DownloadViewModel
import com.universaldownloader.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get application container
        val app = application as DownloaderApp

        // Initialize view models manually via custom provider factory
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(DownloadViewModel::class.java) -> {
                        DownloadViewModel(applicationContext, app.downloadRepository) as T
                    }
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                        SettingsViewModel(app.settingsRepository) as T
                    }
                    else -> throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }

        downloadViewModel = ViewModelProvider(this, factory)[DownloadViewModel::class.java]
        settingsViewModel = ViewModelProvider(this, factory)[SettingsViewModel::class.java]

        // Handle incoming share intents (e.g. YouTube "Share" -> choose this app)
        handleShareIntent(intent)

        setContent {
            UniversalDownloaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        downloadViewModel = downloadViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { handleShareIntent(it) }
    }

    private fun handleShareIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                downloadViewModel.onLinksTextChange(sharedText)
            }
        }
    }
}
