package org.schabi.newpipe.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import org.schabi.newpipe.MayBoxPrefs
import org.schabi.newpipe.R

/**
 * Foreground service that runs yt-dlp downloads in the background,
 * surviving app close and showing progress in the notification shade.
 */
class MayBoxDownloadService : Service() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FORMAT_FLAGS = "extra_format_flags"

        private const val TAG = "MayBoxDownloadService"
        private const val CHANNEL_ID = "maybox_downloads"
        private const val NOTIF_ID_PROGRESS = 2001
        private const val NOTIF_ID_COMPLETE = 2002
        private const val PROCESS_ID = "maybox_svc_dl"

        const val ACTION_CANCEL = "org.schabi.newpipe.download.CANCEL_DOWNLOAD"
    }

    private lateinit var notifManager: NotificationManager
    private var lastDownloadedFile: File? = null

    override fun onCreate() {
        super.onCreate()
        notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            try {
                YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
            } catch (e: Exception) {}
            stopSelf()
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val formatFlags = intent.getStringArrayListExtra(EXTRA_FORMAT_FLAGS)

        // Start foreground immediately with an indeterminate notification
        startForeground(NOTIF_ID_PROGRESS, buildProgressNotification("Downloading...", "Starting...", 0, true))

        Thread {
            runDownload(url, formatFlags)
        }.start()

        return START_NOT_STICKY
    }

    private fun runDownload(url: String, formatFlags: ArrayList<String>?) {
        val prefs = applicationContext.getSharedPreferences(MayBoxPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        val wifiOnly = prefs.getBoolean(MayBoxPrefs.KEY_WIFI_ONLY, false)
        if (wifiOnly) {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = if (network != null) cm.getNetworkCapabilities(network) else null
            val isWifi = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true

            if (!isWifi) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                notifManager.notify(NOTIF_ID_COMPLETE, buildErrorNotification("WiFi not available. Enable WiFi or disable WiFi-only setting."))
                stopSelf()
                return
            }
        }

        val downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MayBox"
        )
        if (!downloadDir.exists()) downloadDir.mkdirs()

        try {
            executeWithFallback(url, downloadDir, formatFlags)
            // Success
            scanDownloadFolder(downloadDir)
            val newestFile = downloadDir.listFiles()
                ?.filter { it.isFile }
                ?.maxByOrNull { it.lastModified() }
            lastDownloadedFile = newestFile
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifManager.notify(NOTIF_ID_COMPLETE, buildCompleteNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifManager.notify(NOTIF_ID_COMPLETE, buildErrorNotification(parseYtDlpError(e.message)))
        } finally {
            stopSelf()
        }
    }

    private fun executeWithFallback(
        url: String,
        downloadDir: File,
        formatFlags: List<String>?
    ) {
        if (!formatFlags.isNullOrEmpty()) {
            executeRequest(buildRequest(url, downloadDir, formatFlags = formatFlags))
            return
        }

        try {
            executeRequest(
                buildRequest(
                    url,
                    downloadDir,
                    formatOverride = listOf("-f", "bestvideo+bestaudio/best")
                )
            )
            return
        } catch (firstError: Exception) {
            if (!isNoVideoInPostError(firstError.message)) {
                throw firstError
            }
            Log.w(TAG, "No video stream found, retrying with audio fallback", firstError)
        }

        try {
            executeRequest(
                buildRequest(
                    url,
                    downloadDir,
                    formatOverride = listOf("-f", "bestaudio/best")
                )
            )
            return
        } catch (secondError: Exception) {
            Log.w(TAG, "Audio fallback failed, retrying without explicit format", secondError)
        }

        executeRequest(buildRequest(url, downloadDir, formatOverride = emptyList()))
    }

    private fun executeRequest(request: YoutubeDLRequest) {
        YoutubeDL.getInstance().execute(request, PROCESS_ID) { progress, bytesPerSecond, line ->
            val speedMb = bytesPerSecond / (1024f * 1024f)
            val speedText = if (speedMb > 0) "%.1f MB/s".format(speedMb) else ""
            val contentText = when {
                progress > 0f && speedText.isNotEmpty() -> "%.0f%% • %s".format(progress, speedText)
                progress > 0f -> "%.0f%%".format(progress)
                else -> "Starting..."
            }
            notifManager.notify(
                NOTIF_ID_PROGRESS,
                buildProgressNotification("Downloading...", contentText, progress.toInt(), progress <= 0f)
            )
            Log.d(TAG, "progress=$progress line=$line")
        }
    }

    private fun buildRequest(
        url: String,
        downloadDir: File,
        formatFlags: List<String>? = null,
        formatOverride: List<String>? = null
    ): YoutubeDLRequest {
        val cleanUrl = extractUrl(url) ?: url
        return YoutubeDLRequest(cleanUrl).apply {
            addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")

            when {
                formatOverride != null && formatOverride.isNotEmpty() -> {
                    addFlags(this, formatOverride)
                }

                !formatFlags.isNullOrEmpty() -> {
                    addFlags(this, formatFlags)
                }
            }

            addOption("--no-mtime")
            if (!isCarouselUrl(cleanUrl)) {
                addOption("--no-playlist")
            }
            val prefs = applicationContext.getSharedPreferences(
                MayBoxPrefs.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val fragments = prefs.getInt(
                MayBoxPrefs.KEY_SIMULTANEOUS,
                MayBoxPrefs.DEFAULT_SIMULTANEOUS
            )
            addOption("--concurrent-fragments", fragments.toString())
            addOption("--no-check-certificates")
            addOption("--no-part")
            addOption("--buffer-size", "16K")
            addOption("--retries", "3")
            addOption("--fragment-retries", "3")
        }
    }

    private fun addFlags(request: YoutubeDLRequest, flags: List<String>) {
        val iter = flags.listIterator()
        while (iter.hasNext()) {
            val flag = iter.next()
            val nextIdx = iter.nextIndex()
            if (nextIdx < flags.size && !flags[nextIdx].startsWith("-")) {
                request.addOption(flag, iter.next())
            } else {
                request.addOption(flag)
            }
        }
    }

    private fun buildProgressNotification(title: String, contentText: String, progress: Int, indeterminate: Boolean): android.app.Notification {
        val cancelIntent = Intent(this, MayBoxDownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_file_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_close, "Cancel", cancelPending)
            .build()
    }

    private fun buildCompleteNotification(): android.app.Notification {
        val fileName = lastDownloadedFile?.name ?: "file"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_file_download)
            .setContentTitle("Download complete!")
            .setContentText("$fileName • Tap to open")
            .setAutoCancel(true)

        lastDownloadedFile?.let { file ->
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "$packageName.provider",
                    file
                )
                val mimeType = when (file.extension.lowercase()) {
                    "mp4", "mkv", "webm", "avi", "mov" -> "video/*"
                    "mp3", "m4a", "aac", "opus", "ogg" -> "audio/*"
                    else -> "*/*"
                }
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setContentIntent(pendingIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create open intent for notification", e)
            }
        }

        return builder.build()
    }

    private fun buildErrorNotification(message: String?): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_file_download)
            .setContentTitle("Download failed")
            .setContentText(message ?: "An error occurred")
            .setAutoCancel(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MayBox Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for MayBox yt-dlp downloads"
            }
            notifManager.createNotificationChannel(channel)
        }
    }

    private fun scanDownloadFolder(folder: File) {
        val files = folder.listFiles() ?: return
        if (files.isEmpty()) return
        MediaScannerConnection.scanFile(
            applicationContext,
            files.map { it.absolutePath }.toTypedArray(),
            null
        ) { path, _ -> Log.d(TAG, "Scanned: $path") }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
