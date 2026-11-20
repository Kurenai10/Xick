package com.xick.app.util.chat

import com.xick.app.util.AnonId
import com.xick.app.util.KickApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Kick's realtime chat transport (Observed, 2026-11-17, by hooking the web
 * client's own WebSocket): a Centrifugo server, not Pusher.
 *
 *   wss://realtime.us-east-1.platform.kick.com/connection/websocket
 *   -> {"connect":{"token":"<token>","name":"js"},"id":1}
 *   <- {"id":1,"connect":{"client":"<uuid>","version":"...","ping":25,"pong":true}}
 *   -> {"subscribe":{"channel":"chatrooms.<chatroomId>.v2","flag":1},"id":N} (+ channel_/chatroom_ variants)
 *   <- {"push":{"channel":"chatrooms.<id>.v2","pub":{"data":{
 *        "event":"App\\Events\\ChatMessageEvent","data":"<ChatMessage as a JSON string>"}}}}
 *   Keepalive: server sends `{}`, client echoes `{}`.
 *
 * The server batches several replies into one frame, newline-separated
 * (Observed), so every frame is split on '\n' before parsing.
 *
 * TOKEN — SOLVED (Observed + verified on a real device, 2026-11-17): the
 * connect JWT comes from POST web.kick.com/api/v1/realtime/auth/connection
 * with body {"client_id":"<anon uuid>"} (see KickApi.realtimeAuthRequest).
 * The earlier websockets.kick.com/viewer/v1/token ULID was the wrong token —
 * the server closed the socket with code 3500 "invalid token" when it was
 * used. With the real JWT the connect ack arrives and live messages flow.
 */
class KickChatWebSocket(
    private val client: OkHttpClient,
    private val channelId: Long,
    private val chatroomId: Long,
    private val categoryId: Int?,
    private val listener: Listener,
) {

    interface Listener {
        fun onSocketOpen()
        fun onConnected()
        fun onChatMessage(rawInnerJson: String)
        fun onClosed(code: Int, reason: String)
        fun onFailure(error: Throwable)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var socket: WebSocket? = null
    private var nextId = 1

    suspend fun connect() {
        val token = TokenClient.fetchViewerToken(client)
            ?: return listener.onFailure(IllegalStateException("token alınamadı"))

        val request = Request.Builder()
            .url("wss://realtime.us-east-1.platform.kick.com/connection/websocket")
            .header("Origin", "https://kick.com")
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onSocketOpen()
                // Order matters: Centrifugo rejects subscribes sent before the
                // connect command. (Earlier version queued the subscribes right
                // after newWebSocket(), i.e. *before* this connect — a real bug.)
                val frames = buildList {
                    add(connectFrame(token))
                    add(subscribeFrame("chatrooms.$chatroomId.v2"))
                    add(subscribeFrame("chatroom_$chatroomId"))
                    add(subscribeFrame("chatrooms.$chatroomId"))
                    add(subscribeFrame("channel_$channelId"))
                    add(subscribeFrame("channel.$channelId"))
                    categoryId?.let { add(subscribeFrame("drops_category_$it")) }
                }
                webSocket.send(frames.joinToString("\n"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                text.split('\n').filter { it.isNotBlank() }.forEach { line ->
                    if (line == "{}") webSocket.send("{}") else routeLine(line)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed(code, reason)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onFailure(t)
            }
        })
    }

    fun disconnect() {
        socket?.close(1000, null)
        socket = null
    }

    private fun connectFrame(token: String): String = buildJsonObject {
        put("connect", buildJsonObject {
            put("token", token)
            put("name", "js")
        })
        put("id", nextId++)
    }.toString()

    private fun subscribeFrame(channel: String): String = buildJsonObject {
        put("subscribe", buildJsonObject {
            put("channel", channel)
            put("flag", 1)
        })
        put("id", nextId++)
    }.toString()

    private fun routeLine(line: String) {
        val root = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return

        root["error"]?.let { err ->
            val msg = runCatching { err.jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
            // id 1 is the connect command; an error there means the token was rejected.
            if (root["id"]?.jsonPrimitive?.content == "1") {
                listener.onFailure(IllegalStateException("sunucu bağlantıyı reddetti: ${msg ?: err}"))
            }
            return
        }
        if (root.containsKey("connect")) {
            listener.onConnected()
            return
        }
        val push = root["push"] as? JsonObject ?: return
        val data = ((push["pub"] as? JsonObject)?.get("data")) as? JsonObject ?: return
        val event = (data["event"] as? JsonPrimitive)?.content
        if (event == "App\\Events\\ChatMessageEvent") {
            // `data` is a JSON *string* holding the message — .content unescapes it.
            val inner = (data["data"] as? JsonPrimitive)?.content ?: return
            listener.onChatMessage(inner)
        }
    }
}

private object TokenClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchViewerToken(client: OkHttpClient): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(KickApi.realtimeAuthRequest(AnonId.get())).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val dataObj = json.parseToJsonElement(body).jsonObject["data"] as? JsonObject
                (dataObj?.get("token") as? JsonPrimitive)?.content
            }
        }.getOrNull()
    }
}
