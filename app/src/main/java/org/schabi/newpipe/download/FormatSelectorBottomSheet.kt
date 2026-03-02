package org.schabi.newpipe.download

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.VDownloadActivity

/**
 * Bottom sheet dialog that lets the user pick a download format
 * (audio or video at various resolutions) before starting the download.
 */
class FormatSelectorBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val ARG_URL = "arg_url"

        fun newInstance(url: String): FormatSelectorBottomSheet {
            return FormatSelectorBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_URL, url) }
            }
        }
    }

    /** Format options with their yt-dlp flags */
    enum class FormatOption(val formatFlags: List<String>) {
        M4A(listOf("-x", "--audio-format", "m4a")),
        MP3(listOf("-x", "--audio-format", "mp3")),
        VIDEO_360P(listOf("-f", "bestvideo[height<=360]+bestaudio/best[height<=360]")),
        VIDEO_720P(listOf("-f", "bestvideo[height<=720]+bestaudio/best[height<=720]")),
        VIDEO_1080P(listOf("-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]"))
    }

    private var selectedFormat: FormatOption = FormatOption.VIDEO_720P

    private lateinit var url: String

    // Format row containers
    private lateinit var optionM4a: LinearLayout
    private lateinit var optionMp3: LinearLayout
    private lateinit var option360p: LinearLayout
    private lateinit var option720p: LinearLayout
    private lateinit var option1080p: LinearLayout

    // Radio buttons
    private lateinit var radioM4a: RadioButton
    private lateinit var radioMp3: RadioButton
    private lateinit var radio360p: RadioButton
    private lateinit var radio720p: RadioButton
    private lateinit var radio1080p: RadioButton

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

        radioM4a = view.findViewById(R.id.bs_radio_m4a)
        radioMp3 = view.findViewById(R.id.bs_radio_mp3)
        radio360p = view.findViewById(R.id.bs_radio_360p)
        radio720p = view.findViewById(R.id.bs_radio_720p)
        radio1080p = view.findViewById(R.id.bs_radio_1080p)

        optionM4a.setOnClickListener { selectFormat(FormatOption.M4A) }
        optionMp3.setOnClickListener { selectFormat(FormatOption.MP3) }
        option360p.setOnClickListener { selectFormat(FormatOption.VIDEO_360P) }
        option720p.setOnClickListener { selectFormat(FormatOption.VIDEO_720P) }
        option1080p.setOnClickListener { selectFormat(FormatOption.VIDEO_1080P) }

        // Default selection
        selectFormat(FormatOption.VIDEO_720P)

        view.findViewById<Button>(R.id.bs_download_btn).setOnClickListener {
            startDownload()
        }
    }

    private fun selectFormat(format: FormatOption) {
        selectedFormat = format
        radioM4a.isChecked = format == FormatOption.M4A
        radioMp3.isChecked = format == FormatOption.MP3
        radio360p.isChecked = format == FormatOption.VIDEO_360P
        radio720p.isChecked = format == FormatOption.VIDEO_720P
        radio1080p.isChecked = format == FormatOption.VIDEO_1080P
    }

    private fun startDownload() {
        val intent = Intent(requireContext(), VDownloadActivity::class.java).apply {
            putExtra(VDownloadActivity.EXTRA_URL, url)
            putStringArrayListExtra(
                VDownloadActivity.EXTRA_FORMAT_FLAGS,
                ArrayList(selectedFormat.formatFlags)
            )
        }
        startActivity(intent)
        dismiss()
    }
}
