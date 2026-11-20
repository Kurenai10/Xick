package com.xick.app.model.kick

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A past broadcast (VOD). Observed: GET /api/v2/channels/{slug}/videos
 * returns a top-level array of these. Playback: GET /api/v1/video/{uuid}
 * -> `source` (an HLS master .m3u8).
 */
@Serializable
data class Video(
    @SerialName("id") val id: Long,
    @SerialName("session_title") val title: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("duration") val duration: Long = 0, // ms
    @SerialName("views") val views: Int = 0,
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("source") val source: String? = null, // HLS master of the recording (full length)
    @SerialName("language") val language: String? = null,
    @SerialName("thumbnail") val thumbnail: BrowseThumbnail? = null,
    @SerialName("video") val video: VideoRef? = null,
    @SerialName("categories") val categories: List<StreamCategory> = emptyList(),
)

@Serializable
data class VideoRef(
    @SerialName("uuid") val uuid: String? = null,
)

/** Observed: GET /api/v2/channels/{slug}/clips returns a `clips` array. */
@Serializable
data class Clip(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String = "",
    @SerialName("video_url") val videoUrl: String? = null, // direct HLS .m3u8 — plays as-is
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("duration") val duration: Int = 0, // seconds
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("creator") val creator: ClipCreator? = null,
)

@Serializable
data class ClipCreator(
    @SerialName("username") val username: String = "",
)

@Serializable
data class ClipsResponse(
    @SerialName("clips") val clips: List<Clip> = emptyList(),
)
