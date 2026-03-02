package org.schabi.newpipe

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File

class VDownloadActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FORMAT_FLAGS = "extra_format_flags"
        private const val TAG = "VDownloadActivity"
        private const val PROCESS_ID = "maybox_dl"
    }

    private lateinit var tvUrl: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnCancel: Button
    private lateinit var btnDone: Button

    private val compositeDisposable = CompositeDisposable()
    private var downloading = false
    private var pendingUrl: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            pendingUrl?.let { startDownload(it) }
        } else {
            tvStatus.text = "Storage permission denied."
            btnCancel.text = "Close"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vdownload)
        supportActionBar?.hide()

        tvUrl = findViewById(R.id.vdl_url)
        tvStatus = findViewById(R.id.vdl_status)
        progressBar = findViewById(R.id.vdl_progress)
        btnCancel = findViewById(R.id.vdl_btn_cancel)
        btnDone = findViewById(R.id.vdl_btn_done)

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }

        tvUrl.text = url
        btnDone.visibility = View.GONE

        btnCancel.setOnClickListener {
            if (downloading) {
                try { YoutubeDL.getInstance().destroyProcessById(PROCESS_ID) } catch (e: Exception) {}
                downloading = false
                tvStatus.text = "Cancelled."
                btnCancel.text = "Close"
            } else finish()
        }
        btnDone.setOnClickListener { finish() }
        checkPermissionsAndDownload(url)
    }

    private fun checkPermissionsAndDownload(url: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startDownload(url)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                startDownload(url)
            } else {
                pendingUrl = url
                tvStatus.text = "Requesting storage permission..."
                requestPermissionLauncher.launch(arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ))
            }
        }
    }

    private fun startDownload(url: String) {
        if (downloading) return
        downloading = true

        val downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MayBox"
        )
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val formatFlags = intent.getStringArrayListExtra(EXTRA_FORMAT_FLAGS)

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

        tvStatus.text = "Starting download..."
        progressBar.progress = 0

        val disposable = Observable.fromCallable {
            YoutubeDL.getInstance().execute(request, PROCESS_ID) { progress, _, line ->
                runOnUiThread {
                    progressBar.progress = progress.toInt()
                    tvStatus.text = line
                }
            }
        }
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ _ ->
                downloading = false
                progressBar.progress = 100
                tvStatus.text = "Download complete!"
                btnCancel.visibility = View.GONE
                btnDone.visibility = View.VISIBLE
                scanDownloadFolder(downloadDir)
                Toast.makeText(this, "Saved to Downloads/MayBox", Toast.LENGTH_LONG).show()
            }, { error ->
                downloading = false
                tvStatus.text = "Error: ${error.message}"
                btnCancel.text = "Close"
                Log.e(TAG, "error", error)
            })

        compositeDisposable.add(disposable)
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

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }
}