package com.arjun.core.rive

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun riveImageModule(): Module = module {

    single<ImageLoader> {
        ImageLoader.Builder(get<Context>())
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(get<Context>())
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(get<Context>().cacheDir.resolve("rive_image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }
}