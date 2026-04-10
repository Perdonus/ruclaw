package com.perdonus.ruclaw.android.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.Settings

class ApkUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)

    fun canRequestPackageInstalls(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    fun enqueueDownload(apkUrl: String, versionName: String): Long {
        val fileName = "ruclaw-${sanitize(versionName)}.apk"
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("RuClaw $versionName")
            .setDescription("Загружаю обновление Android-клиента")
            .setMimeType(APK_MIME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)

        return downloadManager.enqueue(request)
    }

    fun queryDownload(downloadId: Long): ApkDownloadSnapshot? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }

            val status = cursor.int(DownloadManager.COLUMN_STATUS)
            val downloadedBytes = cursor.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalBytes = cursor.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val reason = cursor.stringOrNull(DownloadManager.COLUMN_REASON)?.takeIf { it != "0" }
            val uri = if (status == DownloadManager.STATUS_SUCCESSFUL) {
                downloadManager.getUriForDownloadedFile(downloadId)?.toString().orEmpty()
            } else {
                cursor.stringOrNull(DownloadManager.COLUMN_LOCAL_URI).orEmpty()
            }

            return ApkDownloadSnapshot(
                downloadId = downloadId,
                state = when (status) {
                    DownloadManager.STATUS_PENDING -> ApkDownloadState.PENDING
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> ApkDownloadState.DOWNLOADING
                    DownloadManager.STATUS_SUCCESSFUL -> ApkDownloadState.READY_TO_INSTALL
                    DownloadManager.STATUS_FAILED -> ApkDownloadState.FAILED
                    else -> ApkDownloadState.IDLE
                },
                localUri = uri,
                progressPercent = if (downloadedBytes >= 0L && totalBytes > 0L) {
                    ((downloadedBytes * 100L) / totalBytes).toInt()
                } else {
                    null
                },
                reason = reason,
            )
        }
    }

    fun unknownSourcesIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun installIntent(downloadedUri: String): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(downloadedUri), APK_MIME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun sanitize(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "latest" }
    }

    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    private fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
    }
}

data class ApkDownloadSnapshot(
    val downloadId: Long,
    val state: ApkDownloadState,
    val localUri: String,
    val progressPercent: Int?,
    val reason: String?,
)

enum class ApkDownloadState {
    IDLE,
    PENDING,
    DOWNLOADING,
    READY_TO_INSTALL,
    FAILED,
}
