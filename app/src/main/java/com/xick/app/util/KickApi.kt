package com.xick.app.util

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Thin REST client for Kick's endpoints, confirmed live from kick.com's own
 * network traffic (2026-11-17 capture, anonymous/no-auth session):
 *
 *   GET /api/v2/channels/{slug}/info          -> channel + livestream + chatroom.id
 *   GET /api/v2/channels/{slug}/playback-url  -> { "data": "<signed .m3u8 URL>" }
 *   GET /api/v1/channels/{slug}/links
 *   GET /api/v2/channels/{slug}/leaderboards
 *   GET /api/v2/channels/{slug}/polls
 *   GET /api/v2/channels/{slug}/goals
 *   GET /emotes/{slug}                        -> channel emote set
 *   GET /api/v1/categories/top
 *   GET /api/internal/settings/global
 *
 * IMPORTANT (Observed): kick.com sits behind Cloudflare Bot Management —
 * the page loads /cdn-cgi/challenge-platform/... scripts and a JS challenge
 * runs before these endpoints respond. A bare OkHttp client with no cookie
 * jar / JS execution may get 403'd. Mitigation to validate once the rest of
 * the pipeline works: route first request through a hidden WebView to pick
 * up the clearance cookie, then reuse its cookie jar in OkHttp.
 */
object KickApi {

    private const val BASE_URL = "https://kick.com"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Not yet initialized with a Cache until [init] runs (needs a Context
     * for the cache directory). Data-saving reason this exists at all:
     * channel info / category listings / emote sets are cheap to let
     * OkHttp serve from disk on a Cache-Control-respecting revalidation
     * instead of re-downloading identical JSON every time a screen reopens.
     */
    @Volatile
    var client: OkHttpClient = OkHttpClient.Builder().build()
        private set

    private const val CACHE_SIZE_BYTES = 10L * 1024 * 1024 // 10 MB — plenty for this API's JSON responses

    fun init(context: Context) {
        val cacheDir = java.io.File(context.cacheDir, "kick_http_cache")
        // Allow more parallel requests per host (default is 5), so the followed-channel
        // lookups finish in fewer rounds.
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 24
        }
        client = OkHttpClient.Builder()
            .cache(Cache(cacheDir, CACHE_SIZE_BYTES))
            .cookieJar(AuthManager.cookieJar) // signed-in requests ride on the WebView login's session cookie
            .dispatcher(dispatcher)
            .build()
    }

    // Kick answers this with "max-age=14400" (4 hours). Without FORCE_NETWORK the app's
    // HTTP cache would keep old thumbnails and live state for hours.
    fun channelInfoRequest(slug: String): Request =
        Request.Builder()
            .url("$BASE_URL/api/v2/channels/$slug/info")
            .cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
            .build()

    fun playbackUrlRequest(slug: String): Request =
        Request.Builder()
            .url("$BASE_URL/api/v2/channels/$slug/playback-url")
            .build()

    fun emotesRequest(slug: String): Request =
        Request.Builder()
            .url("$BASE_URL/emotes/$slug")
            .build()

    fun topCategoriesRequest(): Request =
        Request.Builder()
            .url("$BASE_URL/api/v1/categories/top")
            .build()

    /**
     * CONFIRMED (Observed, 2026-11-17, by watching the web client's own
     * traffic): this mints the JWT the Centrifugo `connect` frame needs —
     * NOT websockets.kick.com/viewer/v1/token (that returns an unrelated
     * ULID the server rejects with "invalid token", verified on a real
     * device). The web client POSTs its anon/guest id (a random UUID it
     * keeps in the `kick:anon-id:v1` cookie) and gets back
     * {"data":{"token":"<JWT sub=guest:uuid>"}}. Signed-in users send the
     * same shape with their session cookie (via AuthManager.cookieJar), so
     * the JWT then represents the real user and chat can be sent.
     */
    fun realtimeAuthRequest(clientId: String): Request =
        Request.Builder()
            .url("https://web.kick.com/api/v1/realtime/auth/connection")
            .post("{\"client_id\":\"$clientId\"}".toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("x-app-platform", "web")
            .header("Referer", "https://kick.com")
            .build()

    /**
     * Confirmed (Observed, 2026-11-17): the category/browse stream grid —
     * see model/kick/BrowseStream.kt's doc for the full finding. Lives on
     * web.kick.com, not kick.com, and takes a category_id (numeric), not
     * a slug. Pagination is a plain offset wrapped as base64("{\"o\":N}").
     */
    /**
     * BEST-EFFORT, unverified: sending chat requires a signed-in session and
     * this endpoint/body shape is Kick's widely used public one, not one
     * captured in this project (no real login has been captured yet).
     */
    fun sendMessageRequest(chatroomId: Long, content: String): Request {
        val body = buildString {
            append("{\"content\":")
            append(kotlinx.serialization.json.JsonPrimitive(content).toString()) // JSON-escaped string literal
            append(",\"type\":\"message\"}")
        }
        val builder = Request.Builder()
            .url("$BASE_URL/api/v2/messages/send/$chatroomId")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
        AuthManager.sessionToken()?.let { builder.header("Authorization", "Bearer $it") }
        AuthManager.xsrfToken()?.let { builder.header("X-XSRF-TOKEN", it) }
        return builder.build()
    }

    /** `categoryId == null` = all categories (the "Popüler" tab) — Observed working without the param. */
    fun livestreamsRequest(
        categoryId: Int?,
        offset: Int = 0,
        limit: Int = 24,
        displayLanguage: String = "en",
        sort: String? = null,     // viewer_count_desc already applied below; this adds a second sort override when non-null
        language: String? = null, // Observed filter param on the browse feed
    ): Request {
        // (Observed, real-device bug) Base64.NO_WRAP alone still pads with
        // '=' — Kick's server 500s on a padded `after` cursor; stripping it
        // (confirmed against the live API) is what the web client's own
        // btoa()-based encoding produces and what the server actually wants.
        val cursor = android.util.Base64.encodeToString(
            "{\"o\":$offset}".toByteArray(),
            android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
        return Request.Builder()
            .url(
                "https://web.kick.com/api/v1/livestreams" +
                    "?limit=$limit&sort=${sort ?: "viewer_count_desc"}" +
                    (categoryId?.let { "&category_id=$it" } ?: "") +
                    (if (language != null) "&language=$language" else "") +
                    "&display_language=$displayLanguage&after=$cursor",
            )
            .build()
    }

    /**
     * Observed: GET kick.com/api/v1/subcategories?page=N&limit=24 — the full
     * paginated category list (the 12-item categories/top is just a teaser).
     * Response: a `data` array of category objects (TopCategory shape).
     */
    fun subcategoriesRequest(page: Int, limit: Int = 24): Request =
        Request.Builder()
            .url("$BASE_URL/api/v1/subcategories?page=$page&limit=$limit&sort=viewers")
            .header("Accept", "application/json")
            .build()

    /** Observed: GET /api/v2/channels/{slug}/videos -> array of past broadcasts. */
    fun videosRequest(slug: String): Request =
        Request.Builder()
            .url("$BASE_URL/api/v2/channels/$slug/videos?sort=date&time=all")
            .header("Accept", "application/json")
            .build()

    /** Observed: GET /api/v2/channels/{slug}/clips -> {"clips":[...]}. */
    fun clipsRequest(slug: String): Request =
        Request.Builder()
            .url("$BASE_URL/api/v2/channels/$slug/clips?sort=view&time=all")
            .header("Accept", "application/json")
            .build()

    /** Observed: GET /api/v1/video/{uuid} -> `source` is the VOD's HLS master .m3u8. */
    fun videoSourceRequest(uuid: String): Request =
        Request.Builder()
            .url("$BASE_URL/api/v1/video/$uuid")
            .header("Accept", "application/json")
            .build()

    /** The signed-in account (needs the session cookie; not signed in is refused). */
    fun webUserRequest(): Request =
        Request.Builder()
            .url("$BASE_URL/api/v1/user")
            .header("Accept", "application/json")
            .build()

    /** Observed: GET /api/v2/channels/followed -> followed channels (needs the session cookie; 401 otherwise). */
    fun followedRequest(): Request =
        Request.Builder()
            .url("$BASE_URL/api/v2/channels/followed")
            .header("Accept", "application/json")
            .build()

    /**
     * Observed: GET search.kick.com/api/v1/search?query=<q> — returns
     * {"data":{"categories":[...],"channels":[...],"livestreams":[...]}}.
     */
    fun searchRequest(query: String): Request =
        Request.Builder()
            .url(
                "https://search.kick.com/api/v1/search?query=" +
                    java.net.URLEncoder.encode(query, "UTF-8"),
            )
            .header("Accept", "application/json")
            .build()
}
