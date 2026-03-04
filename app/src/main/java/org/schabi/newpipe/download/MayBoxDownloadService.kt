package org.schabi.newpipe.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.schabi.newpipe.MayBoxPrefs
import org.schabi.newpipe.R
import java.io.File

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

    override fun onCreate() {
        super.onCreate()
        notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            try { YoutubeDL.getInstance().destroyProcessById(PROCESS_ID) } catch (e: Exception) {}
            stopSelf()
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL) ?: run { stopSelf(); return START_NOT_STICKY }
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
            @Suppress("DEPRECATION")
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            val activeNetwork = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            if (activeNetwork == null || activeNetwork.type != ConnectivityManager.TYPE_WIFI) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                notifManager.notify(NOTIF_ID_COMPLETE, buildErrorNotification("WiFi not available. Enable WiFi or disable WiFi-only setting."))
                stopSelf()
                return
            }
        }

        val downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MayBox"
        )
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val request = YoutubeDLRequest(url).apply {
            addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")
            if (formatFlags != null && formatFlags.isNotEmpty()) {
                val iter = formatFlags.listIterator()
                while (iter.hasNext()) {
                    val flag = iter.next()
                    val nextIdx = iter.nextIndex()
                    if (nextIdx < formatFlags.size && !formatFlags[nextIdx].startsWith("-")) {
                        addOption(flag, iter.next())
                    } else {
                        addOption(flag)
                    }
                }
            } else {
                addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
            }
            addOption("--no-mtime")
            addOption("--no-playlist")
            addOption("--concurrent-fragments", "4")
            addOption("--no-check-certificates")
            addOption("--no-part")
            addOption("--buffer-size", "16K")
            addOption("--retries", "3")
            addOption("--fragment-retries", "3")
        }

        try {
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
            // Success
            scanDownloadFolder(downloadDir)
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifManager.notify(NOTIF_ID_COMPLETE, buildCompleteNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifManager.notify(NOTIF_ID_COMPLETE, buildErrorNotification(e.message))
        } finally {
            stopSelf()
        }
    }

    private fun buildProgressNotification(title: String, contentText: String, progress: Int, indeterminate: Boolean): android.app.Notification {
        val cancelIntent = Intent(this, MayBoxDownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this, 0, cancelIntent,
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_file_download)
            .setContentTitle("Download complete!")
            .setContentText("Saved to Downloads/MayBox")
            .setAutoCancel(true)
            .build()
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
                vibrationPattern = longArrayOf(0, 250, 100, 250)
                enableVibration(true)
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
