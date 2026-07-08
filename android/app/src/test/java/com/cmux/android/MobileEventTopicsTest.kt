package com.cmux.android

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileEventTopicsTest {
    @Test
    fun renderGridHostsDoNotSubscribeToRawTerminalBytes() {
        assertEquals(
            listOf(
                "workspace.updated",
                "terminal.render_grid",
                "terminal.set_font",
                "notification.badge",
                "notification.dismissed"
            ),
            MobileEventTopics.topicsForCapabilities(setOf("terminal.render_grid.v1"))
        )
    }

    @Test
    fun rawBytesHostsSubscribeToBytesFallback() {
        assertEquals(
            listOf(
                "workspace.updated",
                "terminal.bytes",
                "terminal.set_font",
                "notification.badge",
                "notification.dismissed"
            ),
            MobileEventTopics.topicsForCapabilities(emptySet())
        )
    }
}
