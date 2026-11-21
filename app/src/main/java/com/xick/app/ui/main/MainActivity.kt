package com.xick.app.ui.main

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.xick.app.databinding.ActivityMainBinding
import com.xick.app.ui.channel.ChannelFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide the status/notification icons app-wide: they cover the top of the
        // screen (and the fullscreen controls under them). Swipe from the top
        // edge still reveals them transiently.
        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, binding.root)
        insetsController.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())

        val navHost = supportFragmentManager
            .findFragmentById(binding.navHostFragment.id) as NavHostFragment
        binding.bottomNav.setupWithNavController(navHost.navController)

        // Keep the content above the tab bar only while the tab bar is shown;
        // when a fragment hides it (fullscreen), the content must fill the screen.
        binding.bottomNav.viewTreeObserver.addOnGlobalLayoutListener {
            val host = binding.navHostFragment.layoutParams as ViewGroup.MarginLayoutParams
            val wanted = if (binding.bottomNav.visibility == android.view.View.VISIBLE) {
                binding.bottomNav.height
            } else 0
            if (host.bottomMargin != wanted) {
                host.bottomMargin = wanted
                binding.navHostFragment.layoutParams = host
            }
        }
    }

    /** Opens a live channel as an overlay so the tab underneath stays alive. */
    fun openChannel(slug: String) {
        supportFragmentManager.popBackStackImmediate(CHANNEL_TAG, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val fragment = ChannelFragment().apply {
            arguments = Bundle().apply { putString(ChannelFragment.ARG_CHANNEL_SLUG, slug) }
        }
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .add(binding.miniOverlay.id, fragment, CHANNEL_TAG)
            .addToBackStack(CHANNEL_TAG)
            .commit()
    }

    companion object {
        const val CHANNEL_TAG = "channel_overlay"
    }
}

/** Finds the hosting MainActivity from any context (fragments can be wrapped). */
fun Context.openChannelOverlay(slug: String) {
    var c: Context? = this
    while (c != null) {
        if (c is MainActivity) { c.openChannel(slug); return }
        c = (c as? ContextWrapper)?.baseContext
    }
}
