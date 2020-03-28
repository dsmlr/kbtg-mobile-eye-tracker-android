package com.ria.demo.utilities

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.ria.demo.R

/**
 * REF: https://codinginfinite.com/android-oreo-notification-channel-example/
 */
class NotificationHelper constructor(context: Context) : ContextWrapper(context) {

    private val notificationManager: NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val MESSAGE_NOTIFICATION_TITLE = "Message Notification Channel"
        private const val COMMENT_NOTIFICATION_TITLE = "Comment Notification Channel"
        private const val DEFAULT_NOTIFICATION_TITLE = "Application Notification"
        private const val MESSAGE_NOTIFICATION_CHANNEL = "com.ria.demo.utilities.message_channel"
        private const val COMMENT_NOTIFICATION_CHANNEL = "com.ria.demo.utilities.comment_channel"
        private const val DEFAULT_NOTIFICATION_CHANNEL = "com.ria.demo.utilities.default_channel"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannels()
        }
    }

    @SuppressLint("WrongConstant")
    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createChannels() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationChannels = mutableListOf<NotificationChannel>()

        // Building message notification channel
        val messageNotificationChannel = NotificationChannel(
            MESSAGE_NOTIFICATION_CHANNEL,
            MESSAGE_NOTIFICATION_TITLE, NotificationManager.IMPORTANCE_HIGH
        )

        messageNotificationChannel.enableLights(true)
        messageNotificationChannel.lightColor = Color.RED
        messageNotificationChannel.setShowBadge(true)
        messageNotificationChannel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
        messageNotificationChannel.enableVibration(true)
        messageNotificationChannel.setSound(uri, null)
        messageNotificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

        notificationChannels.add(messageNotificationChannel)

        // Building comment notification channel
        val commentNotificationChannel = NotificationChannel(
            COMMENT_NOTIFICATION_CHANNEL,
            COMMENT_NOTIFICATION_TITLE, NotificationManager.IMPORTANCE_MAX
        )

        commentNotificationChannel.enableLights(true)
        commentNotificationChannel.lightColor = Color.RED
        commentNotificationChannel.setShowBadge(true)

        messageNotificationChannel.enableVibration(true)

        commentNotificationChannel.setSound(uri, null)
        commentNotificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

        notificationChannels.add(commentNotificationChannel)

        // Building default notification channel
        val defaultNotificationChannel = NotificationChannel(
            DEFAULT_NOTIFICATION_CHANNEL,
            DEFAULT_NOTIFICATION_TITLE, NotificationManager.IMPORTANCE_LOW
        )

        defaultNotificationChannel.setShowBadge(true)
        defaultNotificationChannel.setSound(uri, null)

        notificationChannels.add(defaultNotificationChannel)

        notificationManager.createNotificationChannels(notificationChannels)
    }

    fun createNotificationBuilder(
        title: String, body: String,
        cancelAble: Boolean = true,
        pendingIntent: PendingIntent? = null,
        channelId: String = DEFAULT_NOTIFICATION_CHANNEL
    ): Notification.Builder {
        val builder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(applicationContext, channelId)
            } else {
                Notification.Builder(applicationContext)
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setSmallIcon(R.mipmap.ic_launcher_round)
            builder.setColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
        } else {
            builder.setSmallIcon(R.mipmap.ic_launcher_round)
        }

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        builder.setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(cancelAble)

        return builder
    }

    fun makeNotification(builder: Notification.Builder, notificationId: Int) = apply {
        notificationManager.notify(notificationId, builder.build())
    }
}