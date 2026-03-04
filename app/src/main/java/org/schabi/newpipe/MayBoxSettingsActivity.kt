package org.schabi.newpipe

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat

object MayBoxPrefs {
    const val PREFS_NAME = "maybox_settings"
    const val KEY_DEFAULT_QUALITY = "default_quality"
    const val KEY_DEFAULT_AUDIO = "default_audio"
    const val KEY_SIMULTANEOUS = "simultaneous_downloads"
    const val KEY_WIFI_ONLY = "wifi_only"
    const val KEY_DOWNLOAD_PATH = "download_path"
    const val DEFAULT_QUALITY = "720p"
    const val DEFAULT_AUDIO = "mp3"
    const val DEFAULT_SIMULTANEOUS = 3
    const val DEFAULT_WIFI_ONLY = false
}

class MayBoxSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var qualityValue: TextView
    private lateinit var audioSubtitle: TextView
    private lateinit var simultaneousValue: TextView
    private lateinit var wifiSwitch: Switch

    private val audioOptions = arrayOf("MP3 (320kbps)", "M4A (Best quality)", "AAC", "Opus")
    private val audioKeys = arrayOf("mp3", "m4a", "aac", "opus")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maybox_settings)
        supportActionBar?.hide()

        window.statusBarColor = android.graphics.Color.parseColor("#080808")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        prefs = getSharedPreferences(MayBoxPrefs.PREFS_NAME, Context.MODE_PRIVATE)

        qualityValue = findViewById(R.id.settings_quality_value)
        audioSubtitle = findViewById(R.id.settings_audio_subtitle)
        simultaneousValue = findViewById(R.id.settings_simultaneous_value)
        wifiSwitch = findViewById(R.id.settings_wifi_only_switch)

        setupClickListeners()
        restoreSettings()
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.settings_back_btn).setOnClickListener { finish() }

        // 1. Download location
        findViewById<View>(R.id.settings_download_location).setOnClickListener {
            Toast.makeText(
                this,
                "/storage/emulated/0/Download/MayBox/",
                Toast.LENGTH_LONG
            ).show()
        }

        // 2. Default quality
        val qualityOptions = arrayOf("360p", "720p", "1080p", "Best quality")
        findViewById<View>(R.id.settings_default_quality).setOnClickListener {
            val current = prefs.getString(MayBoxPrefs.KEY_DEFAULT_QUALITY, MayBoxPrefs.DEFAULT_QUALITY)
            val currentIndex = qualityOptions.indexOf(current).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("Default quality")
                .setSingleChoiceItems(qualityOptions, currentIndex) { dialog, which ->
                    val selected = qualityOptions[which]
                    prefs.edit().putString(MayBoxPrefs.KEY_DEFAULT_QUALITY, selected).apply()
                    qualityValue.text = selected
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 3. Default audio format
        findViewById<View>(R.id.settings_audio_format).setOnClickListener {
            val current = prefs.getString(MayBoxPrefs.KEY_DEFAULT_AUDIO, MayBoxPrefs.DEFAULT_AUDIO)
            val currentIndex = audioKeys.indexOf(current).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("Default audio format")
                .setSingleChoiceItems(audioOptions, currentIndex) { dialog, which ->
                    prefs.edit().putString(MayBoxPrefs.KEY_DEFAULT_AUDIO, audioKeys[which]).apply()
                    audioSubtitle.text = audioOptions[which]
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 4. Simultaneous downloads
        val simOptions = arrayOf("1", "2", "3", "4", "5")
        findViewById<View>(R.id.settings_simultaneous_downloads).setOnClickListener {
            val current = prefs.getInt(MayBoxPrefs.KEY_SIMULTANEOUS, MayBoxPrefs.DEFAULT_SIMULTANEOUS)
            val currentIndex = (current - 1).coerceIn(0, 4)
            AlertDialog.Builder(this)
                .setTitle("Simultaneous downloads")
                .setSingleChoiceItems(simOptions, currentIndex) { dialog, which ->
                    val value = which + 1
                    prefs.edit().putInt(MayBoxPrefs.KEY_SIMULTANEOUS, value).apply()
                    simultaneousValue.text = value.toString()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 5. WiFi only switch
        wifiSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MayBoxPrefs.KEY_WIFI_ONLY, isChecked).apply()
        }

        // 7. Check for updates
        findViewById<View>(R.id.settings_check_updates).setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Mondacazo/VDown/releases"))
            )
        }

        // Bottom nav
        findViewById<LinearLayout>(R.id.settings_nav_home).setOnClickListener {
            startActivity(Intent(this, VDownHomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }
        findViewById<LinearLayout>(R.id.settings_nav_library).setOnClickListener {
            startActivity(Intent(this, MayBoxLibraryActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.settings_nav_more).setOnClickListener {
            // Already here
        }
    }

    private fun restoreSettings() {
        val quality = prefs.getString(MayBoxPrefs.KEY_DEFAULT_QUALITY, MayBoxPrefs.DEFAULT_QUALITY)
            ?: MayBoxPrefs.DEFAULT_QUALITY
        qualityValue.text = quality

        val audioKey = prefs.getString(MayBoxPrefs.KEY_DEFAULT_AUDIO, MayBoxPrefs.DEFAULT_AUDIO)
            ?: MayBoxPrefs.DEFAULT_AUDIO
        val audioIndex = audioKeys.indexOf(audioKey).coerceAtLeast(0)
        audioSubtitle.text = audioOptions[audioIndex]

        val simultaneous = prefs.getInt(MayBoxPrefs.KEY_SIMULTANEOUS, MayBoxPrefs.DEFAULT_SIMULTANEOUS)
        simultaneousValue.text = simultaneous.toString()

        wifiSwitch.isChecked = prefs.getBoolean(MayBoxPrefs.KEY_WIFI_ONLY, MayBoxPrefs.DEFAULT_WIFI_ONLY)
    }
}
