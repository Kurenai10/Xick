package com.xick.app.ui.vod

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.xick.app.R
import com.xick.app.databinding.FragmentVodPlayerBinding
import com.xick.app.repository.KickRepository
import kotlinx.coroutines.launch

/**
 * Plays a VOD or a clip. A clip passes its direct HLS url in [ARG_DIRECT_URL];
 * a VOD passes its video uuid in [ARG_VOD_UUID], which is resolved to an HLS
 * master .m3u8 via /api/v1/video/{uuid}. Seeking works (full timeline), so
 * the controller keeps the default seek bar + 10s steps.
 */
@UnstableApi
class VodPlayerFragment : Fragment() {

    private var binding: FragmentVodPlayerBinding? = null
    private var player: ExoPlayer? = null
    private val repository = KickRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentVodPlayerBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val directUrl = requireArguments().getString(ARG_DIRECT_URL)
        val uuid = requireArguments().getString(ARG_VOD_UUID)

        if (directUrl != null) {
            play(directUrl)
        } else if (uuid != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val source = runCatching { repository.getVodSource(uuid) }.getOrNull()
                if (source != null) play(source) else showError()
            }
        } else {
            showError()
        }
    }

    private fun play(url: String) {
        val exo = ExoPlayer.Builder(requireContext())
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
        binding?.vodPlayerView?.player = exo
        player = exo
    }

    private fun showError() {
        binding?.vodError?.apply {
            visibility = View.VISIBLE
            setText(R.string.vod_unavailable)
        }
    }

    override fun onDestroyView() {
        player?.release()
        player = null
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_DIRECT_URL = "directUrl"
        const val ARG_VOD_UUID = "vodUuid"
    }
}
