package com.cmux.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileNotificationBridgeTest {
    @Test
    fun positiveUnreadCountPostsSummaryNotification() {
        val backend = RecordingBackend()
        val bridge = MobileNotificationBridge(backend, FakeStrings)

        bridge.applyUnreadCount(3)

        assertEquals(listOf(MobileNotificationBridge.CHANNEL_ID), backend.channels.map { it.id })
        assertEquals(1, backend.notifications.size)
        val posted = backend.notifications.single()
        assertEquals(MobileNotificationBridge.SUMMARY_NOTIFICATION_ID, posted.id)
        assertEquals("cmux agents need attention", posted.payload.title)
        assertEquals("3 unread notifications", posted.payload.body)
        assertEquals(3, posted.payload.number)
    }

    @Test
    fun singleUnreadCountUsesSingularBody() {
        val backend = RecordingBackend()
        val bridge = MobileNotificationBridge(backend, FakeStrings)

        bridge.applyUnreadCount(1)

        assertEquals("1 unread notification", backend.notifications.single().payload.body)
    }

    @Test
    fun repeatedUnreadUpdatesReuseNotificationChannel() {
        val backend = RecordingBackend()
        val bridge = MobileNotificationBridge(backend, FakeStrings)

        bridge.applyUnreadCount(2)
        bridge.applyUnreadCount(4)

        assertEquals(listOf(MobileNotificationBridge.CHANNEL_ID), backend.channels.map { it.id })
        assertEquals(listOf(2, 4), backend.notifications.map { it.payload.number })
    }

    @Test
    fun zeroUnreadCountCancelsSummaryNotification() {
        val backend = RecordingBackend()
        val bridge = MobileNotificationBridge(backend, FakeStrings)

        bridge.applyUnreadCount(0)

        assertEquals(listOf(MobileNotificationBridge.SUMMARY_NOTIFICATION_ID), backend.cancelledIds)
        assertTrue(backend.notifications.isEmpty())
    }

    @Test
    fun missingNotificationPermissionSkipsPosting() {
        val backend = RecordingBackend(canPost = false, canRequest = true)
        val bridge = MobileNotificationBridge(backend, FakeStrings)

        bridge.applyUnreadCount(5)

        assertTrue(backend.channels.isEmpty())
        assertTrue(backend.notifications.isEmpty())
        assertTrue(backend.cancelledIds.isEmpty())
    }

    @Test
    fun notificationBackendSecurityExceptionDoesNotEscape() {
        val backend = RecordingBackend(throwOnNotify = true)
        val bridge = MobileNotificationBridge(backend, FakeStrings)

        bridge.applyUnreadCount(5)

        assertTrue(backend.notifications.isEmpty())
    }

    @Test
    fun permissionStateReportsEnabledAndRequestAvailability() {
        val bridge = MobileNotificationBridge(
            RecordingBackend(canPost = false, canRequest = true),
            FakeStrings
        )

        assertEquals(
            MobileNotificationBridge.PermissionState(enabled = false, canRequest = true),
            bridge.permissionState()
        )
    }

    @Test
    fun dismissedIdsCancelSummaryNotification() {
        val backend = RecordingBackend()
        val bridge = MobileNotificationBridge(backend, FakeStrings)

        bridge.cancelDismissed(listOf("n-1"))

        assertEquals(listOf(MobileNotificationBridge.SUMMARY_NOTIFICATION_ID), backend.cancelledIds)
    }

    @Test
    fun emptyDismissedIdsDoNotCancelSummaryNotification() {
        val backend = RecordingBackend()
        val bridge = MobileNotificationBridge(backend, FakeStrings)

        bridge.cancelDismissed(emptyList())

        assertTrue(backend.cancelledIds.isEmpty())
    }

    private class RecordingBackend(
        private val canPost: Boolean = true,
        private val canRequest: Boolean = false,
        private val throwOnNotify: Boolean = false
    ) : MobileNotificationBridge.Backend {
        val channels = mutableListOf<RecordedChannel>()
        val notifications = mutableListOf<RecordedNotification>()
        val cancelledIds = mutableListOf<Int>()

        override fun canPostNotifications(): Boolean = canPost

        override fun canRequestNotifications(): Boolean = canRequest

        override fun ensureChannel(channelId: String, channelName: String) {
            channels.add(RecordedChannel(channelId, channelName))
        }

        override fun notify(notificationId: Int, payload: MobileNotificationBridge.NotificationPayload) {
            if (throwOnNotify) throw SecurityException("permission revoked")
            notifications.add(RecordedNotification(notificationId, payload))
        }

        override fun cancel(notificationId: Int) {
            cancelledIds.add(notificationId)
        }
    }

    private data class RecordedChannel(
        val id: String,
        val name: String
    )

    private data class RecordedNotification(
        val id: Int,
        val payload: MobileNotificationBridge.NotificationPayload
    )

    private object FakeStrings : MobileNotificationBridge.Strings {
        override fun channelName(): String = "Agent notifications"

        override fun unreadTitle(): String = "cmux agents need attention"

        override fun unreadBodyOne(): String = "1 unread notification"

        override fun unreadBodyMany(count: Int): String = "$count unread notifications"
    }
}
