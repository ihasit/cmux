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
            .put("workspace_id", cleanId(workspaceId))
            .put("client_id", CLIENT_ID)
    }

    fun workspaceAction(workspaceId: String, action: String): JSONObject {
        return JSONObject()
            .put("workspace_id", cleanId(workspaceId))
            .put("client_id", CLIENT_ID)
            .put("action", cleanId(action))
    }

    fun closeWorkspace(workspaceId: String): JSONObject {
        return JSONObject()
            .put("workspace_id", cleanId(workspaceId))
            .put("client_id", CLIENT_ID)
    }

    fun reconcileNotifications(deliveredIds: JSONArray): JSONObject {
        return JSONObject()
            .put("client_id", CLIENT_ID)
            .put("delivered_ids", sanitizedStringArray(deliveredIds))
    }

    fun dismissNotifications(notificationIds: JSONArray): JSONObject {
        return JSONObject()
            .put("client_id", CLIENT_ID)
            .put("notification_ids", sanitizedStringArray(notificationIds))
    }

    fun dogfoodFeedback(text: String, terminalText: String, buildStamp: String): JSONObject {
        return JSONObject()
            .put("client_id", CLIENT_ID)
            .put("text", text.trim().take(16_384))
            .put("terminal_text", terminalText.take(262_144))
            .put("build_stamp", buildStamp.trim().take(512))
            .put("diagnostic_blob_base64", "")
    }

    fun workspaceGroup(groupId: String): JSONObject {
        return JSONObject()
            .put("group_id", cleanId(groupId))
            .put("client_id", CLIENT_ID)
    }

    fun eventSubscription(streamId: String, topics: List<String>): JSONObject {
        val topicsJson = JSONArray()
        topics.map { it.trim() }.distinct().forEach { topic ->
            if (topic.isNotEmpty()) {
                topicsJson.put(topic)
            }
        }
        return JSONObject()
            .put("stream_id", cleanId(streamId))
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
            .put("submit_key", submitKey.trim().ifBlank { "return" })
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
            .put("image_format", normalizedImageFormat(imageFormat))
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
        val safeDeltaLines = if (deltaLines.isFinite()) deltaLines else 0.0
        val params = terminalViewport(workspaceId, terminalId, columns, rows)
            .put("delta_lines", safeDeltaLines)
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
            .put("workspace_id", cleanId(workspaceId))
            .put("terminal_id", cleanId(terminalId))
            .put("surface_id", cleanId(terminalId))
            .put("client_id", CLIENT_ID)
    }

    private fun cleanId(value: String): String {
        return value.trim()
    }

    private fun sanitizedStringArray(values: JSONArray): JSONArray {
        val sanitized = JSONArray()
        for (index in 0 until values.length()) {
            val value = normalizedId(values.opt(index))
            if (value.isNotEmpty()) {
                sanitized.put(value)
            }
        }
        return sanitized
    }

    private fun normalizedId(value: Any?): String {
        return when (value) {
            is String -> value.trim()
            is Int, is Long, is Double, is Float -> value.toString().trim()
            else -> ""
        }
    }

    private fun normalizedImageFormat(value: String): String {
        val normalized = value.trim().lowercase()
        return when (normalized) {
            "jpg", "jpeg" -> "jpg"
            "png", "gif", "webp" -> normalized
            else -> "png"
        }
    }
}
