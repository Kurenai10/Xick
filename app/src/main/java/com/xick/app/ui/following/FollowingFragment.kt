package com.xick.app.ui.following

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
import com.xick.app.R
import com.xick.app.databinding.FragmentFollowingBinding
import com.xick.app.repository.KickRepository
import com.xick.app.ui.main.openChannelOverlay
import com.xick.app.util.AuthManager
import com.xick.app.util.FollowState
import com.xick.app.util.KickApi
import com.xick.app.util.KickWebActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.util.Log

/**
 * "Takip ettiklerim": the channels the account follows that are live now, most viewers first.
 *
 * The follow list is FollowState, kept up to date by the follow and unfollow actions. It is
 * seeded once from the list read earlier on the device, leaving out the two channels that were
 * wrongly picked up from the page. Each channel's live state comes from Kick's public channel info.
 * Nothing is read from the Kick page any more.
 */
class FollowingFragment : Fragment() {

    private var binding: FragmentFollowingBinding? = null
    private var capture: WebView? = null
    private val main = Handler(Looper.getMainLooper())
    private val repository = KickRepository()
    private val adapter = FollowedAdapter(
        onClick = { slug -> requireContext().openChannelOverlay(slug) },
        onLongClick = { slug -> confirmUnfollow(slug) },
    )

    private val cachePrefs by lazy {
        requireContext().getSharedPreferences("xick_following", Context.MODE_PRIVATE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentFollowingBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        b.signInButton.visibility = View.GONE
        b.followingList.layoutManager = LinearLayoutManager(requireContext())
        b.followingList.adapter = adapter
        b.followingSwipe.setOnRefreshListener { load() }

        migrateOnce()
        // Read the followed list from the page's own request (see setupCapture), if we do not have it yet.
        setupCapture(view as ViewGroup)
        // Show the last known live list at once, then refresh the live state.
        show(loadCache(), finished = false)
        load()
    }

    /** Reads the live state of every followed channel and refreshes the list. */
    private fun load() {
        val b = binding ?: return
        val slugs = FollowState.all(requireContext()).sorted()
        if (slugs.isEmpty()) {
            b.followingSwipe.isRefreshing = false
            // The list has not arrived from Kick yet: keep "loading" and the sign-in prompt, do not claim "no channels".
            if (capture != null) return
            show(emptyList(), finished = true)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val known = loadCache().associateBy { it.slug }
            val rows = mutableListOf<FollowedRow>()
            coroutineScope {
                slugs.map { slug ->
                    launch(Dispatchers.IO) {
                        val row = buildRow(slug, known[slug]?.avatarUrl)
                        withContext(Dispatchers.Main) {
                            rows.add(row)
                            show(rows.toList(), finished = false)
                        }
                    }
                }.joinAll()
            }
            saveCache(rows.toList())
            show(rows.toList(), finished = true)
            binding?.followingSwipe?.isRefreshing = false
        }
    }

    /** Shows the live channels, most viewers first; when finished and none are live, says so. */
    private fun show(rows: List<FollowedRow>, finished: Boolean) {
        val b = binding ?: return
        val live = rows.filter { it.isLive }.sortedByDescending { it.viewers }
        if (live.isNotEmpty()) {
            b.messageText.visibility = View.GONE
            b.followingList.visibility = View.VISIBLE
            adapter.submit(live)
        } else {
            b.followingList.visibility = View.GONE
            b.messageText.visibility = View.VISIBLE
            b.messageText.text = if (finished) {
                "Şu an canlı takip ettiğin kanal yok"
            } else {
                getString(R.string.loading)
            }
        }
    }

    /** Long press on a card: ask, then unfollow on Kick and refresh the list. */
    private fun confirmUnfollow(slug: String) {
        val activity = activity ?: return
        android.app.AlertDialog.Builder(activity)
            .setTitle("Takipten çıkılsın mı?")
            .setMessage(slug)
            .setPositiveButton("Takipten çık") { _, _ ->
                KickWebActions.setFollow(activity, slug, follow = false) { message ->
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    load()
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    /**
     * Removes entries that were never channels (site links from the page) and the two channels
     * the user confirmed they do not follow. Runs once; other doubts are removed by long press.
     */
    private fun migrateOnce() {
        if (cachePrefs.getBoolean("cleaned_v4", false)) return
        val wrong = setOf("cookie-policy", "category", "rammus53", "gokhanoner")
        val cleaned = FollowState.all(requireContext()).filterNot { it in wrong }.toSet()
        FollowState.replaceAll(requireContext(), cleaned)
        cachePrefs.edit().putBoolean("cleaned_v4", true).apply()
    }

    /** One channel: live state, title, game and thumbnail from the channel info; avatar from the search index. */
    private suspend fun buildRow(slug: String, knownAvatar: String?): FollowedRow = coroutineScope {
        val avatar = async {
            knownAvatar ?: runCatching {
                repository.search(slug).channels.firstOrNull { it.slug == slug }?.profilePicture
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
        val info = async { runCatching { repository.getChannel(slug) }.getOrNull() }
        val live = info.await()?.livestream
        FollowedRow(
            slug = slug,
            isLive = live?.isLive == true,
            viewers = live?.viewerCount ?: 0,
            title = live?.title,
            avatarUrl = avatar.await(),
            thumbUrl = live?.thumbnail?.url,
            category = live?.categories?.firstOrNull()?.name,
        )
    }

    private fun saveCache(rows: List<FollowedRow>) {
        runCatching {
            cachePrefs.edit()
                .putString("rows_v2", KickApi.json.encodeToString(ListSerializer(FollowedRow.serializer()), rows))
                .apply()
        }
    }

    private fun loadCache(): List<FollowedRow> = runCatching {
        KickApi.json.decodeFromString(
            ListSerializer(FollowedRow.serializer()),
            cachePrefs.getString("rows_v2", null) ?: "[]",
        )
    }.getOrDefault(emptyList())

    /**
     * Loads Kick's following page in a hidden WebView. A document-start script wraps fetch and XHR
     * before the page runs, so the request the page makes for its followed list is seen and its
     * response is handed to the app. The page's own session makes that request, so the list is the
     * account's real list, not a scrape of the page.
     */
    private fun setupCapture(root: ViewGroup) {
        val wv = WebView(requireContext()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            alpha = 0f
            addJavascriptInterface(CaptureBridge(), "XickCapture")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // Signed out on kick.com: ask for sign-in right away instead of loading for 20 s.
                    main.postDelayed({ checkSignedOutQuick() }, 2500L)
                }
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(wv, CAPTURE_JS, setOf("https://kick.com"))
        }
        capture = wv
        root.addView(wv, 0, ViewGroup.LayoutParams(1, 1))
        wv.loadUrl("https://kick.com/following")
        main.postDelayed(needSignIn, 20_000L)
    }

    /** No list after a while: ask for a Kick sign-in here (the page the list comes from). */
    private val needSignIn = Runnable {
        val b = binding ?: return@Runnable
        if (capture == null) return@Runnable
        b.messageText.text = "Takip listesi için Kick'e giriş yapman gerekiyor."
        b.messageText.visibility = View.VISIBLE
        b.signInButton.setText(R.string.sign_in)
        b.signInButton.visibility = View.VISIBLE
        b.signInButton.setOnClickListener { revealCapture() }
    }

    private fun checkSignedOutQuick() {
        val wv = capture ?: return
        if (wv.alpha > 0f) return // already shown for sign-in
        wv.evaluateJavascript("!!document.getElementById('navbar-account')") { result ->
            if (result == "false" && capture != null) {
                main.removeCallbacks(needSignIn)
                needSignIn.run()
            }
        }
    }

    /** Shows the Kick page full screen so the user can sign in there; the list is read once the session is there. */
    private fun revealCapture() {
        val wv = capture ?: return
        binding?.messageText?.visibility = View.GONE
        binding?.signInButton?.visibility = View.GONE
        wv.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
        )
        wv.alpha = 1f
        wv.visibility = View.VISIBLE
        wv.bringToFront()
        // Open Kick's sign-in form straight away (retries while the page is still hydrating).
        wv.evaluateJavascript(
            """
            (function tryOpen(n){
              if (document.querySelector('input[type="password"]')) return;
              var b = document.querySelector('[data-testid="login"]') || document.elementFromPoint(window.innerWidth - 36, 33);
              if (b) b.click();
              if (n > 0) setTimeout(function(){ tryOpen(n - 1); }, 1500);
            })(8);
            """.trimIndent(),
            null,
        )
        main.post(checkSignedIn)
    }

    /** Waits until the page shows the signed-in account, then reloads it so the list request is made with that session. */
    private val checkSignedIn = Runnable { checkSignedInNow() }

    private fun checkSignedInNow() {
        val wv = capture ?: return
        wv.evaluateJavascript("!!document.getElementById('navbar-account')") { result ->
            if (result == "true") {
                wv.reload()
                main.postDelayed(checkSignedIn, 6000L)
            } else {
                main.postDelayed(checkSignedIn, 3000L)
            }
        }
    }

    /** Receives each request the page makes whose address mentions followed / following. */
    private inner class CaptureBridge {
        @JavascriptInterface
        fun onCapture(url: String, status: Int, body: String) {
            // Kick's own list of the followed channels that are live; never ads or other sites.
            if (status != 200 || !url.startsWith("https://kick.com/api/v1/user/livestreams")) return
            val slugs = parseLiveFollowed(body)
            Log.d("XickCapture", "followed live channels=${slugs.size}")
            main.post { onListCaptured(slugs) }
        }
    }

    private fun onListCaptured(slugs: List<String>) {
        val ctx = context ?: return
        slugs.forEach { FollowState.set(ctx, it, true) }
        capture?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        capture = null
        binding?.signInButton?.visibility = View.GONE
        load()
    }

    override fun onDestroyView() {
        main.removeCallbacksAndMessages(null)
        capture?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        capture = null
        binding = null
        super.onDestroyView()
    }

    companion object {
        /** Wraps fetch and XHR so responses for followed-channel requests are reported to the app. */
        private const val CAPTURE_JS = """
(function(){
  function report(url, status, text){
    try { XickCapture.onCapture(String(url), status, String(text)); } catch (e) {}
  }
  var nativeFetch = window.fetch;
  if (nativeFetch) {
    window.fetch = function(input, init){
      var url = typeof input === 'string' ? input : (input && input.url) || '';
      var p = nativeFetch.apply(this, arguments);
      p.then(function(res){
        if (/\/api\/v1\/user\/livestreams/.test(url)) {
          res.clone().text().then(function(t){ report(url, res.status, t); });
        }
      }).catch(function(){});
      return p;
    };
  }
  var open = XMLHttpRequest.prototype.open;
  var send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function(method, url){ this.__xickUrl = url; return open.apply(this, arguments); };
  XMLHttpRequest.prototype.send = function(){
    var x = this;
    x.addEventListener('load', function(){
      if (/\/api\/v1\/user\/livestreams/.test(x.__xickUrl || '')) report(x.__xickUrl, x.status, x.responseText);
    });
    return send.apply(this, arguments);
  };
})();
"""

        /** Channel slugs from the page's live-followed response: the first list of channel objects found in it. */
        fun parseLiveFollowed(raw: String): List<String> {
            val root = runCatching { KickApi.json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
            val items = findObjectArray(root) ?: return emptyList()
            return items.mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                val channel = o["channel"] as? JsonObject
                (channel?.get("slug") ?: o["channel_slug"] ?: o["slug"] ?: o["username"])
                    ?.jsonPrimitive?.content?.lowercase()
            }.distinct()
        }

        private fun findObjectArray(el: JsonElement): JsonArray? {
            if (el is JsonArray) {
                if (el.isNotEmpty() && el[0] is JsonObject) return el
                for (v in el) findObjectArray(v)?.let { return it }
                return null
            }
            if (el is JsonObject) {
                for (v in el.values) findObjectArray(v)?.let { return it }
            }
            return null
        }

        /** Reads channel slugs from a followed-channels response: a list, or an object with a data list. */
        fun parseFollowedSlugs(raw: String): List<String> {
            val root = runCatching { KickApi.json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
            val items: JsonArray = when {
                root is JsonArray -> root
                root is JsonObject && root["data"] is JsonArray -> root["data"]!!.jsonArray
                else -> return emptyList()
            }
            return items.mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                val direct = o["slug"] ?: o["channel_slug"] ?: o["username"]
                val nested = (o["channel"] as? JsonObject)?.get("slug")
                    ?: (o["user"] as? JsonObject)?.get("username")
                (direct ?: nested)?.jsonPrimitive?.content
            }.distinct()
        }
    }
}
