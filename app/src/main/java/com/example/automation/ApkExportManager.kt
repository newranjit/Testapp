package com.example.automation

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ApkExportManager {

    private const val TAG = "ApkExportManager"
    const val APK_FILE_NAME = "AgentFlow.apk"

    /**
     * Extracts the running app's APK and saves it directly to the emulator's Downloads storage.
     * Accessible in the Files/Downloads app inside the emulator.
     */
    fun saveApkToEmulatorStorage(context: Context): Pair<Boolean, String> {
        return try {
            val sourceApkPath = context.applicationInfo.sourceDir
            val sourceFile = File(sourceApkPath)
            if (!sourceFile.exists()) {
                return Pair(false, "Source APK not found at $sourceApkPath")
            }

            var savedPath: String? = null

            // 1. Save to App-specific external Downloads directory (always writable without permissions)
            val appExternalDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (appExternalDownloads != null) {
                val targetFile = File(appExternalDownloads, APK_FILE_NAME)
                copyFile(sourceFile, targetFile)
                savedPath = targetFile.absolutePath
            }

            // 2. Also save to standard public Downloads folder
            try {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!publicDownloads.exists()) {
                    publicDownloads.mkdirs()
                }
                val publicTarget = File(publicDownloads, APK_FILE_NAME)
                copyFile(sourceFile, publicTarget)
                savedPath = publicTarget.absolutePath
            } catch (e: Exception) {
                Log.w(TAG, "Public downloads copy: ${e.message}")
            }

            // 3. For Android 10+ (API 29+), also insert into MediaStore Downloads collection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, APK_FILE_NAME)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            FileInputStream(sourceFile).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore insert: ${e.message}")
                }
            }

            Pair(true, savedPath ?: "Saved AgentFlow.apk to Downloads")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save APK to emulator storage", e)
            Pair(false, "Failed to export APK: ${e.message}")
        }
    }

    /**
     * Prepares the APK and launches an email/share intent with the .apk file attached
     * pre-addressed to the user's email.
     */
    fun shareOrEmailApk(context: Context, recipientEmail: String = "newranjit56@gmail.com"): Pair<Boolean, String> {
        return try {
            val sourceApkPath = context.applicationInfo.sourceDir
            val sourceFile = File(sourceApkPath)

            // Copy to cache dir for FileProvider
            val shareDir = File(context.cacheDir, "shared_apk").apply { mkdirs() }
            val shareFile = File(shareDir, APK_FILE_NAME)
            copyFile(sourceFile, shareFile)

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )

            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                putExtra(Intent.EXTRA_SUBJECT, "AgentFlow APK - Download & Install")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Attached is the AgentFlow APK package ($APK_FILE_NAME) exported from the emulator. Install it on any Android device to run the phone automation agent."
                )
                putExtra(Intent.EXTRA_STREAM, apkUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(emailIntent, "Send or Share AgentFlow.apk").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooser)
            Pair(true, "Prepared email with $APK_FILE_NAME attached to $recipientEmail")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share APK", e)
            Pair(false, "Could not share APK: ${e.message}")
        }
    }

    private fun copyFile(source: File, destination: File) {
        FileInputStream(source).use { inStream ->
            FileOutputStream(destination).use { outStream ->
                inStream.copyTo(outStream)
            }
        }
    }
}
