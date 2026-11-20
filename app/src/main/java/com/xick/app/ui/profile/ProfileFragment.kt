package com.xick.app.ui.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.xick.app.R
import com.xick.app.databinding.FragmentProfileBinding
import com.xick.app.repository.KickRepository
import com.xick.app.ui.following.FollowedRow
import com.xick.app.ui.main.openChannelOverlay
import com.xick.app.util.AuthManager
import com.xick.app.util.KickApi
import com.xick.app.util.KickOAuth
import com.xick.app.util.FollowState
import com.xick.app.util.KickWebActions
import android.webkit.CookieManager
import com.xick.app.util.image.KickImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * "Profil": the signed-in account, inside the app (never the Kick web page).
 *
 * - Picture and name: Kick's official API (GET /public/v1/users) with the OAuth token.
 * - Follower count: the public channel info of the account (same name as the slug).
 * - Followed channels: the list the Takip tab keeps on the device; long-press to unfollow.
 *
 * Kick's official API has no list of followers (only the count), so the followers'
 * names cannot be shown here.
 */
class ProfileFragment : Fragment() {

    private var binding: FragmentProfileBinding? = null
    private val repository = KickRepository()
    private val adapter = ProfileFollowAdapter(
        onOpen = { slug -> requireContext().openChannelOverlay(slug) },
        onHeart = { slug -> confirmUnfollow(slug) },
    )

    private val prefs by lazy {
        requireContext().getSharedPreferences("xick_following", Context.MODE_PRIVATE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentProfileBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        b.profileFollowList.layoutManager = LinearLayoutManager(requireContext())
        b.profileFollowList.adapter = adapter
        b.profileLogin.setOnClickListener { findNavController().navigate(R.id.loginFragment) }
        b.profileLogout.setOnClickListener {
            AuthManager.signOut()
            KickOAuth.signOut()
            // Forget the follow list and its cache too, so the next account starts clean.
            FollowState.clearAll(requireContext())
            requireContext().getSharedPreferences("xick_following", Context.MODE_PRIVATE).edit().clear().apply()
            CookieManager.getInstance().flush()
            showSignedOut()
        }

        if (!AuthManager.isSignedIn() || KickOAuth.accessToken() == null) {
            showSignedOut()
        } else {
            load()
        }
    }

    private fun showSignedOut() {
        val b = binding ?: return
        b.profileName.text = ""
        b.profileAvatar.setImageDrawable(null)
        b.profileMessage.text = "Giriş yapmadın."
        b.profileFollowers.text = ""
        b.profileFollowing.text = ""
        b.profileEmpty.visibility = View.GONE
        b.profileFollowList.visibility = View.GONE
        b.profileLogin.visibility = View.VISIBLE
        b.profileLogout.visibility = View.GONE
    }

    private fun load() {
        val b = binding ?: return
        b.profileMessage.text = getString(R.string.loading)
        b.profileLogin.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val me = withContext(Dispatchers.IO) { fetchMe() }
            val v = binding ?: return@launch
            if (me == null) {
                v.profileName.text = ""
                v.profileMessage.text = "Profil alınamadı. Çıkış yapıp yeniden giriş yap."
                v.profileLogin.visibility = View.VISIBLE
                v.profileLogout.visibility = View.VISIBLE
                return@launch
            }
            v.profileName.text = me.first
            v.profileAvatar.load(me.second, KickImageLoader.get(requireContext())) {
                transformations(CircleCropTransformation())
                crossfade(true)
            }
            v.profileMessage.text = "Kick hesabı"
            v.profileLogout.visibility = View.VISIBLE

            val followers = withContext(Dispatchers.IO) {
                runCatching { repository.getChannel(me.first.lowercase()).followersCount }.getOrNull()
            }
            binding?.profileFollowers?.text = followers?.let { "${formatCount(it)} takipçi" }
                ?: "Takipçi sayısı alınamadı"
            showFollowList()
        }
    }

    /** Shows the followed channels: live first, then by viewers. Empty means the Takip tab was not opened yet. */
    private fun showFollowList() {
        val b = binding ?: return
        val rows = loadRows().sortedWith(compareByDescending<FollowedRow> { it.isLive }.thenByDescending { it.viewers })
        b.profileFollowing.text = "${rows.size} takip"
        if (rows.isEmpty()) {
            b.profileFollowList.visibility = View.GONE
            b.profileEmpty.text = "Takip listesi boş. Takip sekmesini açınca burada görünür."
            b.profileEmpty.visibility = View.VISIBLE
        } else {
            b.profileEmpty.visibility = View.GONE
            b.profileFollowList.visibility = View.VISIBLE
            adapter.submit(rows)
        }
    }

    /** Long press: ask, then unfollow in the web session and drop the channel from the list. */
    private fun confirmUnfollow(slug: String) {
        val activity = activity ?: return
        android.app.AlertDialog.Builder(activity)
            .setTitle("Takipten çıkılsın mı?")
            .setMessage(slug)
            .setPositiveButton("Takipten çık") { _, _ ->
                KickWebActions.setFollow(activity, slug, follow = false) { message ->
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    if (message == "Takipten çıkıldı" || message == "Zaten takip edilmiyor") {
                        saveRows(loadRows().filterNot { it.slug == slug })
                        showFollowList()
                    }
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    /** The followed-channel rows the Takip tab stored on the device. */
    private fun loadRows(): List<FollowedRow> = runCatching {
        KickApi.json.decodeFromString(
            ListSerializer(FollowedRow.serializer()),
            prefs.getString("rows_v2", null) ?: "[]",
        )
    }.getOrDefault(emptyList())

    private fun saveRows(rows: List<FollowedRow>) {
        runCatching {
            prefs.edit()
                .putString("rows_v2", KickApi.json.encodeToString(ListSerializer(FollowedRow.serializer()), rows))
                .apply()
        }
    }

    /** The account's name and picture from the official API; null when they cannot be read. */
    private fun fetchMe(): Pair<String, String?>? {
        val token = KickOAuth.accessToken() ?: return null
        val request = Request.Builder()
            .url("https://api.kick.com/public/v1/users")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        return runCatching {
            KickApi.client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val root = KickApi.json.parseToJsonElement(r.body?.string().orEmpty())
                val user: JsonObject = when {
                    root is JsonObject && root["data"] is JsonArray ->
                        root["data"]!!.jsonArray.firstOrNull()?.jsonObject
                    root is JsonArray -> root.firstOrNull()?.jsonObject
                    root is JsonObject -> root
                    else -> null
                } ?: return@use null
                val name = (user["name"] ?: user["username"])?.jsonPrimitive?.content ?: return@use null
                val picture = user["profile_picture"]?.jsonPrimitive?.content
                name to picture
            }
        }.getOrNull()
    }

    private fun formatCount(count: Int): String = when {
        count >= 1_000_000 -> String.format(java.util.Locale.getDefault(), "%.1f M", count / 1_000_000f)
        count >= 1_000 -> String.format(java.util.Locale.getDefault(), "%.1f B", count / 1_000f)
        else -> count.toString()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
