package org.schabi.newpipe.download

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.schabi.newpipe.R

class FormatSelectorBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val ARG_URL = "arg_url"

        fun newInstance(url: String): FormatSelectorBottomSheet {
            return FormatSelectorBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_URL, url) }
            }
        }
    }

    enum class FormatOption(val formatFlags: List<String>) {
        M4A(listOf("-x", "--audio-format", "m4a")),
        MP3(listOf("-x", "--audio-format", "mp3")),
        VIDEO_360P(
            listOf(
                "-f",
                "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=360]+bestaudio/best[height<=360]/best",
                "--merge-output-format", "mp4"
            )
        ),
        VIDEO_720P(
            listOf(
                "-f",
                "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720]/best",
                "--merge-output-format", "mp4"
            )
        ),
        BEST_QUALITY(
            listOf("-f", "bestvideo+bestaudio/best")
        ),
        VIDEO_1080P(
            listOf(
                "-f",
                "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best",
                "--merge-output-format", "mp4"
            )
        )
    }

    private var selectedFormat: FormatOption = FormatOption.VIDEO_720P
    private var extraFormatsVisible = false
    private lateinit var url: String

    private lateinit var optionM4a: LinearLayout
    private lateinit var optionMp3: LinearLayout
    private lateinit var option360p: LinearLayout
    private lateinit var option720p: LinearLayout
    private lateinit var option1080p: LinearLayout
    private lateinit var optionBestQuality: LinearLayout

    private lateinit var radioM4a: RadioButton
    private lateinit var radioMp3: RadioButton
    private lateinit var radio360p: RadioButton
    private lateinit var radio720p: RadioButton
    private lateinit var radio1080p: RadioButton
    private lateinit var radioBestQuality: RadioButton

    private lateinit var moreFormatsBtn: LinearLayout
    private lateinit var extraFormatsContainer: LinearLayout
    private lateinit var moreFormatsArrow: android.widget.ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_format_selector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        url = arguments?.getString(ARG_URL) ?: run { dismiss(); return }

        view.findViewById<TextView>(R.id.bs_url_preview).text = url

        optionM4a = view.findViewById(R.id.bs_option_m4a)
        optionMp3 = view.findViewById(R.id.bs_option_mp3)
        option360p = view.findViewById(R.id.bs_option_360p)
        option720p = view.findViewById(R.id.bs_option_720p)
        option1080p = view.findViewById(R.id.bs_option_1080p)
        optionBestQuality = view.findViewById(R.id.bs_option_best_quality)

        radioM4a = view.findViewById(R.id.bs_radio_m4a)
        radioMp3 = view.findViewById(R.id.bs_radio_mp3)
        radio360p = view.findViewById(R.id.bs_radio_360p)
        radio720p = view.findViewById(R.id.bs_radio_720p)
        radio1080p = view.findViewById(R.id.bs_radio_1080p)
        radioBestQuality = view.findViewById(R.id.radio_best_quality)

        moreFormatsBtn = view.findViewById(R.id.bs_more_formats)
        extraFormatsContainer = view.findViewById(R.id.bs_extra_formats_container)
        moreFormatsArrow = view.findViewById(R.id.bs_more_formats_arrow)

        // Click listeners para selección de formato
        optionM4a.setOnClickListener { selectFormat(FormatOption.M4A) }
        optionMp3.setOnClickListener { selectFormat(FormatOption.MP3) }
        option360p.setOnClickListener { selectFormat(FormatOption.VIDEO_360P) }
        option720p.setOnClickListener { selectFormat(FormatOption.VIDEO_720P) }
        option1080p.setOnClickListener { selectFormat(FormatOption.VIDEO_1080P) }
        optionBestQuality.setOnClickListener { selectFormat(FormatOption.BEST_QUALITY) }

        // Toggle "More formats"
        moreFormatsBtn.setOnClickListener {
            extraFormatsVisible = !extraFormatsVisible
            if (extraFormatsVisible) {
                extraFormatsContainer.visibility = View.VISIBLE
                moreFormatsArrow.rotation = 270f
                view.findViewById<TextView>(R.id.bs_more_formats_text).text = "Less formats"
            } else {
                extraFormatsContainer.visibility = View.GONE
                moreFormatsArrow.rotation = 90f
                view.findViewById<TextView>(R.id.bs_more_formats_text).text = "More formats"
                // Si tenía seleccionado 1080p o BestQuality y se colapsa, bajar a 720p
                if (selectedFormat == FormatOption.VIDEO_1080P || selectedFormat == FormatOption.BEST_QUALITY) {
                    selectFormat(FormatOption.VIDEO_720P)
                }
            }
        }

        // Selección por defecto desde SharedPreferences
        val prefs = requireContext().getSharedPreferences(
            org.schabi.newpipe.MayBoxPrefs.PREFS_NAME, Context.MODE_PRIVATE
        )
        val defaultQuality = prefs.getString(
            org.schabi.newpipe.MayBoxPrefs.KEY_DEFAULT_QUALITY,
            org.schabi.newpipe.MayBoxPrefs.DEFAULT_QUALITY
        )
        val defaultFormat = when (defaultQuality) {
            "360p" -> FormatOption.VIDEO_360P
            "720p" -> FormatOption.VIDEO_720P
            "1080p" -> FormatOption.VIDEO_1080P
            "Best", "best", "Best quality", "best_quality" -> FormatOption.BEST_QUALITY
            else -> FormatOption.VIDEO_720P
        }

        // Si el default requiere los formatos extra, expandirlos automáticamente
        if (defaultFormat == FormatOption.VIDEO_1080P || defaultFormat == FormatOption.BEST_QUALITY) {
            extraFormatsVisible = true
            extraFormatsContainer.visibility = View.VISIBLE
            moreFormatsArrow.rotation = 270f
            view.findViewById<TextView>(R.id.bs_more_formats_text).text = "Less formats"
        }

        selectFormat(defaultFormat)

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.bs_download_btn)
            .setOnClickListener { startDownload() }
    }

    private fun selectFormat(format: FormatOption) {
        selectedFormat = format
        radioM4a.isChecked = format == FormatOption.M4A
        radioMp3.isChecked = format == FormatOption.MP3
        radio360p.isChecked = format == FormatOption.VIDEO_360P
        radio720p.isChecked = format == FormatOption.VIDEO_720P
        radio1080p.isChecked = format == FormatOption.VIDEO_1080P
        radioBestQuality.isChecked = format == FormatOption.BEST_QUALITY
    }

    private fun startDownload() {
        val intent = Intent(requireContext(), MayBoxDownloadService::class.java).apply {
            putExtra(MayBoxDownloadService.EXTRA_URL, url)
            putStringArrayListExtra(
                MayBoxDownloadService.EXTRA_FORMAT_FLAGS,
                ArrayList(selectedFormat.formatFlags)
            )
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(intent)
            } else {
                requireContext().startService(intent)
            }
            Toast.makeText(requireContext(), "Download started!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Failed to start download: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
        dismiss()
    }
}