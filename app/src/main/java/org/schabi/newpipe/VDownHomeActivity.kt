package org.schabi.newpipe

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow
import org.schabi.newpipe.download.extractUrl

class VDownHomeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VDownHomeActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }

    private lateinit var urlInput: EditText
    private lateinit var pasteBtn: Button
    private lateinit var downloadBtn: Button
    private lateinit var navHome: LinearLayout
    private lateinit var navLibrary: LinearLayout
    private lateinit var navMore: LinearLayout
    private lateinit var chipYoutube: LinearLayout
    private lateinit var chipInstagram: LinearLayout
    private lateinit var chipTikTok: LinearLayout
    private lateinit var chipTwitter: LinearLayout
    private lateinit var recentEmpty: LinearLayout
    private lateinit var recentList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vdown_home)
        supportActionBar?.hide()
        window.statusBarColor = Color.parseColor("#080808")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        bindViews()
        setupClickListeners()
        handleIncomingIntent(intent)
        requestNotificationPermission()
        loadRecentDownloads()
    }

    override fun onResume() {
        super.onResume()
        loadRecentDownloads()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun bindViews() {
        urlInput = findViewById(R.id.vdown_url_input)
        pasteBtn = findViewById(R.id.vdown_paste_btn)
        downloadBtn = findViewById(R.id.vdown_download_btn)
        navHome = findViewById(R.id.vdown_nav_home)
        navLibrary = findViewById(R.id.vdown_nav_library)
        navMore = findViewById(R.id.vdown_nav_more)
        chipYoutube = findViewById(R.id.vdown_chip_youtube)
        chipInstagram = findViewById(R.id.vdown_chip_instagram)
        chipTikTok = findViewById(R.id.vdown_chip_tiktok)
        chipTwitter = findViewById(R.id.vdown_chip_twitter)
        recentEmpty = findViewById(R.id.vdown_recent_empty)
        recentList = findViewById(R.id.vdown_recent_list)
    }

    private fun setupClickListeners() {
        pasteBtn.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this).toString()
                urlInput.setText(text)
                urlInput.setSelection(text.length)
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        downloadBtn.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(this, "Please paste a video URL first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            routeDownload(url)
        }

        chipYoutube.setOnClickListener { urlInput.setText("https://www.youtube.com/") }
        chipInstagram.setOnClickListener { urlInput.setText("https://www.instagram.com/") }
        chipTikTok.setOnClickListener { urlInput.setText("https://www.tiktok.com/") }
        chipTwitter.setOnClickListener { urlInput.setText("https://twitter.com/") }

        navHome.setOnClickListener { /* already here */ }
        navLibrary.setOnClickListener {
            startActivity(Intent(this, MayBoxLibraryActivity::class.java))
        }
        navMore.setOnClickListener {
            startActivity(Intent(this, MayBoxSettingsActivity::class.java))
        }

findViewById<View>(R.id.vdown_see_all)?.setOnClickListener {
            startActivity(Intent(this, MayBoxLibraryActivity::class.java))
        }

        findViewById<View>(R.id.vdown_see_all)?.setOnClickListener {
            startActivity(Intent(this, MayBoxLibraryActivity::class.java))
        }

        // "See all" abre Library en tab History
        findViewById<View>(R.id.vdown_see_all)?.setOnClickListener {
            startActivity(Intent(this, MayBoxLibraryActivity::class.java))
        }
    }

    private fun routeDownload(url: String) {
        val sanitizedUrl = extractUrl(url)
        if (sanitizedUrl == null) {
            Toast.makeText(this, "No se encontró un enlace válido", Toast.LENGTH_SHORT).show()
            return
        }
        val sheet = org.schabi.newpipe.download.FormatSelectorBottomSheet.newInstance(sanitizedUrl)
        sheet.show(supportFragmentManager, "format_selector")
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val data = intent.data
        when {
            intent.action == Intent.ACTION_SEND && extraText != null -> {
                val extractedUrl = extractUrl(extraText)
                if (extractedUrl == null) {
                    Toast.makeText(this, "No se encontró un enlace válido", Toast.LENGTH_SHORT).show()
                    return
                }
                urlInput.setText(extractedUrl)
                routeDownload(extractedUrl)
            }
            intent.action == Intent.ACTION_VIEW && data != null -> {
                val extractedUrl = extractUrl(data.toString())
                if (extractedUrl == null) {
                    Toast.makeText(this, "No se encontró un enlace válido", Toast.LENGTH_SHORT).show()
                    return
                }
                urlInput.setText(extractedUrl)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    private fun loadRecentDownloads() {
        val downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MayBox"
        )
        if (!downloadDir.exists()) {
            showEmptyState()
            return
        }
        val files = downloadDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.take(5)

        if (files.isNullOrEmpty()) {
            showEmptyState()
            return
        }

        recentEmpty.visibility = View.GONE
        recentList.visibility = View.VISIBLE
        recentList.removeAllViews()

        for (file in files) {
            val itemView = layoutInflater.inflate(android.R.layout.simple_list_item_2, recentList, false)
            itemView.findViewById<TextView>(android.R.id.text1)?.apply {
                text = file.name
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
            }
            itemView.findViewById<TextView>(android.R.id.text2)?.apply {
                text = formatFileSize(file.length())
                setTextColor(0x80FFFFFF.toInt())
                textSize = 11f
            }
            recentList.addView(itemView)
        }
    }

    private fun showEmptyState() {
        recentEmpty.visibility = View.VISIBLE
        recentList.visibility = View.GONE
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(
            bytes / 1024.0.pow(digitGroups.toDouble())
        ) + " " + units[digitGroups]
    }
}




