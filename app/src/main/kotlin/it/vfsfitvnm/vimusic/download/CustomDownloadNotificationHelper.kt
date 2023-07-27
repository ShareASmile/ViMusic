/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.vfsfitvnm.vimusic.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.R
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Requirements.RequirementFlags

/**
 * Helper for creating download notifications.
 */
@UnstableApi
class CustomDownloadNotificationHelper(context: Context, channelId: String) {
    private val notificationBuilder: NotificationCompat.Builder

    @Deprecated(
        """Use {@link #buildProgressNotification(Context, int, PendingIntent, String, List,
     * int)}."""
    )
    fun buildProgressNotification(
        context: Context,
        @DrawableRes smallIcon: Int,
        contentIntent: PendingIntent?,
        message: String?,
        downloads: List<Download>
    ): Notification {
        return buildProgressNotification(
            context, smallIcon, contentIntent, message, downloads,  /* notMetRequirements= */0
        )
    }

    /**
     * Returns a progress notification for the given downloads.
     *
     * @param context            A context.
     * @param smallIcon          A small icon for the notification.
     * @param contentIntent      An optional content intent to send when the notification is clicked.
     * @param message            An optional message to display on the notification.
     * @param downloads          The downloads.
     * @param notMetRequirements Any requirements for downloads that are not currently met.
     * @return The notification.
     */
    fun buildProgressNotification(
        context: Context,
        @DrawableRes smallIcon: Int,
        contentIntent: PendingIntent?,
        message: String?,
        downloads: List<Download>,
        notMetRequirements: @RequirementFlags Int
    ): Notification {
        var totalPercentage = 0f
        var downloadTaskCount = 0
        var allDownloadPercentagesUnknown = true
        var haveDownloadedBytes = false
        var haveDownloadingTasks = false
        var haveQueuedTasks = false
        var haveRemovingTasks = false
        for (i in downloads.indices) {
            val download = downloads[i]
            when (download.state) {
                Download.STATE_REMOVING -> haveRemovingTasks = true
                Download.STATE_QUEUED -> haveQueuedTasks = true
                Download.STATE_RESTARTING, Download.STATE_DOWNLOADING -> {
                    haveDownloadingTasks = true
                    val downloadPercentage = download.percentDownloaded
                    if (downloadPercentage != C.PERCENTAGE_UNSET.toFloat()) {
                        allDownloadPercentagesUnknown = false
                        totalPercentage += downloadPercentage
                    }
                    haveDownloadedBytes = haveDownloadedBytes or (download.bytesDownloaded > 0)
                    downloadTaskCount++
                }
                Download.STATE_STOPPED, Download.STATE_COMPLETED, Download.STATE_FAILED -> {}
                else -> {}
            }
        }
        val titleStringId: Int
        var showProgress = true
        if (haveDownloadingTasks) {
            titleStringId = R.string.exo_download_downloading
        } else if (haveQueuedTasks && notMetRequirements != 0) {
            showProgress = false
            titleStringId = if (notMetRequirements and Requirements.NETWORK_UNMETERED != 0) {
                // Note: This assumes that "unmetered" == "WiFi", since it provides a clearer message that's
                // correct in the majority of cases.
                R.string.exo_download_paused_for_wifi
            } else if (notMetRequirements and Requirements.NETWORK != 0) {
                R.string.exo_download_paused_for_network
            } else {
                R.string.exo_download_paused
            }
        } else if (haveRemovingTasks) {
            titleStringId = R.string.exo_download_removing
        } else {
            // There are either no downloads, or all downloads are in terminal states.
            titleStringId = NULL_STRING_ID
        }
        var maxProgress = 0
        var currentProgress = 0
        var indeterminateProgress = false
        if (showProgress) {
            maxProgress = 100
            if (haveDownloadingTasks) {
                currentProgress = (totalPercentage / downloadTaskCount).toInt()
                indeterminateProgress = allDownloadPercentagesUnknown && haveDownloadedBytes
            } else {
                indeterminateProgress = true
            }
        }
        return buildNotification(
            context,
            smallIcon,
            contentIntent,
            message,
            titleStringId,
            maxProgress,
            currentProgress,
            indeterminateProgress,  /* ongoing= */
            true,  /* showWhen= */
            false
        )
    }

    /**
     * Returns a notification for a completed download.
     *
     * @param context       A context.
     * @param smallIcon     A small icon for the notifications.
     * @param contentIntent An optional content intent to send when the notification is clicked.
     * @param message       An optional message to display on the notification.
     * @return The notification.
     */
    fun buildDownloadCompletedNotification(
        context: Context,
        @DrawableRes smallIcon: Int,
        contentIntent: PendingIntent?,
        message: String?
    ): Notification {
        val titleStringId = R.string.exo_download_completed
        return buildEndStateNotification(context, smallIcon, contentIntent, message, titleStringId)
    }

    /**
     * Returns a notification for a failed download.
     *
     * @param context       A context.
     * @param smallIcon     A small icon for the notifications.
     * @param contentIntent An optional content intent to send when the notification is clicked.
     * @param message       An optional message to display on the notification.
     * @return The notification.
     */
    fun buildDownloadFailedNotification(
        context: Context,
        @DrawableRes smallIcon: Int,
        contentIntent: PendingIntent?,
        message: String?
    ): Notification {
        @StringRes val titleStringId = R.string.exo_download_failed
        return buildEndStateNotification(context, smallIcon, contentIntent, message, titleStringId)
    }

    private fun buildEndStateNotification(
        context: Context,
        @DrawableRes smallIcon: Int,
        contentIntent: PendingIntent?,
        message: String?,
        @StringRes titleStringId: Int
    ): Notification {
        return buildNotification(
            context,
            smallIcon,
            contentIntent,
            message,
            titleStringId,  /* maxProgress= */
            0,  /* currentProgress= */
            0,  /* indeterminateProgress= */
            false,  /* ongoing= */
            false,  /* showWhen= */
            true
        )
    }

    private fun buildNotification(
        context: Context,
        @DrawableRes smallIcon: Int,
        contentIntent: PendingIntent?,
        message: String?,
        @StringRes titleStringId: Int,
        maxProgress: Int,
        currentProgress: Int,
        indeterminateProgress: Boolean,
        ongoing: Boolean,
        showWhen: Boolean
    ): Notification {
        notificationBuilder.setSmallIcon(smallIcon)
        notificationBuilder.setContentTitle(
            if (titleStringId == NULL_STRING_ID) null else context.resources.getString(titleStringId)
        )
        notificationBuilder.setContentIntent(contentIntent)
        notificationBuilder.setStyle(
            if (message == null) null else NotificationCompat.BigTextStyle().bigText(message)
        )
        notificationBuilder.setProgress(maxProgress, currentProgress, indeterminateProgress)
        notificationBuilder.setOngoing(ongoing)
        notificationBuilder.setShowWhen(showWhen)
        notificationBuilder.setOnlyAlertOnce(true)
        notificationBuilder.foregroundServiceBehavior =
            NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
        return notificationBuilder.build()
    }

    companion object {
        @StringRes
        private val NULL_STRING_ID = 0
    }

    /**
     * @param context   A context.
     * @param channelId The id of the notification channel to use.
     */
    init {
        notificationBuilder = NotificationCompat.Builder(context.applicationContext, channelId)
    }
}