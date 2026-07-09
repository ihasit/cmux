package com.cmux.android

import org.json.JSONObject

object MobileEventTopics {
    fun topicsForCapabilities(capabilities: Set<String>): List<String> {
        return topicsForHostStatus(HostStatusCapabilities(capabilities = capabilities))
    }

    fun topicsForHostStatusJson(status: JSONObject): List<String> {
        return topicsForHostStatus(status.hostStatusCapabilities())
    }

    fun topicsForHostStatus(status: HostStatusCapabilities): List<String> {
        val terminalTopic = if (status.supportsRenderGrid) {
            "terminal.render_grid"
        } else {
            "terminal.bytes"
        }
        return listOf(
            "workspace.updated",
            terminalTopic,
            "terminal.set_font",
            "notification.badge",
            "notification.dismissed"
        )
    }

    data class HostStatusCapabilities(
        val capabilities: Set<String> = emptySet(),
        val terminalFidelity: String? = null
    ) {
        val supportsRenderGrid: Boolean
            get() = "terminal.render_grid.v1" in capabilities || terminalFidelity == "render_grid"
    }
}

internal fun JSONObject.hostStatusCapabilities(): MobileEventTopics.HostStatusCapabilities {
    val topLevel = optFirstStringArray("capabilities")
    val capabilities = if (topLevel.isNotEmpty()) {
        topLevel.toSet()
    } else {
        optJSONObject("host_service")
            ?.optFirstStringArray("capabilities")
            ?.toSet()
            ?: emptySet()
    }
    return MobileEventTopics.HostStatusCapabilities(
        capabilities = capabilities,
        terminalFidelity = optString("terminal_fidelity").takeIf { it.isNotBlank() }
    )
}

private fun JSONObject.optStringArray(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optString(index).trim().takeIf { it.isNotEmpty() }
    }
}

private fun JSONObject.optFirstStringArray(vararg names: String): List<String> {
    for (name in names) {
        val values = optStringArray(name)
        if (values.isNotEmpty()) return values
    }
    return emptyList()
}
