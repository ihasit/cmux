package com.cmux.android

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileRpcParamsTest {
    @Test
    fun createWorkspaceCarriesAndroidClientId() {
        val params = MobileRpcParams.createWorkspace()

        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }

    @Test
    fun createTerminalCarriesWorkspaceAndAndroidClientId() {
        val params = MobileRpcParams.createTerminal("workspace-1")

        assertEquals("workspace-1", params.getString("workspace_id"))
        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }

    @Test
    fun workspaceActionCarriesActionAndAndroidClientId() {
        val params = MobileRpcParams.workspaceAction("workspace-1", "rename")
            .put("title", "New name")

        assertEquals("workspace-1", params.getString("workspace_id"))
        assertEquals("rename", params.getString("action"))
        assertEquals("New name", params.getString("title"))
        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }

    @Test
    fun terminalViewportClampsDimensionsAndCarriesAndroidClientId() {
        val params = MobileRpcParams.terminalViewport("workspace-1", "terminal-1", 500, 2)

        assertEquals("workspace-1", params.getString("workspace_id"))
        assertEquals("terminal-1", params.getString("terminal_id"))
        assertEquals("terminal-1", params.getString("surface_id"))
        assertEquals(300, params.getInt("viewport_columns"))
        assertEquals(5, params.getInt("viewport_rows"))
        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }

    @Test
    fun terminalClearViewportCarriesClearFlag() {
        val params = MobileRpcParams.clearTerminalViewport("workspace-1", "terminal-1")

        assertEquals("workspace-1", params.getString("workspace_id"))
        assertEquals("terminal-1", params.getString("terminal_id"))
        assertEquals("terminal-1", params.getString("surface_id"))
        assertEquals(true, params.getBoolean("clear"))
        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }

    @Test
    fun terminalPasteDefaultsBlankSubmitKey() {
        val params = MobileRpcParams.terminalPaste("workspace-1", "terminal-1", "hello", "", 80, 24)

        assertEquals("hello", params.getString("text"))
        assertEquals("return", params.getString("submit_key"))
        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }

    @Test
    fun terminalPasteImageDefaultsBlankFormat() {
        val params = MobileRpcParams.terminalPasteImage("workspace-1", "terminal-1", "base64", "", 80, 24)

        assertEquals("base64", params.getString("image_base64"))
        assertEquals("png", params.getString("image_format"))
        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }

    @Test
    fun terminalScrollClampsCoordinatesAndScrollbackRows() {
        val params = MobileRpcParams.terminalScroll(
            workspaceId = "workspace-1",
            terminalId = "terminal-1",
            deltaLines = -3.5,
            column = -4,
            row = -2,
            maxScrollbackRows = 50000,
            columns = 10,
            rows = 200
        )

        assertEquals(-3.5, params.getDouble("delta_lines"), 0.0)
        assertEquals(0, params.getInt("col"))
        assertEquals(0, params.getInt("row"))
        assertEquals(20000, params.getInt("max_scrollback_rows"))
        assertEquals(20, params.getInt("viewport_columns"))
        assertEquals(120, params.getInt("viewport_rows"))
        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }

    @Test
    fun terminalMouseClampsCoordinatesAndCarriesAndroidClientId() {
        val params = MobileRpcParams.terminalMouse("workspace-1", "terminal-1", -1, 7)

        assertEquals("workspace-1", params.getString("workspace_id"))
        assertEquals("terminal-1", params.getString("terminal_id"))
        assertEquals("terminal-1", params.getString("surface_id"))
        assertEquals(0, params.getInt("col"))
        assertEquals(7, params.getInt("row"))
        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }

    @Test
    fun eventSubscriptionCarriesStreamTopicsAndAndroidClientId() {
        val params = MobileRpcParams.eventSubscription(
            streamId = "stream-android-1",
            topics = listOf("workspace.updated", "terminal.bytes")
        )

        assertEquals("stream-android-1", params.getString("stream_id"))
        assertEquals(
            listOf("workspace.updated", "terminal.bytes"),
            (0 until params.getJSONArray("topics").length()).map { index ->
                params.getJSONArray("topics").getString(index)
            }
        )
        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }
}
