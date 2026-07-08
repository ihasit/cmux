package com.cmux.android

import org.json.JSONObject

object MobileRpcParams {
    const val CLIENT_ID = "cmux-android-webview"

    fun createWorkspace(): JSONObject {
        return JSONObject()
    }

    fun createTerminal(workspaceId: String): JSONObject {
        return JSONObject()
            .put("workspace_id", workspaceId)
    }
}
