package com.xick.app.model.kick

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Confirmed (Observed, 2026-11-17): the real category/browse list shape —
 * distinct from the single-channel `Stream` in Stream.kt (that one comes
 * from GET /api/v2/channels/{slug}/info's nested .livestream and has a
 * different field set entirely; don't conflate the two).
 *
 *   GET https://web.kick.com/api/v1/livestreams
 *       ?limit=24&sort=viewer_count_desc&category_id=<id>
 *       &display_language=<lang>&after=<base64("{\"o\":<offset>}")>
 *   -> { "data": { "livestreams": [ BrowseStream, ... ] } }
 *
 * Pagination is a plain offset, base64-wrapped as {"o": <offset>} — no
 * opaque server cursor to round-trip, so the next page's `after` can be
 * computed client-side (see KickApi.livestreamsRequest).
 */
@Serializable
data class BrowseStream(
    @SerialName("id") val id: String, // UUID here, NOT the numeric id from the single-channel endpoint
    @SerialName("title") val title: String,
    @SerialName("translated_title") val translatedTitle: String? = null,
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("thumbnail") val thumbnail: BrowseThumbnail? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("channel") val channel: BrowseChannel,
    @SerialName("category") val category: BrowseCategory,
    @SerialName("language") val language: String? = null,
    @SerialName("is_mature") val isMature: Boolean = false,
)

@Serializable
data class BrowseThumbnail(
    @SerialName("src") val src: String? = null,
    @SerialName("srcset") val srcset: String? = null, // standard `url Nw, url Nw, ...` — DataSaverPolicy.pickThumbnailUrl parses this directly
)

@Serializable
data class BrowseChannel(
    @SerialName("id") val id: Long,
    @SerialName("slug") val slug: String,
    @SerialName("username") val username: String,
    @SerialName("profile_pic") val profilePic: String? = null,
)

@Serializable
data class BrowseCategory(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("slug") val slug: String,
)
