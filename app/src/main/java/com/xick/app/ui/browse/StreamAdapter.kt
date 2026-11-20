package com.xick.app.ui.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.xick.app.R
import com.xick.app.databinding.ItemStreamBinding
import com.xick.app.model.kick.BrowseStream
import com.xick.app.ui.channel.ChannelFragment
import com.xick.app.ui.main.openChannelOverlay
import com.xick.app.ui.channel.ChannelProfileFragment
import com.xick.app.util.DataSaverPolicy
import com.xick.app.util.image.KickImageLoader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Xtra-style stream card. Thumbnails deliberately come from the smallest
 * acceptable responsive variant (DataSaverPolicy.pickThumbnailUrl) rather
 * than Kick's largest — see util/DataSaverPolicy.kt.
 */
class StreamAdapter(
    private val navActionId: Int = R.id.action_browse_to_channel,
) : PagingDataAdapter<BrowseStream, StreamAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStreamBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, navActionId)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let(holder::bind)
    }

    class ViewHolder(
        private val binding: ItemStreamBinding,
        private val navActionId: Int,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(stream: BrowseStream) {
            val context = binding.root.context
            val loader = KickImageLoader.get(context)

            binding.title.text = stream.translatedTitle?.takeIf { it.isNotBlank() } ?: stream.title
            binding.channelName.text = stream.channel.username
            binding.category.text = stream.category.name
            binding.viewers.text = context.getString(R.string.viewer_count_format, formatCount(stream.viewerCount))
            binding.uptime.text = formatUptime(stream.startTime)?.let {
                context.getString(R.string.uptime_format, it)
            }.orEmpty()
            binding.language.text = languageLabel(stream.language)

            val quality = DataSaverPolicy.currentQuality(context)
            val thumbUrl = DataSaverPolicy.pickThumbnailUrl(
                responsiveSrcset = stream.thumbnail?.srcset,
                fallback = stream.thumbnail?.src,
                quality = quality,
            )
            binding.thumbnail.load(thumbUrl, loader)
            binding.avatar.load(stream.channel.profilePic, loader) {
                transformations(CircleCropTransformation())
                crossfade(true)
            }

            binding.root.setOnClickListener { view ->
                view.context.openChannelOverlay(stream.channel.slug)
            }
            // Tapping the avatar opens the channel profile (VODs / clips) instead of the live stream.
            binding.avatar.setOnClickListener { view ->
                val args = Bundle().apply {
                    putString(ChannelProfileFragment.ARG_SLUG, stream.channel.slug)
                }
                view.findNavController().navigate(R.id.action_global_channelProfile, args)
            }
        }

        /** 68900 -> "68,9 B" (Kick's own web UI abbreviates the same way). */
        private fun formatCount(count: Int): String = when {
            count >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f M", count / 1_000_000f)
            count >= 1_000 -> String.format(Locale.getDefault(), "%.1f B", count / 1_000f)
            else -> count.toString()
        }

        /** start_time is UTC ("2026-11-17T00:03:39Z") -> "1:41:26". minSdk 24-safe (no java.time). */
        private fun formatUptime(startTime: String?): String? {
            if (startTime.isNullOrBlank()) return null
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val start = runCatching { parser.parse(startTime) }.getOrNull() ?: return null
            var seconds = (System.currentTimeMillis() - start.time) / 1000
            if (seconds < 0) return null
            val h = seconds / 3600; seconds %= 3600
            val m = seconds / 60; val s = seconds % 60
            return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        }

        private fun languageLabel(code: String?): String {
            if (code.isNullOrBlank()) return ""
            return runCatching {
                Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
                    .replaceFirstChar { it.uppercase(Locale.getDefault()) }
            }.getOrDefault(code)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BrowseStream>() {
            override fun areItemsTheSame(oldItem: BrowseStream, newItem: BrowseStream) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: BrowseStream, newItem: BrowseStream) = oldItem == newItem
        }
    }
}
