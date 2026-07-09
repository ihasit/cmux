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
    fun workspaceAndTerminalParamsTrimIds() {
        val workspace = MobileRpcParams.createTerminal(" workspace-1 ")
        val action = MobileRpcParams.workspaceAction(" workspace-1 ", " rename ")
        val close = MobileRpcParams.closeWorkspace(" workspace-1 ")
        val group = MobileRpcParams.workspaceGroup(" group-1 ")
        val terminal = MobileRpcParams.terminalViewport(" workspace-1 ", " terminal-1 ", 80, 24)

        assertEquals("workspace-1", workspace.getString("workspace_id"))
        assertEquals("workspace-1", action.getString("workspace_id"))
        assertEquals("rename", action.getString("action"))
        assertEquals("workspace-1", close.getString("workspace_id"))
        assertEquals("group-1", group.getString("group_id"))
        assertEquals("workspace-1", terminal.getString("workspace_id"))
        assertEquals("terminal-1", terminal.getString("terminal_id"))
        assertEquals("terminal-1", terminal.getString("surface_id"))
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
    fun terminalPasteTrimsSubmitKeyAndDefaultsBlankAfterTrim() {
        val explicit = MobileRpcParams.terminalPaste("workspace-1", "terminal-1", "hello", " enter ", 80, 24)
        val blank = MobileRpcParams.terminalPaste("workspace-1", "terminal-1", "hello", "  ", 80, 24)

        assertEquals("enter", explicit.getString("submit_key"))
        assertEquals("return", blank.getString("submit_key"))
    }

    @Test
    fun terminalPasteImageDefaultsBlankFormat() {
        val params = MobileRpcParams.terminalPasteImage("workspace-1", "terminal-1", "base64", "", 80, 24)

        assertEquals("base64", params.getString("image_base64"))
        assertEquals("png", params.getString("image_format"))
        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }

    @Test
    fun terminalPasteImageTrimsFormatAndDefaultsBlankAfterTrim() {
        val explicit = MobileRpcParams.terminalPasteImage("workspace-1", "terminal-1", "base64", " jpeg ", 80, 24)
        val blank = MobileRpcParams.terminalPasteImage("workspace-1", "terminal-1", "base64", "  ", 80, 24)

        assertEquals("jpg", explicit.getString("image_format"))
        assertEquals("png", blank.getString("image_format"))
    }

    @Test
    fun terminalPasteImageDefaultsUnsupportedFormat() {
        val params = MobileRpcParams.terminalPasteImage("workspace-1", "terminal-1", "base64", "../../evil", 80, 24)

        assertEquals("png", params.getString("image_format"))
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
    fun terminalScrollCoercesNonFiniteDeltaToZero() {
        val params = MobileRpcParams.terminalScroll(
            workspaceId = "workspace-1",
            terminalId = "terminal-1",
            deltaLines = Double.NaN,
            column = 3,
            row = 4,
            maxScrollbackRows = 0,
            columns = 80,
            rows = 24
        )

        assertEquals(0.0, params.getDouble("delta_lines"), 0.0)
        assertEquals(3, params.getInt("col"))
        assertEquals(4, params.getInt("row"))
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

    @Test
    fun eventSubscriptionKeepsOnlyUniqueNonBlankTopics() {
        val params = MobileRpcParams.eventSubscription(
            streamId = "stream-android-1",
            topics = listOf(" workspace.updated ", "", "terminal.bytes", "workspace.updated", "  ")
        )

        assertEquals(
            listOf("workspace.updated", "terminal.bytes"),
            (0 until params.getJSONArray("topics").length()).map { index ->
                params.getJSONArray("topics").getString(index)
            }
        )
    }

    @Test
    fun eventSubscriptionTrimsStreamId() {
        val params = MobileRpcParams.eventSubscription(
            streamId = " stream-android-1 ",
            topics = listOf("workspace.updated")
        )

        assertEquals("stream-android-1", params.getString("stream_id"))
    }

    @Test
    fun notificationParamsKeepOnlySupportedNonBlankIds() {
        val ids = org.json.JSONArray()
            .put(" n-1 ")
            .put("")
            .put(42)
            .put("n-2")
            .put(org.json.JSONObject().put("id", "n-3"))

        val reconcile = MobileRpcParams.reconcileNotifications(ids)
        val dismiss = MobileRpcParams.dismissNotifications(ids)

        assertEquals(
            listOf("n-1", "42", "n-2"),
            (0 until reconcile.getJSONArray("delivered_ids").length()).map { index ->
                reconcile.getJSONArray("delivered_ids").getString(index)
            }
        )
        assertEquals(
            listOf("n-1", "42", "n-2"),
            (0 until dismiss.getJSONArray("notification_ids").length()).map { index ->
                dismiss.getJSONArray("notification_ids").getString(index)
            }
        )
    }

    @Test
    fun notificationParamsNormalizeNumericIds() {
        val ids = org.json.JSONArray()
            .put(" n-1 ")
            .put(42)
            .put(42.5)
            .put(true)
            .put(org.json.JSONObject().put("id", "n-2"))

        val reconcile = MobileRpcParams.reconcileNotifications(ids)
        val dismiss = MobileRpcParams.dismissNotifications(ids)

        assertEquals(
            listOf("n-1", "42", "42.5"),
            (0 until reconcile.getJSONArray("delivered_ids").length()).map { index ->
                reconcile.getJSONArray("delivered_ids").getString(index)
            }
        )
        assertEquals(
            listOf("n-1", "42", "42.5"),
            (0 until dismiss.getJSONArray("notification_ids").length()).map { index ->
                dismiss.getJSONArray("notification_ids").getString(index)
            }
        )
    }

    @Test
    fun dogfoodFeedbackCarriesMacSinkFieldsAndAndroidClientId() {
        val params = MobileRpcParams.dogfoodFeedback(
            text = "  Android feedback  ",
            terminalText = "visible terminal",
            buildStamp = "  android-webview test  "
        )

        assertEquals("Android feedback", params.getString("text"))
        assertEquals("visible terminal", params.getString("terminal_text"))
        assertEquals("android-webview test", params.getString("build_stamp"))
        assertEquals("", params.getString("diagnostic_blob_base64"))
        assertEquals(MobileRpcParams.CLIENT_ID, params.getString("client_id"))
    }

    @Test
    fun dogfoodFeedbackCapsLargeFieldsBeforeRpc() {
        val params = MobileRpcParams.dogfoodFeedback(
            text = "x".repeat(16_385),
            terminalText = "y".repeat(262_145),
            buildStamp = "z".repeat(513)
        )

        assertEquals(16_384, params.getString("text").length)
        assertEquals(262_144, params.getString("terminal_text").length)
        assertEquals(512, params.getString("build_stamp").length)
    }
}
