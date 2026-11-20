package com.xick.app.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.xick.app.R
import com.xick.app.databinding.FragmentSearchBinding
import com.xick.app.model.kick.SearchChannel
import com.xick.app.repository.KickRepository
import com.xick.app.ui.browse.BrowseFragment
import com.xick.app.ui.channel.ChannelFragment
import com.xick.app.ui.main.openChannelOverlay
import com.xick.app.util.image.KickImageLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Live search over channels + categories (search.kick.com/api/v1/search), debounced. */
class SearchFragment : Fragment() {

    private var binding: FragmentSearchBinding? = null
    private val repository = KickRepository()
    private val adapter = SearchAdapter(
        onChannel = { slug ->
            requireContext().openChannelOverlay(slug)
        },
        onCategory = { id ->
            val args = Bundle().apply { putInt(BrowseFragment.ARG_CATEGORY_ID, id) }
            findNavController().navigate(R.id.action_search_to_category, args)
        },
    )
    private var searchJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentSearchBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        b.searchResults.layoutManager = LinearLayoutManager(requireContext())
        b.searchResults.adapter = adapter

        b.searchInput.doAfterTextChanged { text ->
            val q = text?.toString()?.trim().orEmpty()
            searchJob?.cancel()
            if (q.length < 2) { adapter.submit(emptyList()); return@doAfterTextChanged }
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(300) // debounce
                runCatching { repository.search(q) }.getOrNull()?.let { data ->
                    val rows = buildList {
                        data.channels.forEach { add(SearchRow.Channel(it)) }
                        data.categories.forEach { add(SearchRow.Category(it.id, it.name)) }
                    }
                    adapter.submit(rows)
                }
            }
        }
        b.searchInput.setOnEditorActionListener { _, id, _ -> id == EditorInfo.IME_ACTION_SEARCH }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}

sealed interface SearchRow {
    data class Channel(val channel: SearchChannel) : SearchRow
    data class Category(val id: Int, val name: String) : SearchRow
}

private class SearchAdapter(
    val onChannel: (String) -> Unit,
    val onCategory: (Int) -> Unit,
) : RecyclerView.Adapter<SearchAdapter.VH>() {

    private val items = mutableListOf<SearchRow>()
    fun submit(rows: List<SearchRow>) { items.clear(); items.addAll(rows); notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_search, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val icon = view.findViewById<ImageView>(R.id.icon)
        private val label = view.findViewById<TextView>(R.id.label)
        private val badge = view.findViewById<TextView>(R.id.badge)

        fun bind(row: SearchRow) {
            val ctx = itemView.context
            when (row) {
                is SearchRow.Channel -> {
                    label.text = row.channel.username
                    badge.text = if (row.channel.isLive) ctx.getString(R.string.live_badge) else ""
                    icon.load(row.channel.profilePicture.ifBlank { null }, KickImageLoader.get(ctx)) {
                        transformations(CircleCropTransformation())
                    }
                    itemView.setOnClickListener { onChannel(row.channel.slug) }
                }
                is SearchRow.Category -> {
                    label.text = row.name
                    badge.setText(R.string.category_badge)
                    icon.setImageResource(R.drawable.ic_games)
                    itemView.setOnClickListener { onCategory(row.id) }
                }
            }
        }
    }
}
