package com.cmux.android

import org.json.JSONArray
import org.json.JSONObject
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

    @Test
    fun terminalFidelityKeepsOlderRenderGridHostsOnRenderGridTopic() {
        assertEquals(
            listOf(
                "workspace.updated",
                "terminal.render_grid",
                "terminal.set_font",
                "notification.badge",
                "notification.dismissed"
            ),
            MobileEventTopics.topicsForHostStatus(
                MobileEventTopics.HostStatusCapabilities(terminalFidelity = "render_grid")
            )
        )
    }

    @Test
    fun hostStatusJsonMergesTopLevelAndNestedCapabilitiesForRenderGridTopic() {
        val status = JSONObject()
            .put("capabilities", JSONArray().put("workspace.create.v1"))
            .put(
                "host_service",
                JSONObject().put("capabilities", JSONArray().put("terminal.render_grid.v1"))
            )

        assertEquals(
            listOf(
                "workspace.updated",
                "terminal.render_grid",
                "terminal.set_font",
                "notification.badge",
                "notification.dismissed"
            ),
            MobileEventTopics.topicsForHostStatusJson(status)
        )
    }

    @Test
    fun hostStatusJsonReadsNestedTerminalFidelityForRenderGridTopic() {
        val status = JSONObject()
            .put("capabilities", JSONArray())
            .put(
                "host_service",
                JSONObject().put("terminal_fidelity", "render_grid")
            )

        assertEquals(
            listOf(
                "workspace.updated",
                "terminal.render_grid",
                "terminal.set_font",
                "notification.badge",
                "notification.dismissed"
            ),
            MobileEventTopics.topicsForHostStatusJson(status)
        )
    }
}
