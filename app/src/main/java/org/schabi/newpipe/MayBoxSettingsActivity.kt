package org.schabi.newpipe

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.switchmaterial.SwitchMaterial

object MayBoxPrefs {
    const val PREFS_NAME = "maybox_settings"
    const val KEY_DEFAULT_QUALITY = "default_quality"
    const val KEY_DEFAULT_AUDIO = "default_audio"
    const val KEY_SIMULTANEOUS = "simultaneous_downloads"
    const val KEY_WIFI_ONLY = "wifi_only"
    const val KEY_DOWNLOAD_PATH = "download_path"
    const val KEY_COOKIES_FILE = "cookies_file"
    const val DEFAULT_QUALITY = "720p"
    const val DEFAULT_AUDIO = "mp3"
    const val DEFAULT_SIMULTANEOUS = 3
    const val DEFAULT_WIFI_ONLY = false

    /** Ruta por defecto — /storage/emulated/0/Download/MayBox */
    fun getDefaultDownloadPath(): String =
        "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/MayBox"

    /** Obtiene la ruta guardada o la predeterminada */
    fun getDownloadPath(prefs: SharedPreferences): String =
        prefs.getString(KEY_DOWNLOAD_PATH, null) ?: getDefaultDownloadPath()
}

class MayBoxSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var qualityValue: TextView
    private lateinit var audioSubtitle: TextView
    private lateinit var simultaneousValue: TextView
    private lateinit var wifiSwitch: SwitchMaterial
    private lateinit var downloadLocationSubtitle: TextView
    private lateinit var cookiesSubtitle: TextView
    private lateinit var cookiesStatus: TextView

    private val audioOptions = arrayOf("MP3 (320kbps)", "M4A (Best quality)", "AAC", "Opus")
    private val audioKeys = arrayOf("mp3", "m4a", "aac", "opus")
    private val qualityOptions = arrayOf("360p", "720p", "1080p", "Best quality")
    private val qualityKeys = arrayOf("360p", "720p", "1080p", "best_quality")

    /** Lanzador del selector de carpeta del sistema */
    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persistir permisos para acceder a la carpeta en el futuro
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val path = uri.toString()
            prefs.edit().putString(MayBoxPrefs.KEY_DOWNLOAD_PATH, path).apply()
            downloadLocationSubtitle.text = uriToDisplayPath(uri)
            Toast.makeText(this, "Download location updated", Toast.LENGTH_SHORT).show()
        }
    }

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
        downloadLocationSubtitle = findViewById(R.id.settings_download_location_subtitle)
        cookiesSubtitle = findViewById(R.id.settings_cookies_subtitle)
        cookiesStatus = findViewById(R.id.settings_cookies_status)

        setupClickListeners()
        restoreSettings()
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.settings_back_btn).setOnClickListener { finish() }

        // 1. Download location — muestra ruta actual y permite cambiarla
        findViewById<View>(R.id.settings_download_location).setOnClickListener {
            val currentPath = MayBoxPrefs.getDownloadPath(prefs)
            AlertDialog.Builder(this)
                .setTitle("Download location")
                .setMessage("Current:\n$currentPath\n\nDo you want to change it?")
                .setPositiveButton("Change folder") { _, _ ->
                    // Abre el selector de carpetas del sistema
                    folderPicker.launch(null)
                }
                .setNeutralButton("Reset to default") { _, _ ->
                    prefs.edit().remove(MayBoxPrefs.KEY_DOWNLOAD_PATH).apply()
                    downloadLocationSubtitle.text = shortenPath(MayBoxPrefs.getDefaultDownloadPath())
                    Toast.makeText(this, "Reset to default location", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 2. Default quality
        findViewById<View>(R.id.settings_default_quality).setOnClickListener {
            val currentKey = normalizeQualityKey(
                prefs.getString(MayBoxPrefs.KEY_DEFAULT_QUALITY, MayBoxPrefs.DEFAULT_QUALITY)
            )
            val currentIndex = qualityKeys.indexOf(currentKey).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("Default quality")
                .setSingleChoiceItems(qualityOptions, currentIndex) { dialog, which ->
                    prefs.edit().putString(MayBoxPrefs.KEY_DEFAULT_QUALITY, qualityKeys[which]).apply()
                    qualityValue.text = qualityOptions[which]
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

        // 6. Cookies file
        val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { selectedUri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        selectedUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    val path = selectedUri.toString()
                    prefs.edit().putString(MayBoxPrefs.KEY_COOKIES_FILE, path).apply()
                    cookiesSubtitle.text = "cookies.txt"
                    cookiesStatus.text = "Set"
                    Toast.makeText(this, "Cookies file set", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to set cookies: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<View>(R.id.settings_cookies_file).setOnClickListener {
            val currentPath = prefs.getString(MayBoxPrefs.KEY_COOKIES_FILE, null)
            AlertDialog.Builder(this)
                .setTitle("Cookies file (cookies.txt)")
                .setMessage(
                    if (currentPath != null) {
                        "Current: $currentPath\n\nSelect a new cookies.txt file or reset to default."
                    } else {
                        "Select a cookies.txt file exported from your browser.\n\nThis allows yt-dlp to access logged-in content on YouTube and other sites."
                    }
                )
                .setPositiveButton("Select file") { _, _ ->
                    filePicker.launch("text/plain")
                }
                .setNeutralButton("Reset") { _, _ ->
                    prefs.edit().remove(MayBoxPrefs.KEY_COOKIES_FILE).apply()
                    cookiesSubtitle.text = "Not set"
                    cookiesStatus.text = "Not set"
                    Toast.makeText(this, "Cookies file reset", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 7. Check for updates
        findViewById<View>(R.id.settings_check_updates).setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Mondacazo/VDown/releases"))
            )
        }

        // Bottom nav
        findViewById<LinearLayout>(R.id.settings_nav_home).setOnClickListener {
            startActivity(
                Intent(this, VDownHomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        }
        findViewById<LinearLayout>(R.id.settings_nav_library).setOnClickListener {
            startActivity(Intent(this, MayBoxLibraryActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.settings_nav_more).setOnClickListener { /* already here */ }
    }

    private fun restoreSettings() {
        // Download location
        val savedPath = prefs.getString(MayBoxPrefs.KEY_DOWNLOAD_PATH, null)
        downloadLocationSubtitle.text = if (savedPath != null) {
            try {
                uriToDisplayPath(Uri.parse(savedPath))
            } catch (e: Exception) {
                shortenPath(MayBoxPrefs.getDefaultDownloadPath())
            }
        } else {
            shortenPath(MayBoxPrefs.getDefaultDownloadPath())
        }

        // Quality
        val qualityKey = normalizeQualityKey(
            prefs.getString(MayBoxPrefs.KEY_DEFAULT_QUALITY, MayBoxPrefs.DEFAULT_QUALITY)
        )
        val qualityIndex = qualityKeys.indexOf(qualityKey).coerceAtLeast(0)
        qualityValue.text = qualityOptions[qualityIndex]

        // Audio
        val audioKey = prefs.getString(MayBoxPrefs.KEY_DEFAULT_AUDIO, MayBoxPrefs.DEFAULT_AUDIO)
            ?: MayBoxPrefs.DEFAULT_AUDIO
        val audioIndex = audioKeys.indexOf(audioKey).coerceAtLeast(0)
        audioSubtitle.text = audioOptions[audioIndex]

        // Simultaneous
        val simultaneous = prefs.getInt(MayBoxPrefs.KEY_SIMULTANEOUS, MayBoxPrefs.DEFAULT_SIMULTANEOUS)
        simultaneousValue.text = simultaneous.toString()

        // WiFi only
        wifiSwitch.isChecked = prefs.getBoolean(MayBoxPrefs.KEY_WIFI_ONLY, MayBoxPrefs.DEFAULT_WIFI_ONLY)

        // Cookies file
        val cookiesPath = prefs.getString(MayBoxPrefs.KEY_COOKIES_FILE, null)
        if (cookiesPath != null) {
            cookiesSubtitle.text = "cookies.txt"
            cookiesStatus.text = "Set"
        } else {
            cookiesSubtitle.text = "Not set"
            cookiesStatus.text = "Not set"
        }
    }

    /** Convierte una URI de carpeta a una ruta legible */
    private fun uriToDisplayPath(uri: Uri): String {
        return try {
            val path = uri.lastPathSegment ?: uri.toString()
            // Ejemplo: "primary:Download/MayBox" → "/Download/MayBox"
            val clean = path.replace("primary:", "/storage/emulated/0/")
            shortenPath(clean)
        } catch (e: Exception) {
            uri.toString().takeLast(40)
        }
    }

    private fun shortenPath(path: String): String {
        return if (path.length > 45) "..." + path.takeLast(42) else path
    }

    private fun normalizeQualityKey(value: String?): String {
        return when (value) {
            "Best", "best", "Best quality", "best_quality" -> "best_quality"
            "360p", "720p", "1080p" -> value ?: MayBoxPrefs.DEFAULT_QUALITY
            else -> MayBoxPrefs.DEFAULT_QUALITY
        }
    }
}