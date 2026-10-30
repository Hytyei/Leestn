package app.leestn

import android.app.Application
import app.leestn.data.Http
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient

/**
 * Sets up how every picture in the app (and in the widgets) is downloaded.
 *
 * Coil's own default identifies itself as "okhttp", and the image servers of many podcast hosts refuse that. Pictures
 * that come with a podcast's own RSS feed (as opposed to Apple's directory) then never showed. Here the requests look
 * like an ordinary phone browser, wait as long as the feed downloads do, and ask for image formats.
 */
class LeestnApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val client: OkHttpClient = Http.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 Leestn/1.0")
                    .header("Accept", "image/webp,image/png,image/jpeg,image/*;q=0.8")
                    .build()
                chain.proceed(req)
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("covers")).maxSizePercent(0.03).build() }
            .respectCacheHeaders(false)
            .build()
    }
}
