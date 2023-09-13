package it.vfsfitvnm.vimusic.service

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.media3.common.C.LENGTH_UNSET
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.utils.downloadFavouritesKey
import it.vfsfitvnm.vimusic.utils.preferences
import java.util.TreeSet

class DownloadEvictor(private val maxBytes: Long, private val context: Context) : CacheEvictor {

    private val leastRecentlyUsed: TreeSet<CacheSpan> = sortedSetOf(object : Comparator<CacheSpan> {
        override fun compare(lhs: CacheSpan, rhs: CacheSpan): Int {
            val lastTouchTimeStampDelta = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp
            if (lastTouchTimeStampDelta == 0L) {
                return lhs.compareTo(rhs)
            }
            return if (lhs.lastTouchTimestamp < rhs.lastTouchTimestamp) -1 else 1
        }
    })

    private var currentSize: Long = 0
    private var downloadSize: Long = 0

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        val downloadFavourites = context.preferences.getBoolean(downloadFavouritesKey, false)
        val shouldSkip = Database.songIsDownloaded(span.key) || (downloadFavourites && Database.songIsFavourited(span.key))
        if (shouldSkip) {
            Log.d("cache", "cache skip ${span.key}")
            downloadSize += span.length
            Downloader.downloadsSize = downloadSize
        } else {
            Log.d("cache", "cache add ${span.key}")
            leastRecentlyUsed.add(span)
            currentSize += span.length
            evictCache(cache, 0)
        }
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSize -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        Log.d("cache", "add to cache: ${newSpan.key}")
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() {
    }

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != LENGTH_UNSET.toLong()) {
            evictCache(cache, length)
        }
    }

    private fun evictCache(cache: Cache, requiredSpace: Long) {
        while (currentSize + requiredSpace > maxBytes && !leastRecentlyUsed.isEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first())
        }
    }
}