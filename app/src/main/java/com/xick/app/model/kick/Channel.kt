package com.xick.app.model.kick

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.xick.app.model.kick.chat.Chatroom
import com.xick.app.model.kick.Stream

/**
 * Confirmed (Observed) field shape from GET /api/v2/channels/{slug}/info —
 * trimmed to the fields this client actually needs; the real response also
 * carries banner_image, subscriber_badges, offline_banner_image, etc.
 */
@Serializable
data class Channel(
    @SerialName("id") val id: Long,
    @SerialName("slug") val slug: String,
    @SerialName("verified") val verified: Boolean = false,
    @SerialName("followers_count") val followersCount: Int = 0,
    @SerialName("subscription_enabled") val subscriptionEnabled: Boolean = false,
    @SerialName("chatroom") val chatroom: Chatroom,
    @SerialName("livestream") val livestream: Stream? = null,
)
