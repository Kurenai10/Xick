package com.xick.app.ui.games

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.xick.app.databinding.FragmentGamesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** "Oyunlar" tab: top live categories as a 3-column poster grid, infinite scroll. */
class GamesFragment : Fragment() {

    private var binding: FragmentGamesBinding? = null
    private val viewModel: GamesViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentGamesBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        val adapter = CategoryAdapter()
        b.categoryGrid.layoutManager = GridLayoutManager(requireContext(), 3)
        b.categoryGrid.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collectLatest { adapter.submitData(it) }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
