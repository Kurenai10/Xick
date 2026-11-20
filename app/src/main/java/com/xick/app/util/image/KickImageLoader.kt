package com.xick.app.util.image

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Single Coil ImageLoader for the app, sized deliberately small. Data-
 * saving reasoning: thumbnails/avatars/emotes are the highest-frequency
 * image traffic in the app; capping both caches keeps re-renders (scroll
 * back up a list, reopen a channel) hitting disk instead of network,
 * without the cache itself ballooning on constrained devices.
 *
 * Which URL gets requested in the first place (small vs large responsive
 * variant) is DataSaverPolicy.pickThumbnailUrl's job, not this loader's —
 * this just serves/caches whatever URL it's given efficiently.
 */
object KickImageLoader {

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

    private fun build(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            // Animated emotes (Kick ships them as animated webp/gif) need these,
            // or they'd render as a static first frame.
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.15) // small slice of app memory, not Coil's 25% default
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("kick_image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024) // 64MB: thumbnails + emotes + avatars, not VOD frames
                    .build()
            }
            .respectCacheHeaders(true)
            .build()
}
