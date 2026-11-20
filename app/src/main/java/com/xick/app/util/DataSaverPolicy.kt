package com.xick.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Central data-usage policy. Xick's whole reason to exist next to the
 * official Kick app is being lighter and cheaper on mobile data — every
 * network/image/video decision in the app goes through this, not ad-hoc
 * per-screen choices.
 *
 * Official app comparison (observed in its APK's native libs/assets):
 * it ships Crashlytics, Datadog NDK, Google/Facebook/TikTok ad + analytics
 * SDKs, ML Kit translate+language-id (~17MB alone), Stripe, DoubleClick —
 * none of that is in Xick. That alone is the largest data/size win; this
 * class covers the remaining day-to-day traffic (images, video, polling).
 */
object DataSaverPolicy {

    enum class Quality { LOW, MEDIUM, HIGH }

    /** User override; null = automatic (follow network type). */
    var forcedQuality: Quality? = null

    fun isOnMeteredConnection(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // fail safe: assume metered if we can't tell
        val network = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun currentQuality(context: Context): Quality =
        forcedQuality ?: if (isOnMeteredConnection(context)) Quality.LOW else Quality.HIGH

    /**
     * Picks the smallest thumbnail that's still acceptable for the given
     * use, instead of always grabbing Kick's largest responsive variant.
     * Kick's `thumbnail.responsive` is a standard `srcset` string:
     * "...160.webp 284w, ...360.webp 480w, ...480.webp 640w, ...720.webp 1280w, ...1080.webp 1920w"
     */
    fun pickThumbnailUrl(responsiveSrcset: String?, fallback: String?, quality: Quality): String? {
        if (responsiveSrcset.isNullOrBlank()) return fallback
        val candidates = responsiveSrcset.split(",").mapNotNull { entry ->
            val parts = entry.trim().split(" ")
            if (parts.size != 2) return@mapNotNull null
            val width = parts[1].removeSuffix("w").toIntOrNull() ?: return@mapNotNull null
            width to parts[0]
        }.sortedBy { it.first }
        if (candidates.isEmpty()) return fallback
        return when (quality) {
            Quality.LOW -> candidates.first().second          // smallest (~284w)
            Quality.MEDIUM -> candidates.getOrElse(candidates.size / 2) { candidates.first() }.second
            Quality.HIGH -> candidates.last().second          // largest (~1920w)
        }
    }

    /**
     * Max HLS rendition height to request from ExoPlayer's track selector.
     * LOW caps hard (saves the most on a cellular connection); HIGH leaves
     * it uncapped (Wi-Fi default).
     */
    fun maxVideoHeightPx(quality: Quality): Int = when (quality) {
        Quality.LOW -> 480
        Quality.MEDIUM -> 720
        Quality.HIGH -> Int.MAX_VALUE
    }

    /** Chat emotes/GIFs: never prefetch the whole channel emote set eagerly — only load one as it's actually rendered in a visible message, then let the disk cache (util/KickApi's OkHttp Cache) keep it. */
    const val EMOTE_LAZY_LOAD = true

    /** Secondary chat-page endpoints (leaderboards/polls/goals) the official
     * web client fetches unconditionally on every page load — Xick fetches
     * these only when the user actually opens that tab. */
    const val EAGER_SECONDARY_CHAT_DATA = false
}
