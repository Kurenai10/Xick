package com.xick.app.model.kick

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Confirmed (Observed, 2026-11-17 capture of GET /api/v2/channels/{slug}/info
 * -> .livestream) field shape for a single channel's current livestream.
 * The category-browse/paged-list endpoint (repository/datasource) response
 * shape is still Assumed to match this and needs its own capture.
 */
@Serializable
data class Stream(
    @SerialName("id") val id: Long,
    @SerialName("slug") val slug: String,
    @SerialName("channel_id") val channelId: Long,
    @SerialName("session_title") val title: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("is_mature") val isMature: Boolean = false,
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("thumbnail") val thumbnail: Thumbnail? = null,
    @SerialName("categories") val categories: List<StreamCategory> = emptyList(),
)

@Serializable
data class Thumbnail(
    @SerialName("url") val url: String? = null,
)

@Serializable
data class StreamCategory(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("slug") val slug: String,
)
