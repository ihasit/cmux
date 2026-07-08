package com.cmux.android

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

class PairingException(val messageKey: String) : IllegalArgumentException(messageKey)

private data class PairingUri(
    val scheme: String?,
    val host: String?,
    private val query: Map<String, List<String>>
) {
    fun getQueryParameter(name: String): String? = query[name]?.firstOrNull()

    fun getQueryParameters(name: String): List<String> = query[name] ?: emptyList()

    companion object {
        fun parse(rawValue: String): PairingUri {
            val uri = URI(rawValue)
            val query = linkedMapOf<String, MutableList<String>>()
            uri.rawQuery
                ?.split("&")
                ?.filter { it.isNotEmpty() }
                ?.forEach { item ->
                    val separator = item.indexOf("=")
                    val rawName = if (separator >= 0) item.substring(0, separator) else item
                    val rawValuePart = if (separator >= 0) item.substring(separator + 1) else ""
                    val name = urlDecode(rawName)
                    val value = urlDecode(rawValuePart)
                    query.getOrPut(name) { mutableListOf() }.add(value)
                }
            return PairingUri(uri.scheme, uri.host, query)
        }

        private fun urlDecode(value: String): String {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }
    }
}

class PairingParser {
    fun parse(rawValue: String): PairedMac {
        val trimmed = rawValue.trim()
        checkPairing(trimmed.isNotEmpty(), "pair.error.empty")
        val lowered = trimmed.lowercase()
        if (lowered.startsWith("ws://") || lowered.startsWith("wss://")) {
            return parseManualWebSocketUrl(trimmed)
        }
        if (!lowered.startsWith("cmux-ios://") && !lowered.startsWith("cmux-ios-dev://") && trimmed.contains(":")) {
            return parseManualHostPort(trimmed)
        }
        val uri = runCatching { PairingUri.parse(trimmed) }
            .getOrElse { throw PairingException("pair.error.scheme") }
        val scheme = uri.scheme?.lowercase()
        checkPairing(scheme == "cmux-ios" || scheme == "cmux-ios-dev", "pair.error.scheme")
        checkPairing(uri.host == "attach" || uri.host == "pair", "pair.error.host")
        return when {
            uri.host == "attach" && uri.getQueryParameter("v") == "2" -> parseAttachV2(uri)
            uri.host == "attach" && uri.getQueryParameter("payload") != null -> parseAttachPayload(uri)
            uri.host == "pair" && uri.getQueryParameter("payload") != null -> parseLegacyPairPayload(uri)
            else -> throw PairingException("pair.error.unsupported")
        }
    }

    private fun parseManualHostPort(rawValue: String): PairedMac {
        val (host, port) = parseHostPort(rawValue)
        checkPairing(!isLoopbackHost(host), "pair.error.loopback")
        val route = CmuxRoute(
            id = "manual",
            kind = "tailscale",
            host = host,
            port = port,
            priority = 10
        )
        return PairedMac(
            id = stableMacId(null, listOf(route)),
            displayName = null,
            userId = null,
            userEmail = null,
            pairingCompatibilityVersion = null,
            appVersion = null,
            appBuild = null,
            routes = listOf(route)
        )
    }

    private fun parseManualWebSocketUrl(rawValue: String): PairedMac {
        checkWebSocketUrl(rawValue)
        val route = CmuxRoute(
            id = "manual_websocket",
            kind = "websocket",
            host = "",
            port = 0,
            priority = 5,
            url = rawValue
        )
        return PairedMac(
            id = stableMacId(null, listOf(route)),
            displayName = null,
            userId = null,
            userEmail = null,
            pairingCompatibilityVersion = null,
            appVersion = null,
            appBuild = null,
            routes = listOf(route)
        )
    }

    private fun parseAttachV2(uri: PairingUri): PairedMac {
        val rawRoutes = uri.getQueryParameters("r")
        checkPairing(rawRoutes.isNotEmpty(), "pair.error.noRoutes")
        checkPairing(rawRoutes.size <= 8, "pair.error.tooManyRoutes")
        val routes = rawRoutes.mapIndexed { index, rawRoute ->
            val (host, port) = parseHostPort(rawRoute)
            checkPairing(!isLoopbackHost(host), "pair.error.loopback")
            CmuxRoute(
                id = if (index == 0) "tailscale" else "tailscale_${index + 1}",
                kind = "tailscale",
                host = host,
                port = port,
                priority = 10 + index * 10
            )
        }
        return PairedMac(
            id = stableMacId(null, routes),
            displayName = null,
            userId = uri.getQueryParameter("ub"),
            userEmail = uri.getQueryParameter("e"),
            pairingCompatibilityVersion = uri.getQueryParameter("pc")?.toIntOrNull(),
            appVersion = uri.getQueryParameter("av"),
            appBuild = uri.getQueryParameter("ab"),
            routes = routes
        )
    }

    private fun parseAttachPayload(uri: PairingUri): PairedMac {
        val payload = uri.getQueryParameter("payload") ?: throw PairingException("pair.error.missingPayload")
        val json = JSONObject(String(base64UrlDecode(payload), StandardCharsets.UTF_8))
        return if (json.has("v")) {
            parseCompactTicket(json)
        } else {
            parseFullTicket(json)
        }
    }

    private fun parseLegacyPairPayload(uri: PairingUri): PairedMac {
        val payload = uri.getQueryParameter("payload") ?: throw PairingException("pair.error.missingPayload")
        val json = JSONObject(String(base64UrlDecode(payload), StandardCharsets.UTF_8))
        val host = json.optString("host").trim()
        val port = json.optInt("port", -1)
        checkPairing(host.isNotEmpty() && port in 1..65535, "pair.error.invalidRoute")
        checkPairing(!isLoopbackHost(host), "pair.error.loopback")
        val route = CmuxRoute("tailscale", json.optString("transport", "tailscale"), host, port, 10)
        return PairedMac(
            id = stableMacId(json.optNullableString("mac_device_id"), listOf(route)),
            displayName = json.optNullableString("mac_display_name"),
            userId = null,
            userEmail = null,
            pairingCompatibilityVersion = null,
            appVersion = null,
            appBuild = null,
            routes = listOf(route)
        )
    }

    private fun parseCompactTicket(json: JSONObject): PairedMac {
        val routesJson = json.optJSONArray("r") ?: JSONArray()
        val kindCounts = mutableMapOf<String, Int>()
        val routes = (0 until routesJson.length()).mapNotNull { index ->
            val routeJson = routesJson.optJSONObject(index) ?: return@mapNotNull null
            val kind = routeJson.optString("k", "tailscale")
            val occurrence = (kindCounts[kind] ?: 0) + 1
            kindCounts[kind] = occurrence
            val endpoint = routeJson.optJSONObject("e") ?: return@mapNotNull null
            parseRoute(
                id = routeJson.optNullableString("i") ?: synthesizedRouteId(kind, occurrence),
                kind = kind,
                endpoint = endpoint,
                priority = routeJson.optInt("p", index * 10)
            )
        }
        checkPairing(routes.isNotEmpty(), "pair.error.noRoutes")
        val deviceId = json.optNullableString("d")
        return PairedMac(
            id = stableMacId(deviceId, routes),
            displayName = null,
            userId = json.optNullableString("u")?.takeUnless { it.contains("@") },
            userEmail = json.optNullableString("u")?.takeIf { it.contains("@") },
            pairingCompatibilityVersion = json.optNullableInt("pc"),
            appVersion = json.optNullableString("av"),
            appBuild = json.optNullableString("ab"),
            routes = routes
        )
    }

    private fun parseFullTicket(json: JSONObject): PairedMac {
        val routesJson = json.optJSONArray("routes") ?: JSONArray()
        val routes = (0 until routesJson.length()).mapNotNull { index ->
            val routeJson = routesJson.optJSONObject(index) ?: return@mapNotNull null
            val endpoint = routeJson.optJSONObject("endpoint") ?: return@mapNotNull null
            parseRoute(
                id = routeJson.optNullableString("id") ?: "route_$index",
                kind = routeJson.optString("kind", "tailscale"),
                endpoint = endpoint,
                priority = routeJson.optInt("priority", index * 10)
            )
        }
        checkPairing(routes.isNotEmpty(), "pair.error.noRoutes")
        val deviceId = json.optNullableString("macDeviceID")
        return PairedMac(
            id = stableMacId(deviceId, routes),
            displayName = json.optNullableString("macDisplayName"),
            userId = json.optNullableString("macUserID"),
            userEmail = json.optNullableString("macUserEmail"),
            pairingCompatibilityVersion = json.optNullableInt("macPairingCompatibilityVersion"),
            appVersion = json.optNullableString("macAppVersion"),
            appBuild = json.optNullableString("macAppBuild"),
            routes = routes
        )
    }

    private fun parseHostPort(rawValue: String): Pair<String, Int> {
        val decoded = rawValue.trim()
        val host: String
        val portText: String
        if (decoded.startsWith("[")) {
            val closeIndex = decoded.indexOf("]")
            checkPairing(closeIndex > 1 && decoded.getOrNull(closeIndex + 1) == ':', "pair.error.invalidRoute")
            host = decoded.substring(1, closeIndex)
            portText = decoded.substring(closeIndex + 2)
        } else {
            val separator = decoded.lastIndexOf(":")
            checkPairing(separator > 0, "pair.error.invalidRoute")
            host = decoded.substring(0, separator)
            portText = decoded.substring(separator + 1)
        }
        val port = portText.toIntOrNull()
        checkPairing(host.isNotBlank() && port != null && port in 1..65535, "pair.error.invalidRoute")
        return host to requireNotNull(port)
    }

    private fun parseRoute(id: String, kind: String, endpoint: JSONObject, priority: Int): CmuxRoute? {
        val url = endpoint.optNullableString("u") ?: endpoint.optNullableString("url")
        if (kind == "websocket" && url != null) {
            checkWebSocketUrl(url)
            return CmuxRoute(id, kind, host = "", port = 0, priority = priority, url = url)
        }
        val host = endpoint.optNullableString("h") ?: endpoint.optNullableString("host") ?: return null
        val port = endpoint.optNullableInt("p") ?: endpoint.optNullableInt("port") ?: return null
        if (port !in 1..65535) return null
        checkPairing(kind == "debug_loopback" || !isLoopbackHost(host), "pair.error.loopback")
        return CmuxRoute(id, kind, host, port, priority)
    }

    private fun checkWebSocketUrl(rawValue: String) {
        val uri = runCatching { URI(rawValue) }
            .getOrElse { throw PairingException("pair.error.invalidRoute") }
        val scheme = uri.scheme?.lowercase()
        checkPairing(scheme == "ws" || scheme == "wss", "pair.error.invalidRoute")
        checkPairing(!uri.host.isNullOrBlank(), "pair.error.invalidRoute")
        checkPairing(!isLoopbackHost(uri.host), "pair.error.loopback")
    }

    private fun isLoopbackHost(host: String): Boolean {
        val lowered = host.trim().lowercase()
        return lowered == "localhost" ||
            lowered == "::1" ||
            lowered == "0:0:0:0:0:0:0:1" ||
            lowered.startsWith("127.") ||
            lowered == "0.0.0.0" ||
            lowered == "::"
    }

    private fun synthesizedRouteId(kind: String, occurrence: Int): String {
        return if (occurrence == 1) kind else "${kind}_$occurrence"
    }

    private fun stableMacId(deviceId: String?, routes: List<CmuxRoute>): String {
        if (!deviceId.isNullOrBlank()) return deviceId
        val routeKey = routes.joinToString("|") { route ->
            route.url?.let { "${route.kind}:$it" } ?: "${route.kind}:${route.host}:${route.port}"
        }
        return UUID.nameUUIDFromBytes(routeKey.toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private fun base64UrlDecode(value: String): ByteArray {
        return Base64.getUrlDecoder().decode(value)
    }

    private fun checkPairing(condition: Boolean, messageKey: String) {
        if (!condition) throw PairingException(messageKey)
    }
}
