package com.universal.downloader.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.universal.downloader.model.MediaFormatType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MediaStoreManager(private val context: Context) {

    suspend fun saveMediaToPublicGallery(
        sourceFile: File,
        title: String,
        formatType: MediaFormatType
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                return@withContext Result.failure(Exception("Source file does not exist or is empty"))
            }

            val filename = sourceFile.name
            val isAudio = formatType == MediaFormatType.AUDIO || filename.endsWith(".mp3", true) || filename.endsWith(".m4a", true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collectionUri = if (isAudio) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val relativeDir = if (isAudio) "Music/UniversalDownloader" else "Movies/UniversalDownloader"
                val mimeType = if (isAudio) "audio/mpeg" else "video/mp4"

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.TITLE, title)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val itemUri = resolver.insert(collectionUri, contentValues)
                    ?: throw Exception("Failed to create MediaStore entry")

                try {
                    resolver.openOutputStream(itemUri).use { outStream ->
                        if (outStream == null) throw Exception("Failed to open output stream")
                        FileInputStream(sourceFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }

                    // Release pending flag so other media players can access it immediately
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)

                    // Clean up source file from private cache
                    sourceFile.delete()

                    Result.success(itemUri)
                } catch (e: Exception) {
                    resolver.delete(itemUri, null, null)
                    throw e
                }
            } else {
                // Legacy Android 9 and lower
                val baseDir = if (isAudio) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                } else {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                }
                val appDir = File(baseDir, "UniversalDownloader").apply { mkdirs() }
                val targetFile = File(appDir, filename)

                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                sourceFile.delete()

                val uri = Uri.fromFile(targetFile)
                Result.success(uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving media to public storage", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "MediaStoreManager"
    }
}
