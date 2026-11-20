package com.xick.app.ui.channel

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.xick.app.R
import com.xick.app.databinding.FragmentChannelBinding
import com.xick.app.ui.chat.ChatAdapter
import com.xick.app.ui.chat.ChatViewModel
import com.xick.app.util.AuthManager
import com.xick.app.util.DataSaverPolicy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Xtra-style channel screen. Portrait: 16:9 video with an icon control
 * overlay (quality / aspect / fullscreen) on top, chat + input below.
 * Fullscreen: landscape, system bars hidden, video fills the screen, and a
 * chat toggle splits it 70/30 (video/chat) so chat stays usable.
 */
@UnstableApi
class ChannelFragment : Fragment() {

    private var binding: FragmentChannelBinding? = null
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null

    private val channelSlug: String by lazy { requireArguments().getString(ARG_CHANNEL_SLUG)!! }
    private val channelViewModel: ChannelViewModel by viewModels { factory { ChannelViewModel(channelSlug) } }
    private var chatViewModel: ChatViewModel? = null
    private val chatAdapter = ChatAdapter()

    private val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
    )
    private var miniGesture: MiniPlayerGesture? = null
    // Hidden channel page kept open while this channel is shown, so follow taps act at once.
    private var followSession: com.xick.app.util.FollowSession? = null
    private var backToPortraitCallback: androidx.activity.OnBackPressedCallback? = null
    private var resizeIndex = 0
    private var isFullscreen = false
    // Fullscreen starts with chat hidden so the video fills the screen; the
    // chat toggle brings up the 70/30 split.
    private var chatVisibleInFullscreen = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentChannelBinding.inflate(inflater, container, false)
        binding = b
        // Full-screen by default; the mini player shrinks this root into a corner.
        b.root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        )
        // Opaque background so the tab underneath does not show through the full-screen channel.
        b.root.setBackgroundColor(android.graphics.Color.parseColor("#16181D"))
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        // The channel is a detail page: hide the bottom tab bar so the chat
        // input at the bottom is actually on screen and tappable.
        setBottomNavVisible(false)
        applyPortraitLayout()
        backToPortraitCallback = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (isFullscreen) toggleFullscreen()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backToPortraitCallback!!)
        // Lock to portrait: rotating the phone must not turn the normal view.
        // Only the fullscreen button unlocks landscape (see toggleFullscreen).
        if (!isFullscreen) requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // The chat toggle only exists in fullscreen; portrait shows no split-chat button.
        b.playerView.findViewById<ImageButton>(R.id.btnChatToggle)?.visibility = View.GONE

        b.chatRecycler.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        b.chatRecycler.adapter = chatAdapter

        // Double-tap anywhere on the video toggles fullscreen, so it works even
        // when the controller (and its fullscreen icon) has auto-hidden.
        val doubleTap = android.view.GestureDetector(requireContext(),
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                    toggleFullscreen()
                    return true
                }

                override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                    toggleChrome()
                    return true
                }
            })
        // PlayerView consumes touches for its own show/hide controller, so a
        // listener on it never sees a double tap. Attach the detector to the
        // container instead, which receives the event in the dispatch chain.
        // Drag down -> shrink to a corner mini-player; drag sideways -> dismiss.
        miniGesture = MiniPlayerGesture(
            view = b.playerContainer,
            onProgress = { p -> applyMiniProgress(p) },
            onMinimize = { enterMiniPlayer() },
            onDismiss = { dismissMiniPlayer() },
            onHorizontalDrag = { dx -> binding?.root?.translationX = dx },
            onSnapBack = { binding?.root?.animate()?.translationX(0f)?.setDuration(150)?.start() },
            onTap = { restoreFullPlayer() },
        )
        // PlayerView consumes touches for its own controller, so the gesture is
        // attached to it directly and always observes the event stream.
        b.playerView.setOnTouchListener { _, ev ->
            doubleTap.onTouchEvent(ev)
            miniGesture?.onTouch(ev) ?: false
        }

        // The controller itself never auto-hides, so the bottom bar stays on screen.
        // Top bar and centre transport are shown/hidden by taps (see toggleChrome).
        b.playerView.controllerHideOnTouch = false
        b.playerView.setControllerShowTimeoutMs(0)
        b.playerView.showController()
        setChromeVisible(true)

        // Custom controller buttons live inside the PlayerView controller layout.
        b.playerView.findViewById<ImageButton>(R.id.btnQuality)?.setOnClickListener { showQualityDialog() }
        b.playerView.findViewById<ImageButton>(R.id.btnAspect)?.setOnClickListener { cycleAspect() }
        b.playerView.findViewById<ImageButton>(R.id.btnFullscreen)?.setOnClickListener { toggleFullscreen() }
        b.playerView.findViewById<ImageButton>(R.id.btnChatToggle)?.setOnClickListener { toggleFullscreenChat() }
        b.playerView.findViewById<View>(R.id.btnLive)?.setOnClickListener {
            if (usingRecording) switchSource(toRecording = false)
            else {
                player?.seekToDefaultPosition()
                player?.play()
            }
        }
        b.playerView.findViewById<View>(R.id.btnJump)?.setOnClickListener { showJumpDialog() }
        b.playerView.findViewById<ImageButton>(R.id.btnFollow)?.setOnClickListener { followThisChannel() }
        if (com.xick.app.util.AuthManager.isSignedIn()) {
            followSession = com.xick.app.util.FollowSession(requireActivity(), channelSlug).also { it.start() }
        }
        applyBottomLayout()
        updateFollowIcon()
        b.playerView.findViewById<ImageButton>(R.id.btnMore)?.setOnClickListener { showMoreMenu(it) }
        b.playerView.findViewById<ImageButton>(R.id.btnClose)?.setOnClickListener { parentFragmentManager.popBackStack() }
        b.playerView.findViewById<View>(R.id.btnSleep)?.setOnClickListener { showSleepDialog() }
        b.playerView.findViewById<android.widget.TextView>(R.id.playerChannelName)?.text = channelSlug

        updateChatInputState()
        b.sendButton.setOnClickListener { sendCurrentMessage() }
        b.messageInput.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) { sendCurrentMessage(); true } else false
        }
        // When not signed in, tapping the input sends the user to login instead
        // of opening a keyboard they can't use (chat send needs a session).
        b.messageInput.setOnClickListener {
            if (!AuthManager.isSignedIn()) {
                findNavController().navigate(R.id.action_global_login)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            channelViewModel.state.collectLatest { state ->
                when (state) {
                    is ChannelUiState.Loading -> b.titleText.setText(R.string.loading)
                    is ChannelUiState.Error -> b.titleText.text = state.message
                    is ChannelUiState.Loaded -> {
                        b.titleText.text = state.channel.livestream?.title ?: channelSlug
                        b.playerView.findViewById<android.widget.TextView>(R.id.playerTitle)?.text =
                            state.channel.livestream?.title.orEmpty()
                        b.playerView.findViewById<android.widget.TextView>(R.id.playerCategory)?.text =
                            state.channel.livestream?.categories?.firstOrNull()?.name.orEmpty()
                        showStreamInfo(state.channel.livestream?.viewerCount, state.channel.livestream?.startTime)
                        livePlaybackUrl = state.playbackUrl
                        if (!recordingChecked) {
                            recordingChecked = true
                            loadBroadcastRecording()
                        }
                        if (player == null) {
                            state.playbackUrl?.let(::startPlayback) ?: b.chatStatusText.setText(R.string.channel_offline)
                        }
                        ensureChat(state)
                    }
                }
            }
        }
    }

    // ---- Layout modes -------------------------------------------------------

    private fun applyPortraitLayout() {
        val b = binding ?: return
        b.contentLayout.orientation = LinearLayout.VERTICAL
        b.playerContainer.layoutParams = b.playerContainer.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            // Use the short screen edge: when leaving fullscreen the display may
            // still report landscape metrics, which would make the player huge.
            height = portraitWidthPx() * 9 / 16
        }
        b.playerView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        )
        b.chatColumn.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0,
        ).apply { weight = 1f }
        b.chatColumn.visibility = View.VISIBLE
    }

    /** Width of the screen in portrait orientation, regardless of the current rotation. */
    private fun portraitWidthPx(): Int {
        val m = resources.displayMetrics
        return minOf(m.widthPixels, m.heightPixels)
    }

    private fun applyFullscreenLayout() {
        val b = binding ?: return
        b.contentLayout.orientation = LinearLayout.HORIZONTAL
        if (chatVisibleInFullscreen) {
            // 70% video / 30% chat split.
            b.playerContainer.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                weight = 7f
            }
            b.chatColumn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                weight = 3f
            }
            b.chatColumn.visibility = View.VISIBLE
        } else {
            // Chat hidden: the video takes the whole screen (no leftover empty column).
            b.playerContainer.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
            b.chatColumn.visibility = View.GONE
        }
        // The PlayerView must also fill its container, otherwise the old
        // portrait height (16:9) stays and black bars remain in landscape.
        b.playerView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        )
        b.playerContainer.requestLayout()
        b.playerView.requestLayout()
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        // Back exits fullscreen first; only when already in portrait does it close the channel.
        backToPortraitCallback?.isEnabled = isFullscreen
        val activity = requireActivity()
        val fsBtn = binding?.playerView?.findViewById<ImageButton>(R.id.btnFullscreen)
        val chatBtn = binding?.playerView?.findViewById<ImageButton>(R.id.btnChatToggle)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)

        if (isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            setBottomNavVisible(false)
            fsBtn?.setImageResource(R.drawable.ic_fullscreen_exit)
            chatBtn?.visibility = View.VISIBLE
            // Fill the screen (crops slightly) so there are no side bars; the
            // aspect button still cycles back to fit/zoom/fill.
            binding?.playerView?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            applyFullscreenLayout()
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            // Keep the status bar hidden app-wide (see MainActivity); only the nav bar returns.
            controller.hide(WindowInsetsCompat.Type.statusBars())
            setBottomNavVisible(true)
            fsBtn?.setImageResource(R.drawable.ic_fullscreen)
            chatBtn?.visibility = View.GONE
            binding?.playerView?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            resizeIndex = 0
            applyPortraitLayout()
        }
    }

    /** Drag-down progress: the whole overlay follows the finger and the chat fades. */
    private fun applyMiniProgress(p: Float) {
        val b = binding ?: return
        b.root.translationY = p * resources.displayMetrics.heightPixels / 5f
        b.chatColumn.alpha = 1f - p
    }

    /**
     * Commits the mini-player: the overlay shrinks to a half-width box in the
     * bottom-right corner (above the tab bar). The tab underneath stays visible.
     */
    private fun enterMiniPlayer() {
        val b = binding ?: return
        b.chatColumn.visibility = View.GONE
        b.root.translationY = 0f
        b.root.translationX = 0f
        setBottomNavVisible(true)
        val density = resources.displayMetrics.density
        val margin = (12 * density).toInt()
        val navHeight = (56 * density).toInt()
        b.root.layoutParams = FrameLayout.LayoutParams(
            resources.displayMetrics.widthPixels / 2,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM or android.view.Gravity.END,
        ).apply { setMargins(margin, margin, margin, margin + navHeight) }
        // The player box must match the half-width overlay: 16:9 of that width,
        // not the full-screen height left over from portrait layout.
        val miniWidth = portraitWidthPx() / 2
        b.playerContainer.layoutParams = LinearLayout.LayoutParams(miniWidth, miniWidth * 9 / 16)
        b.playerView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        )
        b.root.requestLayout()
    }

    /** Tap on the mini-player: back to full screen with the chat. */
    private fun restoreFullPlayer() {
        val b = binding ?: return
        miniGesture?.reset()
        b.root.translationX = 0f
        b.root.translationY = 0f
        b.root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        )
        b.chatColumn.visibility = View.VISIBLE
        b.chatColumn.alpha = 1f
        setBottomNavVisible(false)
        applyPortraitLayout()
        b.root.requestLayout()
    }

    /** Dismisses the mini-player and stops playback. */
    private fun dismissMiniPlayer() {
        player?.stop()
        parentFragmentManager.popBackStack()
    }

    // ---- Sleep timer, audio-only, stream info ------------------------------

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null
    private var audioOnly = false
    private var streamViewers: Int? = null
    private var streamStartMs: Long? = null

    private fun showSleepDialog() {
        val options = arrayOf("15 dakika", "30 dakika", "60 dakika", "Kapat")
        val minutes = intArrayOf(15, 30, 60)
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Uyku zamanlayıcısı")
            .setItems(options) { _, which ->
                sleepRunnable?.let { handler.removeCallbacks(it) }
                sleepRunnable = null
                if (which < minutes.size) {
                    val runnable = Runnable {
                        player?.pause()
                        android.widget.Toast.makeText(requireContext(), "Uyku zamanlayıcısı: yayın durduruldu", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    sleepRunnable = runnable
                    handler.postDelayed(runnable, minutes[which] * 60_000L)
                    android.widget.Toast.makeText(requireContext(), "${minutes[which]} dakika sonra durdurulacak", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun toggleAudioOnly() {
        val exo = player ?: return
        audioOnly = !audioOnly
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
            .build()
    }

    private fun showStreamInfo(viewers: Int?, startTime: String?) {
        streamViewers = viewers
        streamStartMs = startTime?.let {
            runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .parse(it.take(19).replace(' ', 'T'))?.time
            }.getOrNull()
        }
        handler.removeCallbacks(infoTicker)
        handler.post(infoTicker)
    }

    private val infoTicker: Runnable = object : Runnable {
        override fun run() {
            renderStreamInfo()
            handler.postDelayed(this, 30_000L)
        }
    }

    private fun renderStreamInfo() {
        val tv = binding?.playerView?.findViewById<android.widget.TextView>(R.id.streamInfo) ?: return
        val viewers = streamViewers ?: return
        val start = streamStartMs
        val viewerText = getString(R.string.viewer_count_format, formatViewers(viewers))
        tv.text = if (start == null) {
            viewerText
        } else {
            val secs = ((System.currentTimeMillis() - start) / 1000).coerceAtLeast(0)
            val hms = String.format(java.util.Locale.US, "%d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60)
            "$viewerText  ·  ${getString(R.string.uptime_format, hms)}"
        }
    }

    private fun formatViewers(count: Int): String = when {
        count >= 1_000_000 -> String.format(java.util.Locale.getDefault(), "%.1f M", count / 1_000_000f)
        count >= 1_000 -> String.format(java.util.Locale.getDefault(), "%.1f B", count / 1_000f)
        else -> count.toString()
    }

    // ---- Controller chrome: bottom bar always on, top/centre by tap ---------

    private val hideChromeRunnable = Runnable { setChromeVisible(false) }

    private fun setChromeVisible(visible: Boolean) {
        val pv = binding?.playerView ?: return
        listOf(R.id.topBar, R.id.centerRow, R.id.actionStrip).forEach { id ->
            pv.findViewById<View>(id)?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        }
        handler.removeCallbacks(hideChromeRunnable)
        if (visible) handler.postDelayed(hideChromeRunnable, 3_000L)
    }

    private fun toggleChrome() {
        val shown = binding?.playerView?.findViewById<View>(R.id.topBar)?.visibility == View.VISIBLE
        setChromeVisible(!shown)
    }

    // ---- Full-length recording of the current broadcast ----------------------

    private var livePlaybackUrl: String? = null
    private var recordingUrl: String? = null
    private var recordingChecked = false
    private var usingRecording = false
    private val repository = com.xick.app.repository.KickRepository()

    /** Looks for today's recording of the live broadcast; the live VOD is seekable from its start. */
    private fun loadBroadcastRecording() {
        viewLifecycleOwner.lifecycleScope.launch {
            recordingUrl = runCatching {
                repository.getVideos(channelSlug).firstOrNull { it.isLive }?.source
            }.getOrNull()
        }
    }

    /** Follows the channel being watched, in the signed-in Kick web session. */
    private fun followThisChannel() {
        val activity = activity ?: return
        if (!com.xick.app.util.AuthManager.isSignedIn()) {
            android.widget.Toast.makeText(activity, "Takip etmek için önce giriş yap", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        // Heart toggles: filled green = following, outline = not following.
        val wantFollow = !com.xick.app.util.FollowState.isFollowed(activity, channelSlug)
        // Show the new state at once; the page confirms it (and reverts it on failure).
        com.xick.app.util.FollowState.set(activity, channelSlug, wantFollow)
        updateFollowIcon()
        val session = followSession
        if (session == null) {
            com.xick.app.util.FollowState.set(activity, channelSlug, !wantFollow)
            updateFollowIcon()
            return
        }
        session.toggle(wantFollow) { message ->
            android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show()
            updateFollowIcon()
        }
    }

    /** Bottom bar only in recording mode; the action pill sits above it (or low when live). */
    private fun applyBottomLayout() {
        val pv = binding?.playerView ?: return
        pv.findViewById<View>(R.id.bottomBar)?.visibility = if (usingRecording) View.VISIBLE else View.GONE
    }

    /** The more-options menu: audio-only mode and go-to-start (only when a recording exists). */
    private fun showMoreMenu(anchor: View) {
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, if (audioOnly) "Video modu" else "Sadece ses")
        if (recordingUrl != null) popup.menu.add(0, 2, 1, "Başa sar")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> toggleAudioOnly()
                2 -> if (usingRecording) player?.seekTo(0L) else switchSource(toRecording = true)
            }
            true
        }
        popup.show()
    }

    /** Shows the heart as filled green when this channel is followed, outline otherwise. */
    private fun updateFollowIcon() {
        val btn = binding?.playerView?.findViewById<ImageButton>(R.id.btnFollow) ?: return
        val following = com.xick.app.util.FollowState.isFollowed(requireContext(), channelSlug)
        btn.setImageResource(if (following) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
        btn.contentDescription = if (following) "Takibi bırak" else "Takip et"
    }

    /** Asks for a minute of the recording and seeks there. */
    private fun showJumpDialog() {
        val exo = player ?: return
        val duration = exo.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val totalMinutes = (duration / 60_000L).toInt()
        val input = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "0 – $totalMinutes dk"
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Yayının hangi dakikasına gidelim?")
            .setView(input)
            .setPositiveButton("Git") { _, _ ->
                val minute = input.text.toString().toIntOrNull()?.coerceIn(0, totalMinutes) ?: return@setPositiveButton
                exo.seekTo(minute * 60_000L)
                exo.play()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    /** Switches between the live edge and the full recording, keeping the sound settings. */
    private fun switchSource(toRecording: Boolean) {
        val target = (if (toRecording) recordingUrl else livePlaybackUrl) ?: return
        usingRecording = toRecording
        player?.release()
        player = null
        startPlayback(target)
        binding?.playerView?.findViewById<View>(R.id.btnJump)?.visibility =
            if (usingRecording) View.VISIBLE else View.GONE
        // The timeline is only meaningful for the recording; live shows no bottom bar.
        applyBottomLayout()
    }

    private fun toggleFullscreenChat() {
        // Only meaningful in fullscreen; in portrait the chat is already below the video.
        if (!isFullscreen) return
        chatVisibleInFullscreen = !chatVisibleInFullscreen
        applyFullscreenLayout()
    }

    private fun setBottomNavVisible(visible: Boolean) {
        requireActivity().findViewById<View?>(R.id.bottomNav)?.visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    private fun cycleAspect() {
        resizeIndex = (resizeIndex + 1) % resizeModes.size
        binding?.playerView?.resizeMode = resizeModes[resizeIndex]
    }

    // ---- Playback -----------------------------------------------------------

    private fun startPlayback(url: String) {
        val quality = DataSaverPolicy.currentQuality(requireContext())
        val selector = DefaultTrackSelector(requireContext()).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, DataSaverPolicy.maxVideoHeightPx(quality))
                .build()
        }
        trackSelector = selector
        val exo = ExoPlayer.Builder(requireContext())
            .setTrackSelector(selector)
            .setSeekBackIncrementMs(10_000)    // the exo_rew button steps 10s
            .setSeekForwardIncrementMs(10_000) // the exo_ffwd button steps 10s
            .build()
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
        binding?.playerView?.player = exo
        player = exo
    }

    private fun showQualityDialog() {
        val exo = player ?: return
        val selector = trackSelector ?: return
        val videoGroup = exo.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO } ?: run {
            Toast.makeText(requireContext(), R.string.quality_not_ready, Toast.LENGTH_SHORT).show(); return
        }
        val options = (0 until videoGroup.length).map { it to videoGroup.getTrackFormat(it) }
            .sortedByDescending { it.second.height }
        val labels = listOf(getString(R.string.quality_auto)) + options.map { (_, f) ->
            val fps = if (f.frameRate >= 50f) f.frameRate.toInt().toString() else ""
            "${f.height}p$fps"
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.quality)
            .setItems(labels.toTypedArray()) { _, which ->
                val p = selector.buildUponParameters().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                if (which == 0) {
                    p.setMaxVideoSize(Int.MAX_VALUE, DataSaverPolicy.maxVideoHeightPx(DataSaverPolicy.currentQuality(requireContext())))
                } else {
                    p.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                        .setOverrideForType(TrackSelectionOverride(videoGroup.mediaTrackGroup, options[which - 1].first))
                }
                selector.parameters = p.build()
            }
            .show()
    }

    // ---- Chat ---------------------------------------------------------------

    private fun ensureChat(state: ChannelUiState.Loaded) {
        if (chatViewModel != null) return
        val channel = state.channel
        val vm = ViewModelProvider(this, factory {
            ChatViewModel(channel.id, channel.chatroom.id, channel.livestream?.categories?.firstOrNull()?.id)
        })["chat_${channel.chatroom.id}", ChatViewModel::class.java]
        chatViewModel = vm

        viewLifecycleOwner.lifecycleScope.launch {
            vm.state.collectLatest { s ->
                binding?.chatStatusText?.text = when (s) {
                    ChatViewModel.ChatState.Connecting -> getString(R.string.chat_connecting)
                    ChatViewModel.ChatState.WaitingForServer -> getString(R.string.chat_waiting)
                    ChatViewModel.ChatState.Connected -> getString(R.string.chat_connected)
                    is ChatViewModel.ChatState.Unavailable -> getString(R.string.chat_unavailable, s.reason)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.messages.collectLatest {
                chatAdapter.submitList(it)
                if (it.isNotEmpty()) binding?.chatRecycler?.scrollToPosition(it.size - 1)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.sendErrors.collect { Toast.makeText(requireContext(), getString(R.string.send_failed, it), Toast.LENGTH_LONG).show() }
        }
    }

    private fun updateChatInputState() {
        val b = binding ?: return
        val signedIn = AuthManager.isSignedIn()
        // Input stays focusable only when signed in; otherwise it's a button to login.
        b.messageInput.isFocusable = signedIn
        b.messageInput.isFocusableInTouchMode = signedIn
        b.messageInput.hint = getString(if (signedIn) R.string.chat_input_hint else R.string.chat_input_sign_in)
    }

    override fun onResume() {
        super.onResume()
        updateChatInputState() // reflects a login that happened on another screen
    }

    private fun sendCurrentMessage() {
        val input = binding?.messageInput ?: return
        val text = input.text?.toString().orEmpty()
        if (text.isBlank()) return
        chatViewModel?.send(text)
        input.text?.clear()
    }

    override fun onDestroyView() {
        setBottomNavVisible(true) // restore the tab bar when leaving the channel
        if (isFullscreen) {
            // Restore portrait + bars so leaving fullscreen-while-playing doesn't strand the app.
            val activity = requireActivity()
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                .hide(WindowInsetsCompat.Type.statusBars())
            setBottomNavVisible(true)
        }
        handler.removeCallbacksAndMessages(null)
        followSession?.release()
        followSession = null
        // Give rotation back to the rest of the app once the channel is closed.
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        player?.release()
        player = null
        trackSelector = null
        chatViewModel = null
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_CHANNEL_SLUG = "channelSlug"
    }
}

private fun <T : ViewModel> factory(create: () -> T) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <U : ViewModel> create(modelClass: Class<U>): U = create() as U
}
