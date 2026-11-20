package com.xick.app.ui.channel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.xick.app.R
import com.xick.app.repository.KickRepository
import com.xick.app.ui.vod.VodPlayerFragment
import com.xick.app.util.image.KickImageLoader
import kotlinx.coroutines.launch
import java.util.Locale

/** One tab of the channel profile: a list of VODs or clips for a channel. */
class VideoListFragment : Fragment() {

    private val slug: String by lazy { requireArguments().getString(ARG_SLUG)!! }
    private val isClips: Boolean by lazy { requireArguments().getBoolean(ARG_CLIPS) }
    private val repository = KickRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            id = R.id.videoList
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view as RecyclerView
        val adapter = VideoRowAdapter(::openVod, ::openClip)
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            if (isClips) {
                val clips = runCatching { repository.getClips(slug) }.getOrDefault(emptyList())
                adapter.submitClips(clips)
            } else {
                val vids = runCatching { repository.getVideos(slug) }.getOrDefault(emptyList())
                adapter.submitVideos(vids)
            }
        }
    }

    private fun openVod(uuid: String) {
        val args = Bundle().apply { putString(VodPlayerFragment.ARG_VOD_UUID, uuid) }
        findNavController().navigate(R.id.action_channelProfile_to_vod, args)
    }

    private fun openClip(url: String) {
        val args = Bundle().apply { putString(VodPlayerFragment.ARG_DIRECT_URL, url) }
        findNavController().navigate(R.id.action_channelProfile_to_vod, args)
    }

    companion object {
        const val ARG_SLUG = "slug"
        const val ARG_CLIPS = "clips"

        fun create(slug: String, clips: Boolean) = VideoListFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SLUG, slug)
                putBoolean(ARG_CLIPS, clips)
            }
        }
    }
}

private sealed interface Row {
    data class Vod(val video: com.xick.app.model.kick.Video) : Row
    data class ClipRow(val clip: com.xick.app.model.kick.Clip) : Row
}

private class VideoRowAdapter(
    val onVod: (String) -> Unit,
    val onClip: (String) -> Unit,
) : RecyclerView.Adapter<VideoRowAdapter.VH>() {

    private val rows = mutableListOf<Row>()

    fun submitVideos(v: List<com.xick.app.model.kick.Video>) {
        rows.clear(); rows.addAll(v.map { Row.Vod(it) }); notifyDataSetChanged()
    }

    fun submitClips(c: List<com.xick.app.model.kick.Clip>) {
        rows.clear(); rows.addAll(c.map { Row.ClipRow(it) }); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(rows[position])
    override fun getItemCount() = rows.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val thumb = view.findViewById<ImageView>(R.id.thumbnail)
        private val duration = view.findViewById<TextView>(R.id.duration)
        private val title = view.findViewById<TextView>(R.id.title)
        private val meta = view.findViewById<TextView>(R.id.meta)

        fun bind(row: Row) {
            val ctx = itemView.context
            val loader = KickImageLoader.get(ctx)
            when (row) {
                is Row.Vod -> {
                    title.text = row.video.title ?: ctx.getString(R.string.vod)
                    meta.text = ctx.getString(R.string.views_format, formatCount(row.video.views))
                    duration.text = formatDurationMs(row.video.duration)
                    thumb.load(row.video.thumbnail?.src, loader)
                    itemView.setOnClickListener { row.video.video?.uuid?.let(onVod) }
                }
                is Row.ClipRow -> {
                    title.text = row.clip.title.ifBlank { ctx.getString(R.string.clip) }
                    meta.text = ctx.getString(R.string.views_format, formatCount(row.clip.viewCount)) +
                        (row.clip.creator?.username?.let { " · $it" } ?: "")
                    duration.text = formatDurationSec(row.clip.duration)
                    thumb.load(row.clip.thumbnailUrl, loader)
                    itemView.setOnClickListener { row.clip.videoUrl?.let(onClip) }
                }
            }
        }

        private fun formatCount(c: Int) = when {
            c >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", c / 1_000_000f)
            c >= 1_000 -> String.format(Locale.getDefault(), "%.1fB", c / 1_000f)
            else -> c.toString()
        }

        private fun formatDurationSec(sec: Int): String {
            val m = sec / 60; val s = sec % 60
            return String.format(Locale.getDefault(), "%d:%02d", m, s)
        }

        private fun formatDurationMs(ms: Long): String {
            val total = ms / 1000
            val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
            return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
            else String.format(Locale.getDefault(), "%d:%02d", m, s)
        }
    }
}
