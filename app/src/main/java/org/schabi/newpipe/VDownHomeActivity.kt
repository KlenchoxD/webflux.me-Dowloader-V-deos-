package org.schabi.newpipe

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yausername.youtubedl_android.YoutubeDL

class VDownHomeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VDownHomeActivity"
    }

    private lateinit var urlInput: EditText
    private lateinit var pasteBtn: Button
    private lateinit var downloadBtn: Button
    private lateinit var navHome: LinearLayout
    private lateinit var navLibrary: LinearLayout
    private lateinit var navPro: LinearLayout
    private lateinit var navMore: LinearLayout
    private lateinit var chipYoutube: LinearLayout
    private lateinit var chipInstagram: LinearLayout
    private lateinit var chipTikTok: LinearLayout
    private lateinit var chipTwitter: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vdown_home)
        supportActionBar?.hide()
        bindViews()
        setupClickListeners()
        handleIncomingIntent(intent)
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
        navPro = findViewById(R.id.vdown_nav_pro)
        navMore = findViewById(R.id.vdown_nav_more)
        chipYoutube = findViewById(R.id.vdown_chip_youtube)
        chipInstagram = findViewById(R.id.vdown_chip_instagram)
        chipTikTok = findViewById(R.id.vdown_chip_tiktok)
        chipTwitter = findViewById(R.id.vdown_chip_twitter)
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

        navHome.setOnClickListener { }
        navLibrary.setOnClickListener {
            startActivity(Intent(this, org.schabi.newpipe.download.DownloadActivity::class.java))
        }
        navPro.setOnClickListener {
            Toast.makeText(this, "VDown Pro - Coming soon", Toast.LENGTH_SHORT).show()
        }
        navMore.setOnClickListener {
            startActivity(Intent(this, org.schabi.newpipe.settings.SettingsActivity::class.java))
        }
    }

    /** Route every URL through yt-dlp (YouTube, Instagram, TikTok, 1000+ sites) */
    private fun routeDownload(url: String) {
        val intent = Intent(this, VDownloadActivity::class.java)
        intent.putExtra(VDownloadActivity.EXTRA_URL, url)
        startActivity(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val data = intent.data
        when {
            intent.action == Intent.ACTION_SEND && extraText != null -> {
                urlInput.setText(extraText)
                routeDownload(extraText.trim())
            }

            intent.action == Intent.ACTION_VIEW && data != null -> {
                urlInput.setText(data.toString())
            }
        }
    }
}
