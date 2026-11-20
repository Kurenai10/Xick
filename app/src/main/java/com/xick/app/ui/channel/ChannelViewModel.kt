package com.xick.app.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xick.app.model.kick.Channel
import com.xick.app.repository.KickRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ChannelUiState {
    data object Loading : ChannelUiState
    data class Loaded(val channel: Channel, val playbackUrl: String?) : ChannelUiState
    data class Error(val message: String) : ChannelUiState
}

class ChannelViewModel(
    private val slug: String,
    private val repository: KickRepository = KickRepository(),
    private val loadPlayback: Boolean = true,
) : ViewModel() {

    private val _state = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val channel = repository.getChannel(slug)
                // Offline channels have no HLS manifest to fetch — don't
                // treat that 404/empty case as a hard error. The profile
                // screen skips playback entirely (loadPlayback = false).
                val playbackUrl = if (loadPlayback && channel.livestream?.isLive == true) {
                    runCatching { repository.getPlaybackUrl(slug) }.getOrNull()
                } else null
                ChannelUiState.Loaded(channel, playbackUrl)
            }.onSuccess { _state.value = it }
                .onFailure { _state.value = ChannelUiState.Error(it.message ?: it.toString()) }
        }
    }
}
