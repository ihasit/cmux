package com.cmux.android

import org.json.JSONArray
import org.json.JSONObject

object MobileRpcParams {
    const val CLIENT_ID = "cmux-android-webview"

    fun createWorkspace(): JSONObject {
        return JSONObject()
            .put("client_id", CLIENT_ID)
    }

    fun createTerminal(workspaceId: String): JSONObject {
        return JSONObject()
            .put("workspace_id", workspaceId)
            .put("client_id", CLIENT_ID)
    }

    fun workspaceAction(workspaceId: String, action: String): JSONObject {
        return JSONObject()
            .put("workspace_id", workspaceId)
            .put("client_id", CLIENT_ID)
            .put("action", action)
    }

    fun closeWorkspace(workspaceId: String): JSONObject {
        return JSONObject()
            .put("workspace_id", workspaceId)
            .put("client_id", CLIENT_ID)
    }

    fun reconcileNotifications(deliveredIds: JSONArray): JSONObject {
        return JSONObject()
            .put("client_id", CLIENT_ID)
            .put("delivered_ids", deliveredIds)
    }

    fun dismissNotifications(notificationIds: JSONArray): JSONObject {
        return JSONObject()
            .put("client_id", CLIENT_ID)
            .put("notification_ids", notificationIds)
    }

    fun workspaceGroup(groupId: String): JSONObject {
        return JSONObject()
            .put("group_id", groupId)
            .put("client_id", CLIENT_ID)
    }

    fun eventSubscription(streamId: String, topics: List<String>): JSONObject {
        val topicsJson = JSONArray()
        topics.forEach { topic ->
            topicsJson.put(topic)
        }
        return JSONObject()
            .put("stream_id", streamId)
            .put("topics", topicsJson)
            .put("client_id", CLIENT_ID)
    }

    fun terminalViewport(workspaceId: String, terminalId: String, columns: Int, rows: Int): JSONObject {
        return terminalBase(workspaceId, terminalId)
            .put("viewport_columns", columns.coerceIn(20, 300))
            .put("viewport_rows", rows.coerceIn(5, 120))
    }

    fun clearTerminalViewport(workspaceId: String, terminalId: String): JSONObject {
        return terminalBase(workspaceId, terminalId)
            .put("clear", true)
    }

    fun terminalInput(workspaceId: String, terminalId: String, text: String, columns: Int, rows: Int): JSONObject {
        return terminalViewport(workspaceId, terminalId, columns, rows)
            .put("text", text)
    }

    fun terminalPaste(
        workspaceId: String,
        terminalId: String,
        text: String,
        submitKey: String,
        columns: Int,
        rows: Int
    ): JSONObject {
        return terminalViewport(workspaceId, terminalId, columns, rows)
            .put("text", text)
            .put("submit_key", submitKey.ifBlank { "return" })
    }

    fun terminalPasteImage(
        workspaceId: String,
        terminalId: String,
        imageBase64: String,
        imageFormat: String,
        columns: Int,
        rows: Int
    ): JSONObject {
        return terminalViewport(workspaceId, terminalId, columns, rows)
            .put("image_base64", imageBase64)
            .put("image_format", imageFormat.ifBlank { "png" })
    }

    fun terminalScroll(
        workspaceId: String,
        terminalId: String,
        deltaLines: Double,
        column: Int,
        row: Int,
        maxScrollbackRows: Int,
        columns: Int,
        rows: Int
    ): JSONObject {
        val params = terminalViewport(workspaceId, terminalId, columns, rows)
            .put("delta_lines", deltaLines)
            .put("col", column.coerceAtLeast(0))
            .put("row", row.coerceAtLeast(0))
        if (maxScrollbackRows > 0) {
            params.put("max_scrollback_rows", maxScrollbackRows.coerceIn(1, 20000))
        }
        return params
    }

    fun terminalMouse(workspaceId: String, terminalId: String, column: Int, row: Int): JSONObject {
        return terminalBase(workspaceId, terminalId)
            .put("col", column.coerceAtLeast(0))
            .put("row", row.coerceAtLeast(0))
    }

    private fun terminalBase(workspaceId: String, terminalId: String): JSONObject {
        return JSONObject()
            .put("workspace_id", workspaceId)
            .put("terminal_id", terminalId)
            .put("surface_id", terminalId)
            .put("client_id", CLIENT_ID)
    }
}
