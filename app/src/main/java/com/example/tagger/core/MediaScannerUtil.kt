package com.example.tagger.core

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "MediaScannerUtil"

/**
 * Utility class for notifying the system MediaScanner about new/modified media files.
 * This ensures files appear in music players and file managers.
 */
object MediaScannerUtil {

    /**
     * Scan a single file and notify the media library.
     * @param context Application context
     * @param filePath Absolute path to the file
     * @param mimeType MIME type of the file (e.g., "audio/mpeg", "audio/flac")
     * @return The scanned Uri, or null if scanning failed
     */
    suspend fun scanFile(
        context: Context,
        filePath: String,
        mimeType: String? = null
    ): Uri? = suspendCancellableCoroutine { continuation ->
        Log.d(TAG, "Scanning file: $filePath")

        MediaScannerConnection.scanFile(
            context,
            arrayOf(filePath),
            if (mimeType != null) arrayOf(mimeType) else null
        ) { path, uri ->
            Log.d(TAG, "Scan completed: path=$path, uri=$uri")
            if (continuation.isActive) {
                continuation.resume(uri)
            }
        }
    }

    /**
     * Scan multiple files.
     * @param context Application context
     * @param filePaths List of absolute file paths
     * @param mimeTypes Optional list of MIME types (must match filePaths length if provided)
     */
    fun scanFiles(
        context: Context,
        filePaths: List<String>,
        mimeTypes: List<String>? = null
    ) {
        Log.d(TAG, "Scanning ${filePaths.size} files")

        MediaScannerConnection.scanFile(
            context,
            filePaths.toTypedArray(),
            mimeTypes?.toTypedArray()
        ) { path, uri ->
            Log.d(TAG, "Scan completed: path=$path, uri=$uri")
        }
    }

    /**
     * Broadcast media scan for a file using Intent (alternative method).
     * Works better on some devices/Android versions.
     */
    @Suppress("DEPRECATION")
    fun broadcastScan(context: Context, file: File) {
        try {
            // Method 1: Direct scan
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(getMimeType(file.extension))
            ) { path, uri ->
                Log.d(TAG, "Broadcast scan completed: $path -> $uri")
            }

            // Method 2: Broadcast intent (deprecated but works on older devices)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = Uri.fromFile(file)
                context.sendBroadcast(intent)
                Log.d(TAG, "Sent MEDIA_SCANNER_SCAN_FILE broadcast")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast scan failed", e)
        }
    }

    /**
     * Get the public Music directory path.
     * Falls back to Downloads if Music is not available.
     */
    fun getMusicDirectory(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        return if (musicDir.exists() || musicDir.mkdirs()) {
            musicDir
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }
    }

    /**
     * Get the public Downloads directory path.
     */
    fun getDownloadsDirectory(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    /**
     * Get MIME type from file extension.
     */
    fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "m4a", "aac" -> "audio/mp4"
            "ogg", "oga" -> "audio/ogg"
            "wav" -> "audio/wav"
            "opus" -> "audio/opus"
            else -> "audio/*"
        }
    }
}
