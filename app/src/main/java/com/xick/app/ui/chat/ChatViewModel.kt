package com.xick.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xick.app.model.kick.chat.ChatMessage
import com.xick.app.repository.KickRepository
import com.xick.app.util.KickApi
import com.xick.app.util.chat.KickChatWebSocket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * "Connected" is only reported on the server's connect ack — earlier this
 * fired on the bare socket open, which showed "Sohbet bağlı" even when the
 * server then rejected the token and no messages ever came.
 */
class ChatViewModel(
    private val channelId: Long,
    private val chatroomId: Long,
    private val categoryId: Int?,
    private val repository: KickRepository = KickRepository(),
) : ViewModel() {

    sealed interface ChatState {
        data object Connecting : ChatState
        data object WaitingForServer : ChatState
        data object Connected : ChatState
        data class Unavailable(val reason: String) : ChatState
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<ChatState>(ChatState.Connecting)
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sendErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sendErrors: SharedFlow<String> = _sendErrors

    private var socket: KickChatWebSocket? = null

    init {
        val ws = KickChatWebSocket(
            client = KickApi.client,
            channelId = channelId,
            chatroomId = chatroomId,
            categoryId = categoryId,
            listener = object : KickChatWebSocket.Listener {
                override fun onSocketOpen() { _state.value = ChatState.WaitingForServer }
                override fun onConnected() { _state.value = ChatState.Connected }

                override fun onChatMessage(rawInnerJson: String) {
                    val parsed = runCatching { json.decodeFromString(ChatMessage.serializer(), rawInnerJson) }
                        .getOrNull() ?: return
                    _messages.value = (_messages.value + parsed).takeLast(MAX_RETAINED_MESSAGES)
                    if (_state.value !is ChatState.Connected) _state.value = ChatState.Connected
                }

                override fun onClosed(code: Int, reason: String) {
                    _state.value = ChatState.Unavailable("bağlantı kapandı ($code) $reason")
                }

                override fun onFailure(error: Throwable) {
                    _state.value = ChatState.Unavailable(error.message ?: error.toString())
                }
            },
        )
        socket = ws
        viewModelScope.launch { ws.connect() }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.sendChatMessage(chatroomId, text.trim()) }
                .onFailure { _sendErrors.tryEmit(it.message ?: it.toString()) }
        }
    }

    override fun onCleared() {
        socket?.disconnect()
        super.onCleared()
    }

    companion object {
        // Data-saving/memory: only the tail of chat is ever on screen.
        private const val MAX_RETAINED_MESSAGES = 200
    }
}
