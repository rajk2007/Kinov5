package com.lagradost.cloudstream3.ui.player

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.PlayerSheetTracksBinding
import com.lagradost.cloudstream3.ui.result.displayLinkName
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToEnglishLanguageName
import kotlin.math.roundToInt

/** Small, non-blocking player selector shared by the phone audio/subtitle and quality sheets. */
class KinoTrackSheetDialog(
    context: Context,
    private val mode: Mode,
    private val audioTracks: List<AudioTrack> = emptyList(),
    private val videoTracks: List<VideoTrack> = emptyList(),
    private val subtitles: List<SubtitleData> = emptyList(),
    private val selectedAudio: AudioTrack? = null,
    private val selectedVideo: VideoTrack? = null,
    private val selectedSubtitle: SubtitleData? = null,
    private val subtitlesEnabled: Boolean = true,
    private val sources: List<VideoLink> = emptyList(),
    private val selectedSource: VideoLink? = null,
    private val initialSubtitles: Boolean = false,
    private val onAudioSelected: (AudioTrack) -> Unit = {},
    private val onVideoSelected: (VideoTrack) -> Unit = {},
    private val onSubtitleSelected: (SubtitleData) -> Unit = {},
    private val onSubtitlesToggled: (Boolean) -> Unit = {},
    private val onSourceSelected: (VideoLink) -> Unit = {},
    private val onSearchOnline: () -> Unit = {},
    private val onSubtitleSettings: () -> Unit = {},
) : Dialog(context, R.style.DialogCompactPlayer) {
    enum class Mode { AUDIO_SUBTITLES, QUALITY }

    private val binding = PlayerSheetTracksBinding.inflate(LayoutInflater.from(context))
    private val red = ContextCompat.getColor(context, R.color.colorPrimary)
    private val white = ContextCompat.getColor(context, R.color.white)
    private val gray = ContextCompat.getColor(context, R.color.grayTextColor)
    private var currentVideo: VideoTrack? = selectedVideo
    private var currentSubtitle: SubtitleData? = selectedSubtitle
    private val qualityRows = mutableListOf<Pair<VideoTrack, View>>()
    private val subtitleRows = mutableListOf<Pair<SubtitleData, View>>()
    private var lastUserTapAt = 0L
    private val panelWidth = (context.resources.displayMetrics.widthPixels * .42f).roundToInt()
        .coerceAtLeast(dp(300)).coerceAtMost(dp(480))

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        setCanceledOnTouchOutside(true)
        window?.setBackgroundDrawableResource(R.color.transparent)
        window?.setDimAmount(0f)
        build()
    }

    override fun show() {
        super.show()
        window?.apply {
            setLayout(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            attributes = attributes.apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(16)
                y = dp(52)
            }
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        binding.sheetPanel.alpha = 0f
        binding.sheetPanel.translationX = dp(18).toFloat()
        binding.sheetPanel.animate().alpha(1f).translationX(0f).setDuration(200L).start()
        binding.sheetScroll.post {
            val maxHeight = (context.resources.displayMetrics.heightPixels * .65f).roundToInt()
            if (binding.sheetContent.height > maxHeight) {
                binding.sheetScroll.layoutParams = binding.sheetScroll.layoutParams.apply {
                    height = maxHeight
                }
            }
        }
    }

    private fun build() {
        binding.sheetPanel.background = rounded(0xD90A0A0C.toInt(), 24)
        binding.sheetTabs.setPadding(
            binding.sheetTabs.paddingLeft,
            binding.sheetTabs.paddingTop,
            binding.sheetTabs.paddingRight,
            dp(16),
        )
        if (mode == Mode.AUDIO_SUBTITLES) {
            binding.sheetTabs.visibility = View.VISIBLE
            val audioTab = tab("♫  Audio", true)
            val subTab = tab("CC  Subtitles", false)
            binding.sheetTabs.addView(audioTab, weightParams())
            binding.sheetTabs.addView(subTab, weightParams())
            audioTab.setOnClickListener { renderAudio(audioTab, subTab) }
            subTab.setOnClickListener { renderSubtitles(audioTab, subTab) }
            if (initialSubtitles) renderSubtitles(audioTab, subTab) else renderAudio(audioTab, subTab)
        } else {
            binding.sheetTitle.text = "Quality"
            (binding.sheetTitle.layoutParams as? LinearLayout.LayoutParams)?.let {
                it.topMargin = dp(12)
                binding.sheetTitle.layoutParams = it
            }
            renderQuality()
        }
    }

    private fun renderAudio(audioTab: TextView, subTab: TextView) {
        selectTab(audioTab, true)
        selectTab(subTab, false)
        binding.sheetTitle.visibility = View.GONE
        binding.subtitleToggleRow.visibility = View.GONE
        binding.subtitleActions.visibility = View.GONE
        binding.sheetContent.removeAllViews()

        binding.sheetContent.addView(sectionLabel("Audio languages"))

        if (sources.isEmpty() && audioTracks.size <= 1) {
            addRow("Default audio", R.drawable.ic_baseline_audiotrack_24, true) {}
        }

        val selectedSourceKey = selectedSource?.let { it.first?.url ?: it.second?.uri?.toString() }
        sources.forEach { source ->
            val link = source.first
            val name = link?.name ?: source.second?.name ?: context.getString(R.string.video_source)
            val label = displayLinkName(name)
            val sourceKey = source.first?.url ?: source.second?.uri?.toString()
            addRow(label, R.drawable.ic_baseline_audiotrack_24, sourceKey == selectedSourceKey) {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                onSourceSelected(source)
                dismiss()
            }
        }

        if (audioTracks.size > 1) {
            val divider = View(context).apply { setBackgroundColor(0x33FFFFFF) }
            binding.sheetContent.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply {
                topMargin = dp(12); bottomMargin = dp(12)
            })
            val heading = TextView(context).apply {
                text = "Audio tracks"
                setTextColor(gray)
                textSize = 12f
                setPadding(dp(8), 0, dp(8), dp(6))
            }
            binding.sheetContent.addView(heading)
            audioTracks.forEach { track ->
                val selected = track.id == selectedAudio?.id && track.formatIndex == selectedAudio?.formatIndex
                addRow(audioLabel(track), R.drawable.ic_baseline_audiotrack_24, selected) {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    onAudioSelected(track)
                    renderAudio(audioTab, subTab)
                }
            }
        }
    }

    private fun renderSubtitles(audioTab: TextView, subTab: TextView) {
        selectTab(audioTab, false)
        selectTab(subTab, true)
        binding.sheetTitle.visibility = View.GONE
        binding.subtitleToggleRow.visibility = View.VISIBLE
        binding.subtitleActions.visibility = View.VISIBLE
        binding.sheetContent.removeAllViews()
        subtitleRows.clear()

        binding.subtitleToggleRow.removeAllViews()
        val label = TextView(context).apply {
            text = "Subtitles"
            setTextColor(white)
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
        }
        val toggle = Switch(context).apply {
            isChecked = subtitlesEnabled
            buttonTintList = ColorStateList.valueOf(red)
            setOnCheckedChangeListener { _, checked ->
                onSubtitlesToggled(checked)
                binding.sheetContent.alpha = if (checked) 1f else .45f
                binding.sheetContent.isEnabled = checked
            }
        }
        binding.subtitleToggleRow.addView(label)
        binding.subtitleToggleRow.addView(toggle)
        binding.sheetContent.alpha = if (subtitlesEnabled) 1f else .45f
        binding.sheetContent.isEnabled = subtitlesEnabled

        if (subtitles.isEmpty()) {
            addRow(context.getString(R.string.no_subtitles_loaded), R.drawable.ic_baseline_closed_caption_24, false) {}
        } else {
            val displayNames = subtitles.map(::subtitleDisplayName)
            val duplicateNames = displayNames.groupingBy { it }.eachCount()
            subtitles.forEachIndexed { index, subtitle ->
                val selected = subtitle == currentSubtitle
                val displayName = displayNames[index]
                val label = if (duplicateNames[displayName] == 1) displayName else {
                    val tag = subtitle.languageCode?.takeIf { it.isNotBlank() } ?: subtitle.originalName
                    "$displayName ($tag)"
                }
                val row = addRow(subtitleLabel(subtitle, label), R.drawable.ic_baseline_closed_caption_24, selected) {
                    if (!subtitlesEnabled) return@addRow
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    currentSubtitle = subtitle
                    subtitleRows.forEach { (item, itemRow) -> updateRowSelection(itemRow, item == currentSubtitle) }
                    onSubtitleSelected(subtitle)
                }
                subtitleRows += subtitle to row
            }
        }

        val settings = ImageView(context).apply {
            setImageResource(R.drawable.ic_baseline_settings_24)
            imageTintList = ColorStateList.valueOf(gray)
            contentDescription = "Subtitle settings"
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onSubtitleSettings() }
        }
        val online = actionText(context.getString(R.string.player_load_subtitles_online)) { onSearchOnline() }
        binding.subtitleActions.addView(settings, LinearLayout.LayoutParams(dp(42), dp(42)))
        binding.subtitleActions.addView(online, LinearLayout.LayoutParams(0, dp(42), 1f))
    }

    private fun renderQuality() {
        binding.sheetTitle.visibility = View.VISIBLE
        binding.subtitleToggleRow.visibility = View.GONE
        binding.subtitleActions.visibility = View.GONE
        binding.sheetContent.removeAllViews()
        qualityRows.clear()
        videoTracks.forEach { track ->
            val selected = track.id == currentVideo?.id && track.width == currentVideo?.width && track.height == currentVideo?.height
            val row = addRow(videoLabel(track), R.drawable.ic_baseline_audiotrack_24, selected) {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                currentVideo = track
                lastUserTapAt = SystemClock.uptimeMillis()
                qualityRows.forEach { (item, itemRow) -> updateRowSelection(itemRow, item == currentVideo) }
                onVideoSelected(track)
            }
            qualityRows += track to row
        }
    }

    fun refreshSelection(track: VideoTrack?) {
        if (!isShowing) return
        if (track == currentVideo) return
        if (SystemClock.uptimeMillis() - lastUserTapAt < SELECTION_REFRESH_DEBOUNCE_MS) return
        currentVideo = track
        qualityRows.forEach { (item, row) -> updateRowSelection(row, item == currentVideo) }
    }

    fun refreshSubtitleSelection(subtitle: SubtitleData?) {
        if (!isShowing || subtitle == currentSubtitle) return
        currentSubtitle = subtitle
        subtitleRows.forEach { (item, row) -> updateRowSelection(row, item == currentSubtitle) }
    }

    private fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(white)
        textSize = 13f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(14), 0, dp(14), dp(6))
    }

    private fun addRow(label: String, icon: Int, selected: Boolean, click: (View) -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(52)
            setPadding(dp(14), 0, dp(10), 0)
            background = rounded(if (selected) 0x1AE50914 else 0x0FFFFFFF, 30, if (selected) red else null)
            isClickable = true
            setOnClickListener { click(this) }
        }
        val iconView = ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(if (selected) red else gray)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) }
        }
        val text = TextView(context).apply {
            this.text = label
            setTextColor(if (selected) white else gray)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val radio = ImageView(context).apply {
            setImageResource(R.drawable.ic_baseline_check_24)
            imageTintList = ColorStateList.valueOf(if (selected) white else gray)
            background = radioBackground(selected)
            visibility = if (selected) View.VISIBLE else View.INVISIBLE
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        }
        row.addView(iconView); row.addView(text); row.addView(radio)
        binding.sheetContent.addView(row, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })
        return row
    }

    private fun updateRowSelection(row: View, selected: Boolean) {
        val container = row as? LinearLayout ?: return
        container.background = rounded(if (selected) 0x1AE50914 else 0x0FFFFFFF, 30, if (selected) red else null)
        (container.getChildAt(0) as? ImageView)?.imageTintList = ColorStateList.valueOf(if (selected) red else gray)
        (container.getChildAt(1) as? TextView)?.setTextColor(if (selected) white else gray)
        (container.getChildAt(2) as? ImageView)?.apply {
            imageTintList = ColorStateList.valueOf(if (selected) white else gray)
            background = radioBackground(selected)
            visibility = if (selected) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun tab(text: String, active: Boolean) = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 14f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(8), 0, dp(8), 0)
        selectTab(this, active)
    }

    private fun selectTab(view: TextView, active: Boolean) {
        view.setTextColor(if (active) white else gray)
        view.background = rounded(if (active) 0xB3E50914.toInt() else 0x00000000, 20, if (active) red else null)
    }

    private fun actionText(text: String, click: () -> Unit) = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(red)
        textSize = 13f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setOnClickListener { click() }
        setPadding(dp(4), 0, dp(4), 0)
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun radioBackground(selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        if (selected) setColor(red) else { setColor(Color.TRANSPARENT); setStroke(dp(1), gray) }
    }

    /** V35: maps a raw audio track language/label to a real language name. */
    private fun normalizeAudioLanguage(language: String?, label: String?): String {
        val direct = com.lagradost.cloudstream3.ui.result.normalizeLanguageName(language, fallback = "")
        if (direct.isNotBlank()) return direct
        val fromLabel = com.lagradost.cloudstream3.ui.result.normalizeLanguageName(label, fallback = "")
        if (fromLabel.isNotBlank()) return fromLabel
        // Neither the language tag nor the label is a language: fall back to the label
        // text itself (still better than a wrong language name).
        return label?.takeIf { it.isNotBlank() } ?: "Audio"
    }

    private fun audioLabel(track: AudioTrack): String {
        // V35: normalize the raw HLS LANGUAGE/NAME attribute; junk like "Vela" or
        // "atlas" must never be shown as a language name.
        val language = normalizeAudioLanguage(track.language, track.label)
        val channels = when (track.channelCount) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "" }
        return listOf(language, channels, track.sampleMimeType?.substringAfter('/')?.uppercase().orEmpty()).filter { it.isNotBlank() }.joinToString(" • ")
    }

    private fun videoLabel(track: VideoTrack): String = track.label ?: track.height?.takeIf { it > 0 }?.let { "${it}p" } ?: "Auto"
    private fun subtitleDisplayName(subtitle: SubtitleData): String =
        resolveLanguageName(subtitle.languageCode)?.takeIf { it.isNotBlank() }
            ?: subtitle.originalName

    private fun resolveLanguageName(code: String?): String? {
        if (code.isNullOrBlank()) return null
        return fromTagToEnglishLanguageName(code)
            ?: code.substringBefore('-').takeIf { it != code }?.let(::fromTagToEnglishLanguageName)
    }

    private fun subtitleLabel(subtitle: SubtitleData, displayName: String = subtitleDisplayName(subtitle)): String =
        displayName + subtitle.nameSuffix.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
    private fun weightParams() = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val SELECTION_REFRESH_DEBOUNCE_MS = 1500L
    }
}
