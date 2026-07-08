package com.cmux.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat

class MobileNotificationBridge(
    private val backend: Backend,
    private val strings: Strings
) {
    constructor(context: Context) : this(
        backend = AndroidNotificationBackend(context),
        strings = AndroidNotificationStrings(context)
    )

    fun applyUnreadCount(unreadCount: Int?) {
        val count = unreadCount ?: return
        if (count <= 0) {
            backend.cancel(SUMMARY_NOTIFICATION_ID)
            return
        }
        if (!backend.canPostNotifications()) return

        backend.ensureChannel(
            CHANNEL_ID,
            strings.channelName()
        )
        val body = if (count == 1) {
            strings.unreadBodyOne()
        } else {
            strings.unreadBodyMany(count)
        }
        backend.notify(
            SUMMARY_NOTIFICATION_ID,
            NotificationPayload(
                channelId = CHANNEL_ID,
                title = strings.unreadTitle(),
                body = body,
                number = count
            )
        )
    }

    fun permissionState(): PermissionState {
        return PermissionState(
            enabled = backend.canPostNotifications(),
            canRequest = backend.canRequestNotifications()
        )
    }

    fun cancelDismissed(ids: List<String>) {
        if (ids.isNotEmpty()) {
            backend.cancel(SUMMARY_NOTIFICATION_ID)
        }
    }

    interface Backend {
        fun canPostNotifications(): Boolean
        fun canRequestNotifications(): Boolean
        fun ensureChannel(channelId: String, channelName: String)
        fun notify(notificationId: Int, payload: NotificationPayload)
        fun cancel(notificationId: Int)
    }

    interface Strings {
        fun channelName(): String
        fun unreadTitle(): String
        fun unreadBodyOne(): String
        fun unreadBodyMany(count: Int): String
    }

    data class NotificationPayload(
        val channelId: String,
        val title: String,
        val body: String,
        val number: Int
    )

    data class PermissionState(
        val enabled: Boolean,
        val canRequest: Boolean
    )

    private class AndroidNotificationStrings(private val context: Context) : Strings {
        override fun channelName(): String {
            return context.getString(R.string.notification_channel_agents)
        }

        override fun unreadTitle(): String {
            return context.getString(R.string.notification_unread_title)
        }

        override fun unreadBodyOne(): String {
            return context.getString(R.string.notification_unread_body_one)
        }

        override fun unreadBodyMany(count: Int): String {
            return context.getString(R.string.notification_unread_body_many, count)
        }
    }

    private class AndroidNotificationBackend(private val context: Context) : Backend {
        override fun canPostNotifications(): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }

        override fun canRequestNotifications(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canPostNotifications()
        }

        override fun ensureChannel(channelId: String, channelName: String) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        override fun notify(notificationId: Int, payload: NotificationPayload) {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(
                    0,
                    PendingIntentFlags.immutableUpdateCurrent()
                )
            val notification = NotificationCompat.Builder(context, payload.channelId)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(payload.title)
                .setContentText(payload.body)
                .setNumber(payload.number)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }

        override fun cancel(notificationId: Int) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }

    private object PendingIntentFlags {
        fun immutableUpdateCurrent(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "cmux_agent_notifications"
        const val SUMMARY_NOTIFICATION_ID = 40_001
    }
}
