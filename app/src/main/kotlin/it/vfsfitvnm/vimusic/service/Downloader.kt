package it.vfsfitvnm.vimusic.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.vfsfitvnm.vimusic.R
import okhttp3.internal.notify

object Downloader {
    private var currentCount: Int = 0
    private const val channelId = "download"
    private const val notificationId = 7123
    private var initialized = false

    @SuppressLint("StaticFieldLeak")
    private lateinit var builder: NotificationCompat.Builder

    @Synchronized
    fun addItem(context: Context, count: Int = 1) {
        currentCount += count
        if (currentCount == 1) {
            createNotification(context.applicationContext)
        }
    }

    @Synchronized
    fun removeItem(context: Context, count: Int = 1) {
        currentCount -= count
        if (currentCount == 0) {
            removeNotification(context.applicationContext)
        }
    }

    private fun removeNotification(context: Context) {
        NotificationManagerCompat.from(context).apply {
            builder.setContentText("Download complete").setProgress(0, 0, false)
            notify(notificationId, builder.build())
        }
    }

    private fun registerChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("cache", "registering channel")
            val channel = NotificationChannel(
                channelId,
                "ViMusic pre-cache",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Pre-cache download progress"
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

    }

    private fun createNotification(context: Context) {
        if (!initialized) registerChannel(context)
        builder = NotificationCompat.Builder(context, "download")
            .setContentTitle("Pre-caching songs")
            .setContentText("Download in progress")
            .setSmallIcon(R.drawable.baseline_download_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        val progressMax = 100
        val progressCurrent = 0
        NotificationManagerCompat.from(context).apply {
            builder.setProgress(progressMax, progressCurrent, false)
            notify(7, builder.build())
        }
    }
}