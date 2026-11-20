package com.xick.app.ui.saved

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.xick.app.R

/**
 * "Kaydedildi" tab placeholder. Bookmarks/downloads need the Room entities
 * (db/) populated by features that don't exist yet, so this is an honest
 * empty state rather than a fake list.
 */
class SavedFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FrameLayout(requireContext()).apply {
            addView(
                TextView(requireContext()).apply {
                    setText(R.string.saved_empty)
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                },
            )
        }
    }
}
