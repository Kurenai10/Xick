package com.xick.app.util

import android.content.Context
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.net.URLDecoder
// KickApi referenced in verifySession; same util package.

/**
 * Session storage for the WebView-based login (ui/login). Sign-in happens
 * in a real WebView on kick.com (whose login is a modal behind bot
 * protection — not something to re-implement as a raw POST); the cookies
 * Android's CookieManager captures are then shared with OkHttp.
 *
 * How "signed in" is detected: LoginFragment watches the page and marks
 * the session once Kick's own login button (`[data-testid="login"]`) is
 * gone — the same thing a person sees. Which cookie carries the session is
 * NOT confirmed (no real login has been captured yet); [sessionToken]
 * assumes Kick's `session_token` cookie and should be checked after the
 * first real login.
 */
object AuthManager {

    private const val PREFS = "xick_auth"
    private const val KEY_SIGNED_IN = "signed_in"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val cookieJar: CookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val raw = runCatching { CookieManager.getInstance().getCookie(url.toString()) }.getOrNull()
                ?: return emptyList()
            return raw.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
        }
    }

    /**
     * The saved flag is only a hint. A stale flag from an earlier (wrong) login
     * must not show "signed in", so the flag is cleared unless the session is
     * confirmed by the server. Call this at app start (XickApplication).
     */
    suspend fun reconcileSession() {
        val hinted = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getBoolean(KEY_SIGNED_IN, false) == true
        // A stored official token means the user signed in; don't clear the flag at startup.
        if (hinted && KickOAuth.accessToken() == null && !verifySession()) {
            appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
                ?.putBoolean(KEY_SIGNED_IN, false)?.apply()
        }
    }

    fun isSignedIn(): Boolean =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getBoolean(KEY_SIGNED_IN, false) == true

    fun markSignedIn() {
        CookieManager.getInstance().flush()
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY_SIGNED_IN, true)?.apply()
    }

    /**
     * Real verification: a signed-in session can read /api/v2/channels/followed
     * (200); a guest gets 401. Used by the login flow so "signed in" reflects an
     * actual session, not just the login button disappearing (which also happens
     * when the user cancels the modal).
     */
    suspend fun verifySession(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            KickApi.client.newCall(KickApi.followedRequest()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    fun signOut() {
        CookieManager.getInstance().removeAllCookies(null)
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
    }

    fun sessionToken(): String? = cookieValue("session_token")

    fun xsrfToken(): String? = cookieValue("XSRF-TOKEN")

    private fun cookieValue(name: String): String? {
        val raw = runCatching { CookieManager.getInstance().getCookie("https://kick.com") }.getOrNull() ?: return null
        return raw.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
    }
}
