package com.xick.app.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.xick.app.R
import com.xick.app.ui.following.FollowedRow
import com.xick.app.util.image.KickImageLoader

/**
 * Profil list: each followed channel is only its avatar, name and a heart.
 * Tapping the row opens the channel; tapping the heart asks to unfollow.
 */
class ProfileFollowAdapter(
    private val onOpen: (String) -> Unit,
    private val onHeart: (String) -> Unit,
) : RecyclerView.Adapter<ProfileFollowAdapter.Holder>() {

    private val rows = mutableListOf<FollowedRow>()

    fun submit(list: List<FollowedRow>) {
        rows.clear()
        rows.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.profileRowAvatar)
        val name: TextView = view.findViewById(R.id.profileRowName)
        val heart: ImageButton = view.findViewById(R.id.profileRowHeart)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_profile_follow, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.avatar.load(row.avatarUrl, KickImageLoader.get(holder.itemView.context)) {
            transformations(CircleCropTransformation())
            crossfade(true)
        }
        holder.name.text = row.slug
        holder.heart.setOnClickListener { onHeart(row.slug) }
        holder.itemView.setOnClickListener { onOpen(row.slug) }
    }

    override fun getItemCount(): Int = rows.size
}
