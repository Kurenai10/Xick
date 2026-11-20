package com.xick.app.ui.channel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.xick.app.ui.main.openChannelOverlay
import androidx.viewpager2.adapter.FragmentStateAdapter
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.tabs.TabLayoutMediator
import com.xick.app.R
import com.xick.app.databinding.FragmentChannelProfileBinding
import com.xick.app.util.image.KickImageLoader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/** Channel profile: header (avatar / name / followers) + Videolar & Klipler tabs. */
class ChannelProfileFragment : Fragment() {

    private var binding: FragmentChannelProfileBinding? = null
    private val slug: String by lazy { requireArguments().getString(ARG_SLUG)!! }
    private val viewModel: ChannelViewModel by viewModels { factory { ChannelViewModel(slug, loadPlayback = false) } }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentChannelProfileBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        b.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int) =
                VideoListFragment.create(slug, clips = position == 1)
        }
        TabLayoutMediator(b.tabLayout, b.pager) { tab, pos ->
            tab.text = getString(if (pos == 0) R.string.tab_videos else R.string.tab_clips)
        }.attach()

        b.username.text = slug
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                if (state is ChannelUiState.Loaded) {
                    val ch = state.channel
                    b.username.text = ch.slug
                    b.followers.text = getString(R.string.followers_format, formatCount(ch.followersCount))
                    b.avatar.load(ch.livestream?.thumbnail?.url, KickImageLoader.get(requireContext())) {
                        transformations(CircleCropTransformation())
                    }
                    if (ch.livestream?.isLive == true) {
                        b.watchLiveButton.visibility = View.VISIBLE
                        b.watchLiveButton.setOnClickListener {
                            requireContext().openChannelOverlay(slug)
                        }
                    }
                }
            }
        }
    }

    private fun formatCount(c: Int) = when {
        c >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", c / 1_000_000f)
        c >= 1_000 -> String.format(Locale.getDefault(), "%.1fB", c / 1_000f)
        else -> c.toString()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_SLUG = "slug"
    }
}

private fun <T : ViewModel> factory(create: () -> T) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <U : ViewModel> create(modelClass: Class<U>): U = create() as U
}
