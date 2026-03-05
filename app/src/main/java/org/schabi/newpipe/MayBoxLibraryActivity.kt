package org.schabi.newpipe

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MayBoxLibraryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var placeholder: View
    private lateinit var placeholderText: TextView
    private lateinit var tabHistory: TextView
    private lateinit var tabActive: TextView
    private lateinit var tabScheduled: TextView

    private var currentTab = 0 // 0=History, 1=Active, 2=Scheduled

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maybox_library)
        supportActionBar?.hide()

        window.statusBarColor = android.graphics.Color.parseColor("#080808")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        recyclerView = findViewById(R.id.library_recycler)
        emptyState = findViewById(R.id.library_empty_state)
        placeholder = findViewById(R.id.library_placeholder)
        placeholderText = findViewById(R.id.library_placeholder_text)
        tabHistory = findViewById(R.id.library_tab_history)
        tabActive = findViewById(R.id.library_tab_active)
        tabScheduled = findViewById(R.id.library_tab_scheduled)

        recyclerView.layoutManager = LinearLayoutManager(this)

        setupTabs()
        setupBottomNav()

        findViewById<View>(R.id.library_back_btn).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (currentTab == 0) loadFiles()
    }

    private fun setupTabs() {
        tabHistory.setOnClickListener { selectTab(0) }
        tabActive.setOnClickListener { selectTab(1) }
        tabScheduled.setOnClickListener { selectTab(2) }
        selectTab(0)
    }

    private fun selectTab(index: Int) {
        currentTab = index

        val activeColor = android.graphics.Color.parseColor("#E60A15")
        val inactiveColor = android.graphics.Color.parseColor("#64748B")

        tabHistory.setTextColor(if (index == 0) activeColor else inactiveColor)
        tabActive.setTextColor(if (index == 1) activeColor else inactiveColor)
        tabScheduled.setTextColor(if (index == 2) activeColor else inactiveColor)

        when (index) {
            0 -> loadFiles()
            1 -> showPlaceholder("No active downloads")
            2 -> showPlaceholder("No scheduled downloads")
        }
    }

    private fun loadFiles() {
        val downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MayBox"
        )
        val files = if (downloadDir.exists()) {
            downloadDir.listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } else {
            emptyList()
        }

        recyclerView.visibility = View.GONE
        emptyState.visibility = View.GONE
        placeholder.visibility = View.GONE

        if (files.isEmpty()) {
            emptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = LibraryAdapter(files.toMutableList())
        }
    }

    private fun showPlaceholder(text: String) {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.GONE
        placeholder.visibility = View.VISIBLE
        placeholderText.text = text
    }

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.library_nav_home).setOnClickListener {
            startActivity(
                Intent(this, VDownHomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        }
        findViewById<LinearLayout>(R.id.library_nav_library).setOnClickListener { /* already here */ }
        findViewById<LinearLayout>(R.id.library_nav_more).setOnClickListener {
            startActivity(Intent(this, MayBoxSettingsActivity::class.java))
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file.extension))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Cannot open file", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(ext: String): String = when (ext.lowercase()) {
        "mp4", "mkv", "webm", "avi", "mov" -> "video/*"
        "mp3", "m4a", "aac", "opus", "ogg" -> "audio/*"
        else -> "*/*"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private inner class LibraryAdapter(private val files: MutableList<File>) :
        RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.lib_item_icon)
            val name: TextView = view.findViewById(R.id.lib_item_name)
            val ext: TextView = view.findViewById(R.id.lib_item_ext)
            val size: TextView = view.findViewById(R.id.lib_item_size)
            val date: TextView = view.findViewById(R.id.lib_item_date)
            val delete: ImageView = view.findViewById(R.id.lib_item_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_library_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            val extension = file.extension.lowercase()

            holder.name.text = file.name
            holder.ext.text = file.extension.uppercase()
            holder.size.text = formatSize(file.length())
            holder.date.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(Date(file.lastModified()))

            val iconRes = when (extension) {
                "mp4", "mkv", "webm", "avi", "mov" -> R.drawable.ic_movie
                "mp3", "m4a", "aac", "opus", "ogg" -> R.drawable.ic_music_note
                else -> R.drawable.ic_file_download
            }
            holder.icon.setImageResource(iconRes)

            holder.itemView.setOnClickListener { openFile(file) }

            holder.delete.setOnClickListener {
                AlertDialog.Builder(this@MayBoxLibraryActivity)
                    .setTitle("Delete ${file.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        val pos = holder.bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            file.delete()
                            files.removeAt(pos)
                            notifyItemRemoved(pos)
                            if (files.isEmpty()) {
                                recyclerView.visibility = View.GONE
                                emptyState.visibility = View.VISIBLE
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        override fun getItemCount() = files.size
    }
}
