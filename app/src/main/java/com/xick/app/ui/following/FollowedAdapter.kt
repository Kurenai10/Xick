package com.xick.app.ui.following

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.xick.app.R
import com.xick.app.util.image.KickImageLoader
import kotlinx.serialization.Serializable

/** One followed channel as shown in the list (also what is cached on the device). */
@Serializable
data class FollowedRow(
    val slug: String,
    val isLive: Boolean,
    val viewers: Int,
    val title: String? = null,
    val avatarUrl: String? = null,
    val thumbUrl: String? = null,
    val category: String? = null,
)

/** Cards for the followed live channels; tapping one calls [onClick] with the channel slug. */
class FollowedAdapter(
    private val onClick: (String) -> Unit,
    private val onLongClick: (String) -> Unit = {},
) : RecyclerView.Adapter<FollowedAdapter.Holder>() {

    private val rows = mutableListOf<FollowedRow>()

    fun submit(list: List<FollowedRow>) {
        rows.clear()
        rows.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.followedThumb)
        val avatar: ImageView = view.findViewById(R.id.followedAvatar)
        val name: TextView = view.findViewById(R.id.followedName)
        val category: TextView = view.findViewById(R.id.followedCategory)
        val status: TextView = view.findViewById(R.id.followedStatus)
        val title: TextView = view.findViewById(R.id.followedTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_followed, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val loader = KickImageLoader.get(holder.itemView.context)
        holder.thumb.load(row.thumbUrl, loader) { crossfade(true) }
        holder.avatar.load(row.avatarUrl, loader) {
            transformations(CircleCropTransformation())
            crossfade(true)
        }
        holder.name.text = row.slug
        holder.category.text = row.category.orEmpty()
        if (row.isLive) {
            holder.status.text = "● CANLI  ${formatCount(row.viewers)}"
            holder.status.setTextColor(0xFF53FC18.toInt())
        } else {
            holder.status.text = "Çevrimdışı"
            holder.status.setTextColor(0xFF888888.toInt())
        }
        holder.title.text = row.title.orEmpty()
        holder.itemView.setOnClickListener { onClick(row.slug) }
        // Long press: unfollow (asked for confirmation in the fragment).
        holder.itemView.setOnLongClickListener { onLongClick(row.slug); true }
    }

    override fun getItemCount(): Int = rows.size

    private fun formatCount(count: Int): String = when {
        count >= 1_000_000 -> String.format(java.util.Locale.getDefault(), "%.1f M", count / 1_000_000f)
        count >= 1_000 -> String.format(java.util.Locale.getDefault(), "%.1f B", count / 1_000f)
        else -> count.toString()
    }
}
