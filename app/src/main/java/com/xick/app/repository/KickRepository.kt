package com.xick.app.repository

import com.xick.app.model.kick.BrowseStream
import com.xick.app.model.kick.Channel
import com.xick.app.model.kick.TopCategory
import com.xick.app.util.KickApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Single entry point the ViewModels talk to. Backed by KickApi (REST) and,
 * for paged lists (category streams), by Paging3 datasources under
 * repository/datasource/.
 *
 * Every call here goes through KickApi.client, whose Cache lets OkHttp
 * reuse Kick's own Cache-Control headers (Observed: channel info replies
 * with `public, max-age=14400`) instead of re-fetching unchanged JSON.
 */
class KickRepository {

    suspend fun getChannel(slug: String): Channel {
        val body = execute(KickApi.channelInfoRequest(slug))
        return KickApi.json.decodeFromString(Channel.serializer(), body)
    }

    suspend fun getPlaybackUrl(slug: String): String {
        val body = execute(KickApi.playbackUrlRequest(slug))
        val root = KickApi.json.parseToJsonElement(body).jsonObject
        return root["data"]?.jsonPrimitive?.content
            ?: error("playback-url response missing 'data' field")
    }

    /** Observed: GET kick.com/api/v1/categories/top -> top-level JSON array. */
    suspend fun getTopCategories(): List<TopCategory> {
        val body = execute(KickApi.topCategoriesRequest())
        return KickApi.json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(TopCategory.serializer()),
            body,
        )
    }

    /** Observed: GET /api/v1/subcategories?page=N returns a `data` array of categories. One page. */
    suspend fun getSubcategories(page: Int, limit: Int = 24): List<TopCategory> {
        val body = execute(KickApi.subcategoriesRequest(page, limit))
        val data = KickApi.json.parseToJsonElement(body).jsonObject["data"] ?: return emptyList()
        return KickApi.json.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(TopCategory.serializer()),
            data,
        )
    }

    /** Best-effort (needs sign-in, endpoint unverified) — see KickApi.sendMessageRequest. */
    suspend fun sendChatMessage(chatroomId: Long, content: String) {
        execute(KickApi.sendMessageRequest(chatroomId, content))
    }

    suspend fun getVideos(slug: String): List<com.xick.app.model.kick.Video> {
        val body = execute(KickApi.videosRequest(slug))
        return KickApi.json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(com.xick.app.model.kick.Video.serializer()), body,
        )
    }

    suspend fun getClips(slug: String): List<com.xick.app.model.kick.Clip> {
        val body = execute(KickApi.clipsRequest(slug))
        return KickApi.json.decodeFromString(com.xick.app.model.kick.ClipsResponse.serializer(), body).clips
    }

    /** VOD playback: resolve a video uuid to its HLS master .m3u8 (the `source` field). */
    suspend fun getVodSource(uuid: String): String? {
        val body = execute(KickApi.videoSourceRequest(uuid))
        return KickApi.json.parseToJsonElement(body).jsonObject["source"]?.jsonPrimitive?.content
    }

    /** Needs a signed-in session. Returns the followed channels as BrowseStream-like entries where possible. */
    suspend fun getFollowed(): String = execute(KickApi.followedRequest())

    /** Observed: GET search.kick.com/api/v1/search?query=<q>. */
    suspend fun search(query: String): com.xick.app.model.kick.SearchData {
        val body = execute(KickApi.searchRequest(query))
        return KickApi.json.decodeFromString(
            com.xick.app.model.kick.SearchResponse.serializer(), body,
        ).data
    }

    /** Confirmed (Observed) — see model/kick/BrowseStream.kt. `categoryId == null` = all categories. */
    suspend fun getCategoryStreams(
        categoryId: Int?,
        offset: Int = 0,
        limit: Int = 24,
        language: String? = null,
        sort: String? = null,
    ): List<BrowseStream> {
        val body = execute(KickApi.livestreamsRequest(categoryId, offset, limit, sort = sort, language = language))
        val root = KickApi.json.parseToJsonElement(body).jsonObject
        val streams = root["data"]?.jsonObject?.get("livestreams") ?: return emptyList()
        return KickApi.json.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(BrowseStream.serializer()),
            streams,
        )
    }

    // OkHttp's Call.execute() is a blocking call — this was originally called
    // directly from a `suspend fun`, which does NOT move it off the calling
    // thread by itself. (Observed, real-device crash): that bug put a
    // blocking network call straight on the UI thread via Paging3's initial
    // load, which Android kills outright with NetworkOnMainThreadException.
    private suspend fun execute(request: okhttp3.Request): String = withContext(Dispatchers.IO) {
        val response = KickApi.client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) error("Kick API ${request.url} -> HTTP ${it.code}")
            it.body?.string() ?: error("Kick API ${request.url} -> empty body")
        }
    }
}
