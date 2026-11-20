package com.xick.app.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Official Kick sign-in (OAuth 2.1 + PKCE). The sign-in page is shown inside the
 * app's WebView; when Kick redirects to the callback address the code is read
 * there and exchanged for tokens. The client secret, if Kick requires it, comes
 * from the git-ignored local.properties (see app/build.gradle.kts).
 */
object KickOAuth {

    private const val PREFS = "xick_oauth"
    private const val CLIENT_ID = "01M7NW92NS813AME3TXV6KY9R4"
    private const val REDIRECT_URI = "https://kurenai10.github.io/xick-callback/"
    private const val SCOPES = "user:read channel:read chat:write"
    private const val AUTHORIZE_URL = "https://id.kick.com/oauth/authorize"
    private const val TOKEN_URL = "https://id.kick.com/oauth/token"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True when a navigation URL is the OAuth redirect (intercepted inside the WebView). */
    fun isRedirect(url: String): Boolean = url.startsWith(REDIRECT_URI)

    /** Builds the Kick sign-in URL and remembers the PKCE verifier and state. */
    fun buildAuthorizeUrl(): String {
        val verifier = randomUrlSafe(64)
        val state = randomUrlSafe(24)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        prefs().edit().putString("verifier", verifier).putString("state", state).apply()
        return Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build().toString()
    }

    /**
     * Handles xick://callback?code=...&state=... . Returns true when the tokens were
     * stored; false if the callback is missing data, has a wrong state, or the exchange failed.
     */
    suspend fun handleCallback(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val code = uri.getQueryParameter("code") ?: return@withContext false
        val state = uri.getQueryParameter("state")
        val p = prefs()
        if (state == null || state != p.getString("state", null)) return@withContext false
        val verifier = p.getString("verifier", null) ?: return@withContext false

        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", CLIENT_ID)
            .add("redirect_uri", REDIRECT_URI)
            .add("code", code)
            .add("code_verifier", verifier)
        if (com.xick.app.BuildConfig.KICK_CLIENT_SECRET.isNotBlank()) {
            form.add("client_secret", com.xick.app.BuildConfig.KICK_CLIENT_SECRET)
        }
        val request = Request.Builder().url(TOKEN_URL).post(form.build()).build()
        runCatching {
            KickApi.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val text = response.body?.string().orEmpty()
                storeTokens(KickApi.json.parseToJsonElement(text).jsonObject)
            }
        }.getOrDefault(false)
    }

    private fun storeTokens(json: JsonObject): Boolean {
        val access = json["access_token"]?.jsonPrimitive?.content ?: return false
        val refresh = json["refresh_token"]?.jsonPrimitive?.content
        prefs().edit()
            .putString("access_token", access)
            .putString("refresh_token", refresh)
            .remove("verifier").remove("state")
            .apply()
        return true
    }

    /** The bearer token for official API calls, or null when not signed in with Kick. */
    fun accessToken(): String? = prefs().getString("access_token", null)

    fun signOut() {
        prefs().edit().clear().apply()
    }

    private fun randomUrlSafe(bytes: Int): String {
        val buf = ByteArray(bytes)
        SecureRandom().nextBytes(buf)
        return base64Url(buf)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
