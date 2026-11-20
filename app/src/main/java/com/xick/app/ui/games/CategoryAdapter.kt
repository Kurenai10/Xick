package com.xick.app.ui.games

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.xick.app.R
import com.xick.app.databinding.ItemCategoryBinding
import com.xick.app.model.kick.TopCategory
import com.xick.app.ui.browse.BrowseFragment
import com.xick.app.util.image.KickImageLoader
import java.util.Locale

/** Tapping a category opens BrowseFragment filtered to that category_id. */
class CategoryAdapter : PagingDataAdapter<TopCategory, CategoryAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let(holder::bind)
    }

    class ViewHolder(private val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: TopCategory) {
            val context = binding.root.context
            binding.name.text = category.name
            binding.viewers.text = context.getString(R.string.viewer_count_format, formatCount(category.viewers))
            binding.banner.load(category.banner?.bestUrl, KickImageLoader.get(context))
            binding.root.setOnClickListener { view ->
                val args = Bundle().apply { putInt(BrowseFragment.ARG_CATEGORY_ID, category.id) }
                view.findNavController().navigate(R.id.action_games_to_category, args)
            }
        }

        private fun formatCount(count: Int): String = when {
            count >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f M", count / 1_000_000f)
            count >= 1_000 -> String.format(Locale.getDefault(), "%.1f B", count / 1_000f)
            else -> count.toString()
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TopCategory>() {
            override fun areItemsTheSame(oldItem: TopCategory, newItem: TopCategory) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: TopCategory, newItem: TopCategory) = oldItem == newItem
        }
    }
}
