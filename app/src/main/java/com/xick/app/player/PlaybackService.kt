package com.xick.app.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.xick.app.util.DataSaverPolicy

/**
 * Hosts the Media3 ExoPlayer instance + MediaSession for background/PiP
 * playback of Kick's HLS streams, mirroring Xtra's player service.
 *
 * (Observed) GET /api/v2/channels/{slug}/playback-url returns
 * { "data": "<AWS IVS .m3u8 URL>?token=<short-lived signed JWT>" } — Kick
 * streams are served via Amazon IVS (not a Kick-owned CDN). The JWT embeds
 * its own `exp`; Media3's HLS extractor consumes the URL as-is, no custom
 * DataSource auth needed — just re-fetch playback-url when it expires.
 *
 * Data-saving: the track selector is capped by DataSaverPolicy so ExoPlayer
 * never auto-picks the highest HLS rendition on a metered connection — the
 * official app always starts at the stream's default/highest quality.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val quality = DataSaverPolicy.currentQuality(this)
        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, DataSaverPolicy.maxVideoHeightPx(quality))
                .build()
        }
        val player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
