package org.schabi.newpipe.download

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

/**
 * Transparent activity that shows ONLY the FormatSelectorBottomSheet when
 * the user shares a URL from another app (TikTok, YouTube, Instagram, etc.).
 * Finishes itself when the bottom sheet is dismissed.
 */
class ShareReceiverActivity : AppCompatActivity() {

    private val fragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
            if (f is FormatSelectorBottomSheet) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawText = when {
            intent?.action == Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            intent?.action == Intent.ACTION_VIEW -> intent.data?.toString()
            else -> null
        }
        val url = rawText?.let(::extractUrl)

        if (url.isNullOrBlank()) {
            Toast.makeText(this, "No se encontró un enlace válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, false)

        val sheet = FormatSelectorBottomSheet.newInstance(url.trim())
        sheet.show(supportFragmentManager, "format_selector")
    }

    override fun onDestroy() {
        super.onDestroy()
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks)
    }
}
