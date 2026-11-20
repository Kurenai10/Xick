package com.xick.app.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Follow / unfollow one channel in the signed-in Kick web session.
 *
 * The official API has no follow endpoint, so the channel page is loaded in a hidden
 * 1x1 WebView and its follow button is clicked. The page is loaded once and kept while
 * the channel is open, so a tap acts at once instead of waiting for a fresh page load.
 */
class FollowSession(private val activity: Activity, private val slug: String) {

    private val main = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var ready = false
    private var pending = false
    private var busy = false
    private var wantFollow = false
    private var callback: ((String) -> Unit)? = null
    private val timeout = Runnable { deliver("timeout") }

    /** Loads the channel page in the background (safe to call more than once). */
    fun start() {
        if (webView != null) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val wv = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true // the hidden page must not play the stream
            alpha = 0f
            isClickable = false
            isFocusable = false
            addJavascriptInterface(Bridge(), "XickFollowBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (!url.startsWith("https://kick.com")) return
                    ready = true
                    if (pending) {
                        pending = false
                        runScript()
                    }
                }
            }
        }
        webView = wv
        root.addView(wv, FrameLayout.LayoutParams(1, 1))
        wv.loadUrl("https://kick.com/$slug")
    }

    /** Follow (true) or unfollow (false). [onResult] receives a Turkish message when done. */
    fun toggle(follow: Boolean, onResult: (String) -> Unit) {
        if (busy) {
            onResult("Takip işlemi zaten sürüyor")
            return
        }
        busy = true
        wantFollow = follow
        callback = onResult
        main.postDelayed(timeout, 12_000L)
        if (webView == null) start()
        if (ready) runScript() else pending = true
    }

    private fun runScript() {
        webView?.evaluateJavascript(script(wantFollow), null)
    }

    private fun deliver(code: String) {
        main.removeCallbacks(timeout)
        if (!busy) return
        busy = false
        val want = wantFollow
        val message = when (code) {
            "clicked" -> if (want) "Takip edildi" else "Takipten çıkıldı"
            "already" -> if (want) "Zaten takip ediliyor" else "Zaten takip edilmiyor"
            "notfound" -> "Takip düğmesi bulunamadı"
            else -> "Takip işlemi tamamlanamadı, tekrar dene"
        }
        // Success leaves the wanted state; any failure keeps the previous state (the opposite).
        val nowFollowed = if (code == "clicked" || code == "already") want else !want
        FollowState.set(activity, slug, nowFollowed)
        callback?.invoke(message)
        callback = null
    }

    /** Removes the hidden page. */
    fun release() {
        main.removeCallbacksAndMessages(null)
        webView?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        webView = null
        ready = false
        pending = false
        busy = false
        callback = null
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onResult(text: String) {
            main.post { deliver(text) }
        }
    }

    companion object {
        /**
         * Finds the follow control (data-testid="follow-button", or a text match), compares its
         * state with the wanted one, clicks it if needed and confirms the change by polling.
         */
        private fun script(want: Boolean): String = """
            (function(){
              var want = $want;
              function norm(s){ return (s || '').replace(/\s+/g, ' ').trim().toLowerCase(); }
              function label(e){ return norm(e.innerText || e.textContent) || norm(e.getAttribute('aria-label')) || norm(e.getAttribute('title')); }
              function findBtn(){
                var b = document.querySelector('[data-testid="follow-button"]');
                if (b) return b;
                var all = Array.prototype.slice.call(document.querySelectorAll('button,[role="button"],a'));
                return all.find(function(e){ var t = label(e); return /takip|follow/.test(t) && !/takipçi|follower/.test(t); }) || null;
              }
              function following(b){ return /takibi b[ıi]rak|takip ediliyor|following|unfollow/.test(label(b)); }
              var btn = findBtn();
              if (!btn) { XickFollowBridge.onResult('notfound'); return; }
              if (following(btn) === want) { XickFollowBridge.onResult('already'); return; }
              btn.click();
              var polls = 0;
              var timer = setInterval(function(){
                polls++;
                if (!want) {
                  var confirm = Array.prototype.slice.call(document.querySelectorAll('button')).find(function(b){
                    var t = label(b); return t === 'evet' || t === 'onayla';
                  });
                  if (confirm) confirm.click();
                }
                var now = findBtn();
                if (now && following(now) === want) { clearInterval(timer); XickFollowBridge.onResult('clicked'); return; }
                if (polls >= 30) { clearInterval(timer); XickFollowBridge.onResult('timeout'); }
              }, 150);
            })();
        """.trimIndent()
    }
}

/** One-shot follow / unfollow (used by the Takip list): loads, acts, then releases the page. */
object KickWebActions {
    fun setFollow(activity: Activity, slug: String, follow: Boolean, onResult: (String) -> Unit) {
        val session = FollowSession(activity, slug)
        session.start()
        session.toggle(follow) { message ->
            onResult(message)
            session.release()
        }
    }
}
