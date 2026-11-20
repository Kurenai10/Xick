package com.xick.app.model.kick.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Confirmed (Observed, 2026-11-17 WebSocket capture on kick.com/jahrein via
 * a WebSocket-constructor hook — real `App\Events\ChatMessageEvent` push
 * payloads, `.pub.data.data` already comes as a JSON *string* that must be
 * parsed a second time).
 *
 * Example raw frame (trimmed):
 * {"push":{"channel":"chatrooms.<id>.v2","pub":{"data":{
 *   "event":"App\\Events\\ChatMessageEvent",
 *   "data":"{\"id\":\"...\",\"chatroom_id\":...,\"content\":\"...\",
 *            \"type\":\"message\"|\"reply\",\"created_at\":\"...\",
 *            \"sender\":{...},\"metadata\":{...}}"
 * }}}}
 *
 * Emotes are inline text tokens in `content`: `[emote:<id>:<name>]` — not a
 * separate structured field. The chat renderer must regex-replace these.
 */
@Serializable
data class ChatMessage(
    @SerialName("id") val id: String,
    @SerialName("chatroom_id") val chatroomId: Long,
    @SerialName("content") val content: String,
    @SerialName("type") val type: String, // "message" | "reply"
    @SerialName("created_at") val createdAt: String,
    @SerialName("sender") val sender: ChatSender,
    @SerialName("metadata") val metadata: ChatMessageMetadata? = null,
)

@Serializable
data class ChatSender(
    @SerialName("id") val id: Long,
    @SerialName("username") val username: String,
    @SerialName("slug") val slug: String,
    @SerialName("identity") val identity: ChatIdentity,
)

@Serializable
data class ChatIdentity(
    @SerialName("color") val color: String? = null,
    // Legacy badge list ({"type","text","count"?,"sort_order"}); badges_v2 is the current one.
    @SerialName("badges") val badges: List<LegacyBadge> = emptyList(),
    @SerialName("badges_v2") val badgesV2: List<Badge> = emptyList(),
)

@Serializable
data class LegacyBadge(
    @SerialName("type") val type: String,
    @SerialName("text") val text: String,
    @SerialName("count") val count: Int? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class Badge(
    @SerialName("name") val name: String, // e.g. "level"
    @SerialName("badge_type") val badgeType: String, // e.g. "global"
    @SerialName("image_url") val imageUrl: String,
    @SerialName("metadata") val metadata: Map<String, JsonElement>? = null, // e.g. {"level": 33}
    @SerialName("selected") val selected: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

/** Present when `type == "reply"`; carries the quoted message + thread id. */
@Serializable
data class ChatMessageMetadata(
    @SerialName("original_sender") val originalSender: OriginalSender? = null,
    @SerialName("original_message") val originalMessage: OriginalMessage? = null,
    @SerialName("message_ref") val messageRef: String? = null,
    @SerialName("thread_parent_id") val threadParentId: String? = null,
)

@Serializable
data class OriginalSender(
    @SerialName("id") val id: Long,
    @SerialName("username") val username: String,
)

@Serializable
data class OriginalMessage(
    @SerialName("id") val id: String,
    @SerialName("content") val content: String,
)

/** Confirmed (Observed): GET /api/v2/channels/{slug}/info -> .chatroom.id */
@Serializable
data class Chatroom(
    @SerialName("id") val id: Long,
)
