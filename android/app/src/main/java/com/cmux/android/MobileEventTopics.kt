package com.cmux.android

object MobileEventTopics {
    fun topicsForCapabilities(capabilities: Set<String>): List<String> {
        return topicsForHostStatus(HostStatusCapabilities(capabilities = capabilities))
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
