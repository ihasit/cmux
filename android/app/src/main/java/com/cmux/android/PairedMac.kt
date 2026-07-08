package com.cmux.android

import org.json.JSONArray
import org.json.JSONObject

data class CmuxRoute(
    val id: String,
    val kind: String,
    val host: String,
    val port: Int,
    val priority: Int = 0,
    val url: String? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("kind", kind)
        .put("host", host)
        .put("port", port)
        .put("priority", priority)
        .put("url", url)
}

data class PairedMac(
    val id: String,
    val displayName: String?,
    val userId: String?,
    val userEmail: String?,
    val pairingCompatibilityVersion: Int?,
    val appVersion: String?,
    val appBuild: String?,
    val routes: List<CmuxRoute>
) {
    val primaryRoute: CmuxRoute?
        get() = supportedRoutes().firstOrNull()

    fun supportedRoutes(): List<CmuxRoute> {
        return routes
            .filter { it.isSupportedMobileRoute() }
            .sortedWith(compareBy<CmuxRoute> { it.priority }.thenBy { it.id })
    }

    fun toJson(): JSONObject {
        val routeArray = JSONArray()
        routes.forEach { route -> routeArray.put(route.toJson()) }
        return JSONObject()
            .put("id", id)
            .put("display_name", displayName)
            .put("user_id", userId)
            .put("user_email", userEmail)
            .put("pairing_compatibility_version", pairingCompatibilityVersion)
            .put("app_version", appVersion)
            .put("app_build", appBuild)
            .put("routes", routeArray)
    }

    companion object {
        fun fromJson(json: JSONObject): PairedMac {
            val routesJson = json.optJSONArray("routes") ?: JSONArray()
            val routes = (0 until routesJson.length()).mapNotNull { index ->
                val route = routesJson.optJSONObject(index) ?: return@mapNotNull null
                val host = route.optString("host").trim()
                val port = route.optInt("port", -1)
                val url = route.optNullableString("url")
                if (url == null && (host.isEmpty() || port !in 1..65535)) {
                    return@mapNotNull null
                }
                CmuxRoute(
                    id = route.optString("id", "route_$index"),
                    kind = route.optString("kind", "tailscale"),
                    host = host,
                    port = port,
                    priority = route.optInt("priority", index * 10),
                    url = url
                )
            }
            return PairedMac(
                id = json.optString("id").ifBlank { routes.firstOrNull()?.let { "${it.host}:${it.port}" } ?: "unknown" },
                displayName = json.optNullableString("display_name"),
                userId = json.optNullableString("user_id"),
                userEmail = json.optNullableString("user_email"),
                pairingCompatibilityVersion = json.optNullableInt("pairing_compatibility_version"),
                appVersion = json.optNullableString("app_version"),
                appBuild = json.optNullableString("app_build"),
                routes = routes
            )
        }
    }
}

fun CmuxRoute.isSupportedMobileRoute(): Boolean {
    return kind == "tailscale" || kind == "debug_loopback" || kind == "websocket"
}

fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).trim().ifEmpty { null }
}

fun JSONObject.optNullableInt(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return optInt(name)
}
