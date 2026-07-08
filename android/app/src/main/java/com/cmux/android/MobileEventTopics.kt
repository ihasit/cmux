package com.cmux.android

object MobileEventTopics {
    fun topicsForCapabilities(capabilities: Set<String>): List<String> {
        return listOf(
            "workspace.updated",
            "terminal.render_grid",
            "terminal.bytes",
            "terminal.set_font",
            "notification.badge",
            "notification.dismissed"
        )
    }
}
