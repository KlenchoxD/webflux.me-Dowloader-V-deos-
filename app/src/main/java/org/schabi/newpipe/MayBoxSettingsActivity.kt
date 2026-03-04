package org.schabi.newpipe

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import org.schabi.newpipe.download.DownloadActivity

class MayBoxSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maybox_settings)
        supportActionBar?.hide()

        // Dark status bar
        window.statusBarColor = android.graphics.Color.parseColor("#080808")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        prefs = getSharedPreferences("maybox_settings", Context.MODE_PRIVATE)

        setupClickListeners()
        restoreSettings()
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<View>(R.id.settings_back_btn).setOnClickListener { finish() }

        // General settings
        findViewById<View>(R.id.settings_download_location).setOnClickListener {
            Toast.makeText(this, "Download location: /Internal Storage/MayBox/Downloads", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.settings_default_quality).setOnClickListener {
            Toast.makeText(this, "Default quality: 720p", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.settings_audio_format).setOnClickListener {
            Toast.makeText(this, "Default audio: MP3 (320kbps)", Toast.LENGTH_SHORT).show()
        }

        // Download settings
        findViewById<View>(R.id.settings_simultaneous_downloads).setOnClickListener {
            Toast.makeText(this, "Simultaneous downloads: 3", Toast.LENGTH_SHORT).show()
        }

        // WiFi only switch
        val wifiSwitch = findViewById<Switch>(R.id.settings_wifi_only_switch)
        wifiSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("wifi_only", isChecked).apply()
        }

        // About settings
        findViewById<View>(R.id.settings_check_updates).setOnClickListener {
            Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
        }

        // Bottom nav
        findViewById<LinearLayout>(R.id.settings_nav_home).setOnClickListener {
            startActivity(Intent(this, VDownHomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
        findViewById<LinearLayout>(R.id.settings_nav_library).setOnClickListener {
            startActivity(Intent(this, DownloadActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.settings_nav_more).setOnClickListener {
            // Already here
        }
    }

    private fun restoreSettings() {
        val wifiSwitch = findViewById<Switch>(R.id.settings_wifi_only_switch)
        wifiSwitch.isChecked = prefs.getBoolean("wifi_only", true)
    }
}
