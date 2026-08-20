package com.ramzes.visavinet

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ramzes.visavinet.network.VisaviApi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class VisaviApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("User-Agent", "VisaviClient")

                VisaviApi.getToken()?.let { token ->
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                chain.proceed(requestBuilder.build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}
