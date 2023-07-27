package it.vfsfitvnm.vimusic

import android.app.Application
import androidx.core.text.isDigitsOnly
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import it.vfsfitvnm.vimusic.enums.CoilDiskCacheMaxSize
import it.vfsfitvnm.vimusic.enums.ExoPlayerDiskCacheMaxSize
import it.vfsfitvnm.vimusic.utils.coilDiskCacheMaxSizeKey
import it.vfsfitvnm.vimusic.utils.exoPlayerDiskCacheMaxSizeKey
import it.vfsfitvnm.vimusic.utils.getEnum
import it.vfsfitvnm.vimusic.utils.preferences

class MainApplication : Application(), ImageLoaderFactory {

    private var _cache: SimpleCache? = null
    val cache: SimpleCache
        get() {
            if (_cache == null) {
                initCache()
            }
            return _cache
                ?: error("Cache has not been initialised")
        }

    override fun onCreate() {
        super.onCreate()
        DatabaseInitializer()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .diskCache(
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes(
                        preferences.getEnum(
                            coilDiskCacheMaxSizeKey,
                            CoilDiskCacheMaxSize.`128MB`
                        ).bytes
                    )
                    .build()
            )
            .build()
    }

    private fun initCache() {
        val cacheEvictor = when (val size =
            preferences.getEnum(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`)) {
            ExoPlayerDiskCacheMaxSize.Unlimited -> NoOpCacheEvictor()
            else -> LeastRecentlyUsedCacheEvictor(size.bytes)
        }

        // TODO: Remove in a future release
        val directory = cacheDir.resolve("exoplayer").also { directory ->
            if (directory.exists()) return@also

            directory.mkdir()

            cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name.length == 1 && file.name.isDigitsOnly() || file.extension == "uid") {
                    if (!file.renameTo(directory.resolve(file.name))) {
                        file.deleteRecursively()
                    }
                }
            }

            filesDir.resolve("coil").deleteRecursively()
        }

        _cache = SimpleCache(directory, cacheEvictor, StandaloneDatabaseProvider(this))
    }

    fun releaseCache() {
        cache.release()
        _cache = null
    }

}
