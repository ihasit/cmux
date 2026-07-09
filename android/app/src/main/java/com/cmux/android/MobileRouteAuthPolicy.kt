package com.cmux.android

import java.net.URI

object MobileRouteAuthPolicy {
    fun routeAllowsStackAuth(route: CmuxRoute): Boolean {
        return when (route.kind) {
            "debug_loopback" -> isLoopbackHost(route.host)
            "tailscale" -> isTailscaleHost(route.host)
            "websocket" -> route.url?.let { isSecureWebSocketUrl(it) } == true
            else -> false
        }
    }

    private fun isSecureWebSocketUrl(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        val host = uri.host?.trim().orEmpty()
        return uri.scheme?.lowercase() == "wss" &&
            host.isNotEmpty() &&
            uri.userInfo.isNullOrEmpty() &&
            !isLoopbackHost(host)
    }

    private fun isLoopbackHost(host: String): Boolean {
        val normalized = host.trim().lowercase().trim('[', ']')
        return normalized == "localhost" ||
            normalized == "::1" ||
            normalized == "0:0:0:0:0:0:0:1" ||
            isIPv4LoopbackHost(normalized)
    }

    private fun isIPv4LoopbackHost(host: String): Boolean {
        val octets = ipv4Octets(host) ?: return false
        return octets[0] == 127
    }

    private fun isTailscaleHost(host: String): Boolean {
        val normalized = host.trim().lowercase().trim('[', ']')
        return normalized.endsWith(".ts.net") || isTailscaleIPv4Host(normalized)
    }

    private fun isTailscaleIPv4Host(host: String): Boolean {
        val octets = ipv4Octets(host) ?: return false
        return octets[0] == 100 && octets[1] in 64..127
    }

    private fun ipv4Octets(host: String): List<Int>? {
        val parts = host.split(".")
        if (parts.size != 4) return null
        return parts.map { part ->
            if (part.isEmpty() || !part.all { it.isDigit() }) return null
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            value
        }
    }
}
