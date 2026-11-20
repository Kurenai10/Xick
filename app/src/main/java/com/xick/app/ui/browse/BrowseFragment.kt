package com.xick.app.ui.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.xick.app.R
import com.xick.app.databinding.FragmentBrowseBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The "Popüler" tab: the global live-stream list (all categories), the same
 * single-column card feed Xtra's Popular tab uses. When opened with a
 * [ARG_CATEGORY_ID] argument it instead shows that one category's streams
 * (used by the Oyunlar/categories flow).
 */
class BrowseFragment : Fragment() {

    private var binding: FragmentBrowseBinding? = null

    private val categoryId: Int?
        get() = arguments?.let { if (it.containsKey(ARG_CATEGORY_ID)) it.getInt(ARG_CATEGORY_ID) else null }

    private val viewModel: BrowseViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                BrowseViewModel(categoryId = categoryId) as T
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val b = FragmentBrowseBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        val navAction = if (categoryId != null) R.id.action_category_to_channel else R.id.action_browse_to_channel
        val adapter = StreamAdapter(navAction)
        b.streamGrid.layoutManager = LinearLayoutManager(requireContext())
        b.streamGrid.adapter = adapter

        // The search/filter bar only makes sense on the global Popüler feed.
        if (categoryId != null) {
            b.topBar.visibility = View.GONE
        } else {
            b.searchBox.setOnClickListener {
                findNavController().navigate(R.id.action_browse_to_search)
            }
            b.filterButton.setOnClickListener { showFilterDialog() }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.streams.collectLatest { adapter.submitData(it) }
        }
    }

    private fun showFilterDialog() {
        // Language codes Kick's browse filter accepts; label -> code (null = all).
        val languages = listOf(
            getString(R.string.lang_all) to null,
            "Türkçe" to "tr",
            "English" to "en",
            "Español" to "es",
            "Português" to "pt",
            "Deutsch" to "de",
            "العربية" to "ar",
            "Français" to "fr",
        )
        val labels = languages.map { it.first }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.filter_language)
            .setItems(labels) { _, which ->
                viewModel.setFilter(language = languages[which].second, sort = null)
            }
            .show()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_CATEGORY_ID = "categoryId"
    }
}
